package de.mhus.con.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import de.mhus.con.api.AMojo;
import de.mhus.con.api.ConUtil;
import de.mhus.con.api.Conductor;
import de.mhus.con.api.ConductorPlugin;
import de.mhus.con.api.ErrorInfo;
import de.mhus.con.api.ExecutePlugin;
import de.mhus.con.api.ExecutionInterceptorPlugin;
import de.mhus.con.api.Executor;
import de.mhus.con.api.Labels;
import de.mhus.con.api.Lifecycle;
import de.mhus.con.api.Plugin;
import de.mhus.con.api.Plugin.SCOPE;
import de.mhus.con.api.Project;
import de.mhus.con.api.Scheme;
import de.mhus.con.api.Step;
import de.mhus.con.api.Steps;
import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MLog;
import de.mhus.lib.core.MString;
import de.mhus.lib.core.lang.Value;
import de.mhus.lib.core.util.MUri;
import de.mhus.lib.errors.MRuntimeException;
import de.mhus.lib.errors.NotFoundException;

public class ExecutorDefault extends MLog implements Executor {

    private Conductor con;
    private LinkedList<ErrorInfo> errors = new LinkedList<>();
    private Map<String,ConductorPlugin> mojos = new HashMap<>();
    private Map<String, Object[]> pluginClassLoaders = new HashMap<>();
    private LinkedList<ExecutionInterceptorPlugin> interceptors = new LinkedList<>();
    
    public ExecutorDefault() {
        interceptors.add(new ExecutionInterceptorDefault()); // TODO dynamic
    }
    
    @Override
    public void execute(Conductor con, String lifecycle) {
        this.con = con;
        ((ConductorImpl)con).properties.put(ConUtil.PROPERTY_LIFECYCLE, lifecycle);
        
        Lifecycle lf = con.getLifecycles().get(lifecycle);
        try {
        	log().d("executeLifecycle",lf);
	        Steps steps = lf.getSteps();
	        execute(lifecycle, steps);
        } catch (Throwable t) {
        	log().d(lf,t);
        	throw new MRuntimeException(lf,t);
        }
    }

    protected void execute(String lifecycle, Steps steps) {
        interceptors.forEach(i -> i.executeBegin(con, lifecycle, steps));
        try {
            for (Step step : steps)
                execute(step);
        } finally {
            interceptors.forEach(i -> i.executeEnd(con, lifecycle, steps, errors));
        }
    }

    protected void execute(Step step) {
        
    	log().d("executeStep",step);
    	// reset all projects
    	con.getProjects().forEach(p -> ((ProjectImpl)p).setStatus(Project.STATUS.NONE) );
    	
    	try {
	        // load plugin
	        String target = step.getTarget();
	        Plugin plugin = con.getPlugins().get(target);
	
	    	// check for scope
	    	
	        if (plugin.getScope() == SCOPE.STEP) {
	        	// scope: step
	        	execute(step, (Project)null, plugin);
	        	return;
	        }
	    	
	        // scope: project
	    	
	        // select projects
	        Labels selector = step.getSelector();
	        LinkedList<Project> projects = null;
	        if (selector != null) {
	            projects = new LinkedList<>(con.getProjects().select(selector));
	        } else {
	            projects = new LinkedList<>(con.getProjects().getAll());
	        }
	        projects.forEach(p -> ((ProjectImpl)p).setStatus(Project.STATUS.SKIPPED) );
	        
	        // order
	        String order = step.getSortBy();
	        if (MString.isSet(order)) {
	            ConUtil.orderProjects(projects, order, step.isOrderAsc());
	        }
	
	        execute(step, projects, plugin);
    	} catch (Throwable t) {
    		throw new MRuntimeException(step,t);
    	}
    }

    protected void execute(Step step, LinkedList<Project> projects, Plugin plugin) {
    	if (projects == null || projects.size() == 0)
    		log().w("no projects selected",step);
        for (Project project : projects)
            execute(step, project, plugin);
    }

