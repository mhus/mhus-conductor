package de.mhus.con.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.LinkedList;

import de.mhus.conductor.api.meta.Version;
import de.mhus.lib.core.MString;
import de.mhus.lib.core.MSystem;
import de.mhus.lib.core.console.Console;
import de.mhus.lib.core.console.XTermConsole;
import de.mhus.lib.core.console.Console.COLOR;
import de.mhus.lib.core.logging.Log;
import de.mhus.lib.core.util.MUri;
import de.mhus.lib.errors.NotFoundException;

public class ConUtil {

	private static final Log log = Log.getLog(Conductor.class);
	public static final String PROPERTY_FAE = "conductor.fae";
	public static final String PROPERTY_CMD_PATH = "conductor.cmd.";
	public static final String PROPERTY_PATH = "conductor.path";
	public static final String DEFAULT_PATHES_UNIX = "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin";
	public static final String DEFAULT_PATHES_WINDOWS = "C:\\Program Files;C:\\Winnt;C:\\Winnt\\System32";
    public static final String PROPERTY_VERSION = "conductor.version";
    public static final String PROPERTY_LIFECYCLE = "conductor.lifecycle";
    public static final String PROPERTY_DOWNLOAD_SNAPSHOTS = "conductor.downloadSnapshots";
    private static Console console;
	
    public static void orderProjects(LinkedList<Project> projects, String order, boolean orderAsc) {
        projects.sort(new Comparator<Project>() {

            @Override
            public int compare(Project o1, Project o2) {
                int ret = compare( o1.getLabels().getOrNull(order), o2.getLabels().getOrNull(order));
                if (!orderAsc) ret = ret * -1;
                return ret;
            }

            private int compare(String o1, String o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                return o1.compareTo(o2);
            }
        });
    }

	public static String[] execute(String name, File rootDir, String cmd) throws IOException {
		
		log.i(name,"execute",cmd,rootDir);

		ProcessBuilder processBuilder = new ProcessBuilder();
		if (rootDir != null)
		    processBuilder.directory(rootDir);
		if (MSystem.isWindows())
			// Windows
			processBuilder.command("cmd.exe", "/c", cmd);
		else
			// Unix
			processBuilder.command("/bin/bash", "-c", cmd);

		Console console = getConsole();
		
        try {

            Process process = processBuilder.start();

            BufferedReader outReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder stdOutBuilder = new StringBuilder();
            
            String line;
            while ((line = outReader.readLine()) != null) {
                
                console.print("[");
                console.setColor(COLOR.GREEN, null);
                console.print(name);
                console.cleanup();
                console.print("] ");
                console.println(line);
                
                if (stdOutBuilder.length() > 0) 
                	stdOutBuilder.append("\n");
                stdOutBuilder.append(line);
            }

            BufferedReader errReader =
                    new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder stdErrBuilder = new StringBuilder();
            while ((line = errReader.readLine()) != null) {
                
                console.print("[");
                console.setColor(COLOR.RED, null);
                console.print(name);
                console.cleanup();
                console.print("] ");
                console.println(line);
                
                if (stdErrBuilder.length() > 0) 
                	stdErrBuilder.append("\n");
                stdErrBuilder.append(line);
            }

            int exitCode = process.waitFor();
    		String stderr = stdErrBuilder.toString();
    		String stdout = stdOutBuilder.toString();
    		log.i(name,"exitCode",exitCode);
    		log.t("result",stdout,stderr,exitCode);
    		return new String[] {stdout, stderr, String.valueOf(exitCode)};
            

        } catch (InterruptedException e) {
            throw new IOException(e);
        }
		
		
	}

	public static Console getConsole() {
	    if (console == null) {
	        String term = System.getenv("TERM");
            if (term != null) {
                term = term.toLowerCase();
                if (term.indexOf("xterm") >= 0) {
                    try {
                        console = new XTermConsole() {
                            @Override
                            public boolean isSupportColor() {
                                return true;
                            }
        
                        };
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            if (console == null) console = Console.get();
            Console.set(console);
	    }
        return console;
    }

    public static String cmdLocationOrNull(Conductor con, String cmd) {
		try {
			return cmdLocation(con, cmd);
		} catch (NotFoundException e) {
			return null;
		}
	}
	
	//TODO cache findings
	public static String cmdLocation(Conductor con, String cmd) throws NotFoundException {
		if (con != null) {
			// check direct configuration
			String path = con.getProperties().getString(ConUtil.PROPERTY_CMD_PATH + cmd.toUpperCase(), null);
			if (path != null) return path;
		}
		String[] pathes = null;
		if (MSystem.isWindows())
			pathes = con.getProperties().getString(ConUtil.PROPERTY_PATH, DEFAULT_PATHES_WINDOWS).split(";");
		else
			pathes = con.getProperties().getString(ConUtil.PROPERTY_PATH, DEFAULT_PATHES_UNIX).split(":");
		
		for (String path : pathes) {
			File file = new File(path + File.separator + cmd);
			if (file.exists() && file.isFile() && file.canExecute() && file.canRead())
				return file.getAbsolutePath();
		}
		throw new NotFoundException("Command not found",cmd);
	}

    public static MUri getDefaultConfiguration(String name) {
        String ext = MString.afterLastIndex(name, '.');
        name = MString.beforeLastIndex(name, '.');
        MUri uri = MUri.toUri("mvn:de.mhus.conductor/conductor-plugin/"+Version.VERSION+"/"+ext+"/"+name);
        return uri;
    }

    public static File getFile(File root, String path) {
        File f = new File(path);
        if (!f.isAbsolute())
            f = new File(root, path);
        return f;
    }

}
