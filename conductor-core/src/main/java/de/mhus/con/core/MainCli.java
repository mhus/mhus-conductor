package de.mhus.con.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.reflections.Reflections;

import de.mhus.con.api.AConfigType;
import de.mhus.con.api.AOption;
import de.mhus.con.api.AScheme;
import de.mhus.con.api.AValidator;
import de.mhus.con.api.Cli;
import de.mhus.con.api.Conductor;
import de.mhus.con.api.ConfigType;
import de.mhus.con.api.MainOptionHandler;
import de.mhus.con.api.Scheme;
import de.mhus.con.api.Validator;
import de.mhus.conductor.api.meta.Version;
import de.mhus.lib.core.MLog;
import de.mhus.lib.core.MProperties;
import de.mhus.lib.core.MString;
import de.mhus.lib.errors.MException;
import de.mhus.lib.errors.NotFoundException;

public class MainCli extends MLog implements Cli {

	protected Map<String, MainOptionHandler> optionHandlers = new HashMap<>();
	protected Map<String, Scheme> schemes = new HashMap<>();
	protected Map<String, ConfigType> configTypes = new HashMap<>();
	protected Map<String, Validator> validators = new HashMap<>();
	protected File rootDir = new File(".");
	protected ConductorImpl con;
	protected String configFile;
	
	public static void main(String[] args) throws Exception {
		
		if (args == null || args.length == 0) {
			System.out.println("Try --help");
			return;
		}
		
		LinkedList<String> queue = new LinkedList<>();
		for (String arg : args)
			queue.add(arg);
		
		new MainCli().execute(queue);
		
	}
	
	public MainCli() throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String pack = MString.beforeLastIndex(getClass().getPackageName(), '.');
		log().t("Scan Package", pack);
		
		Reflections reflections = new Reflections(pack);
				
		for (Class<?> clazz : reflections.getTypesAnnotatedWith(AOption.class) ) {
			AOption def = clazz.getAnnotation(AOption.class);
			log().t("AOption",clazz,def);
			if (def != null) {
				Object inst = clazz.getConstructor().newInstance();
				for (String alias : def.alias()) {
					MainOptionHandler old = optionHandlers.put(alias, (MainOptionHandler) inst);
					if (old != null)
						log().w("Overwrite",alias);
				}
			}
		}
		
		for (Class<?> clazz : reflections.getTypesAnnotatedWith(AScheme.class) ) {
			AScheme def = clazz.getAnnotation(AScheme.class);
			log().t("AScheme",clazz,def);
			if (def != null) {
				Object inst = clazz.getConstructor().newInstance();
				for (String alias : def.name()) {
					Scheme old = schemes.put(alias, (Scheme) inst);
					if (old != null)
						log().w("Overwrite",alias);
				}
			}
		}

		for (Class<?> clazz : reflections.getTypesAnnotatedWith(AConfigType.class) ) {
			AConfigType def = clazz.getAnnotation(AConfigType.class);
			log().t("AConfigType",clazz,def);
			if (def != null) {
				Object inst = clazz.getConstructor().newInstance();
				for (String alias : def.name()) {
					ConfigType old = configTypes.put(alias, (ConfigType) inst);
					if (old != null)
						log().w("Overwrite",alias);
				}
			}
		}
		
		for (Class<?> clazz : reflections.getTypesAnnotatedWith(AConfigType.class) ) {
			AConfigType def = clazz.getAnnotation(AConfigType.class);
			log().t("AConfigType",clazz,def);
			if (def != null) {
				Object inst = clazz.getConstructor().newInstance();
				for (String alias : def.name()) {
					ConfigType old = configTypes.put(alias, (ConfigType) inst);
					if (old != null)
						log().w("Overwrite",alias);
				}
			}
		}
		
		for (Class<?> clazz : reflections.getTypesAnnotatedWith(AValidator.class) ) {
			AValidator def = clazz.getAnnotation(AValidator.class);
			log().t("AValidator",clazz,def);
			if (def != null) {
				Object inst = clazz.getConstructor().newInstance();
				for (String alias : def.name()) {
					Validator old = validators.put(alias, (Validator) inst);
					if (old != null)
						log().w("Overwrite",alias);
				}
			}
		}
		
		log().t("optionHandlers",optionHandlers);
	}
	
	protected void execute(LinkedList<String> queue) throws MException {
		
		MProperties execProperties = null;
		String execLifecycle = null;
		
		while (queue.size() > 0) {
			String next = queue.removeFirst();
			if (next.startsWith("-")) {
				if (execLifecycle != null) {
					executeLifecycle(execLifecycle, execProperties);
					execLifecycle = null;
					execProperties = null;
				}
				executeOption(next, queue);
				
			} else
			if (execLifecycle == null) {
				execLifecycle = next;
				execProperties = new MProperties();
			} else {
				if (MString.isIndex(next, '='))
					execProperties.put(MString.beforeIndex(next, '=').trim(), MString.afterIndex(next, '='));
			}
		}
		
		if (execLifecycle != null) {
			executeLifecycle(execLifecycle, execProperties);
			execLifecycle = null;
			execProperties = null;
		}
		
		resetCon();
		
	}

	private void executeOption(String next, LinkedList<String> queue) throws NotFoundException {
		MainOptionHandler handler = optionHandlers.get(next);
		if (handler == null) throw new NotFoundException("option",next);
		handler.execute(this,next, queue);
	}

	private void executeLifecycle(String execLifecycle, MProperties execProperties) throws MException {
		
		createConductor();

		((MProperties)con.getProperties()).putReadProperties(execProperties);
        ExecutorDefault executor = new ExecutorDefault();
        
        executor.execute(con, execLifecycle);

	}

	private void createConductor() throws MException {
		if (con != null) return;
		log().d("Create conductor object");
		ConfiguratorDefault config = new ConfiguratorDefault();
		
		for (Entry<String, Scheme> entry : schemes.entrySet())
			((SchemesImpl)config.getSchemes()).put(entry.getKey(), entry.getValue() );
		for (Entry<String, ConfigType> entry : configTypes.entrySet())
			((ConfigTypesImpl)config.getTypes()).put(entry.getKey(), entry.getValue());
		for (Entry<String, Validator> entry :validators.entrySet())
			config.getValidators().add(entry.getValue());

        if (configFile == null) {
        	// set default
        	File file = new File(rootDir, "conductor.yml");
        	if (file.exists() && file.isFile())
        		configFile = "file:conductor.yml";
        	else
        		configFile = "mvn:de.mhus.conductor/conductor-plugin/"+Version.VERSION+"/yml/configuration-default";
        }
        URI uri = URI.create(configFile);
        
        con = new ConductorImpl(rootDir);
        
        config.configure(uri, con, null);
		
	}

	@Override
	public Map<String, MainOptionHandler> getOptions() {
		return optionHandlers;
	}

	public void resetCon() {
		if (con == null) return;
		con.close();
		con = null;
	}

    @Override
    public Map<String, Scheme> getSchemes() {
        return schemes;
    }

    @Override
    public Map<String, ConfigType> getConfigTypes() {
        return configTypes;
    }

    @Override
    public Map<String, Validator> getValidators() {
        return validators;
    }

    @Override
    public Conductor getConductor() {
        try {
            createConductor();
        } catch (MException e) {
            log().w(e);
        }
        return con;
    }

}