    protected void execute(Step step, Project project, Plugin plugin) {
    	log().d("executeProject",step,project,plugin);
        try {
	        ContextImpl context = new ContextImpl(con, project == null ? null : project.getProperties(), step.getProperties() );
	                
	        context.init(project, plugin, step);
	        
	        if (!step.matchCondition(context)) {
	        	log().d("condition not successful",step);
	        	return;
	        }
	        interceptors.forEach(i -> i.executeBegin(context));
	        
	        ConductorPlugin impl = loadMojo(context);
	        try {
	        	((ExecutePlugin)impl).execute(context);
	        } catch (Throwable t) {
	            if (project != null) ((ProjectImpl)project).setStatus(Project.STATUS.FAILURE);
	            interceptors.forEach(i -> i.executeError(context, t));
	        	errors.add(new ErrorsInfoImpl(context, t));
	        	if (con.getProperties().getBoolean(ConUtil.PROPERTY_FAE, false)) {
	        		log().e(context,t);
	        	} else
	        		throw t;
	        }
	        if (project != null) ((ProjectImpl)project).setStatus(Project.STATUS.SUCCESS);
            interceptors.forEach(i -> i.executeEnd(context));
        } catch (Throwable t) {
        	throw new MRuntimeException(project,t);
        }
    }

    public ConductorPlugin loadMojo(ContextImpl context) {
		ConductorPlugin impl = mojos.get(context.getPlugin().getTarget());
		if (impl != null) return impl;
        try {
        	impl = createMojo(con, context.getPlugin());
        } catch (Throwable t) {
        	throw new MRuntimeException(context.getPlugin(),t);
        }
        mojos.put(context.getPlugin().getTarget(), impl);
        return impl;
	}

	public ConductorPlugin createMojo(Conductor con, Plugin plugin) throws IOException, NotFoundException {
		log().d("createMojo",plugin.getUri(),plugin.getMojo());
		String mojoName = plugin.getMojo();
		
		Object[] entry = pluginClassLoaders.get(plugin.getUri());
		
		if (entry == null) {
			MUri uri = MUri.toUri(plugin.getUri());
			Scheme scheme = con.getSchemes().get(uri);
			File pFile = scheme.load(con, uri);
			
			Value<JarEntry> metaInfEntry = new Value<>();
			ArrayList<String> classes=new ArrayList<>();
			LinkedList<URL> urls = new LinkedList<>();
			urls.add(pFile.toURI().toURL());
			log().d("Add main JAR",pFile);
			try (JarFile jar = new JarFile(pFile)) {
		        jar.stream().forEach(jarEntry -> {
		            if(jarEntry.getName().endsWith(".class"))
		            {
		                classes.add(jarEntry.getName().replaceAll("/",".").replace(".class",""));
		            } else if (jarEntry.getName().equals("META-INF/dependencies.txt")) {
		            	metaInfEntry.value = jarEntry;
		            }
		        });
		        
		        // load dependencies information out of META-INF/dependencies.txt
		        if (metaInfEntry.value != null) {
		        	InputStream is = jar.getInputStream(metaInfEntry.value);
		        	for (String line : MFile.readLines(is, true)) {
		        		line = line.trim();
		        		if (MString.isSet(line) && !line.startsWith("#")) {
		        			MUri uriDep = MUri.toUri(plugin.getUri());

		        			if (!uri.getScheme().equals("file") && uri.getScheme().equals(uriDep.getScheme()) )
		        				throw new MRuntimeException("scheme access denied",uri.getScheme(),uriDep.getScheme());

		        			Scheme schemeDep = con.getSchemes().get(uriDep);
		        			File depFile = schemeDep.load(con, uriDep);
		        			urls.add(depFile.toURI().toURL());
		        			log().d("Add dependency JAR",depFile);
		        		}
		        	}
		        }
			}
						
			URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), con.getClassLoader());
			entry = new Object[] {classes, cl };
			pluginClassLoaders.put(plugin.getUri(), entry);
		}
		
		@SuppressWarnings("unchecked")
		ArrayList<String> classes = (ArrayList<String>) entry[0];
		URLClassLoader cl = (URLClassLoader) entry[1];
		
		// scan classes
		for (String className : classes) {
			try {
				Class<?> clazz = cl.loadClass(className);
				AMojo mojoDef = clazz.getAnnotation(AMojo.class);
				log().t("class",clazz,mojoDef);
				if (mojoDef != null) {
					if (mojoDef.name().equals(mojoName)) {
						ConductorPlugin inst = (ConductorPlugin)clazz.getConstructor().newInstance();
						return inst;
					}
				}
			} catch (ClassNotFoundException cnfe) {
			    log().t(cnfe);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ie) {
				log().w(className, ie);
			}
		}
		
		throw new NotFoundException("Plugin not found",plugin, plugin.getUri(), mojoName );
	}

}