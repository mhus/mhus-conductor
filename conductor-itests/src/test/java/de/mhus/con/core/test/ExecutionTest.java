package de.mhus.con.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.mhus.con.api.ConUtil;
import de.mhus.con.api.Conductor;
import de.mhus.con.core.ConductorImpl;
import de.mhus.con.core.ConfigTypesImpl;
import de.mhus.con.core.ConfiguratorDefault;
import de.mhus.con.core.ExecutorDefault;
import de.mhus.con.core.FileScheme;
import de.mhus.con.core.MainCli;
import de.mhus.con.core.MavenScheme;
import de.mhus.con.core.ProjectsValidator;
import de.mhus.con.core.SchemesImpl;
import de.mhus.con.core.YmlConfigType;
import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MProperties;
import de.mhus.lib.core.MSystem;
import de.mhus.lib.core.MXml;
import de.mhus.lib.errors.MException;

public class ExecutionTest {

    @Test
    public void testNewVersion() throws Exception {
        File to = new File("target/scenario");
        if (to.exists())
            MFile.deleteDir(to);
        to.mkdirs();
        
        File from = new File("../example2");
        MFile.copyDir(from, to);
        
        File root = new File(to, "sample-parent");
        
        String[] args = new String[] {
                "-vvv",
                "-p",
                root.getAbsolutePath(),
                "newVersion"
        };
        
        MainCli.main(args);
        
        // test
        {
            Element pomE = MXml.loadXml(new File(to, "sample-parent/pom.xml")).getDocumentElement();
            String version = MXml.getValue(pomE, "/version", "");
            assertEquals("1.0.0",version);
            {
                String pVersion = MXml.getValue(pomE, "properties/sample-core.version", "");
                assertEquals("1.0.0",pVersion);
            }
            {
                String pVersion = MXml.getValue(pomE, "properties/sample-api.version", "");
                assertEquals("0.0.1",pVersion);
            }
        }
        {
            Element pomE = MXml.loadXml(new File(to, "sample-api/pom.xml")).getDocumentElement();
            String version = MXml.getValue(pomE, "/version", "");
            assertEquals("1.0.0-SNAPSHOT",version);
            String parent = MXml.getValue(pomE, "/parent/version", "");
            assertEquals("1.0.0-SNAPSHOT",parent);
        }
        {
            Element pomE = MXml.loadXml(new File(to, "sample-core/pom.xml")).getDocumentElement();
            String version = MXml.getValue(pomE, "/version", "");
            assertEquals("1.0.0",version);
            String parent = MXml.getValue(pomE, "/parent/version", "");
            assertEquals("1.0.0",parent);
        }
        {
            MProperties hist = MProperties.load(new File(to,"sample-parent/history.properties"));
            assertEquals("1.0.0", hist.getString("core"));
            assertEquals("1.0.0", hist.getString("parent"));
            assertEquals("0.0.1", hist.getString("api"));
        }
    }
    @Test
    public void testExecution() throws MException, ParserConfigurationException, SAXException, IOException {

		TestUtil.enableDebug();

        Conductor con = new ConductorImpl(new File("../example/sample-parent"));
        
        ConfiguratorDefault config = new ConfiguratorDefault();
        ((SchemesImpl)config.getSchemes()).put("file", new FileScheme() );
        ((SchemesImpl)config.getSchemes()).put("mvn", new DummyScheme() );
        ((ConfigTypesImpl)config.getTypes()).put("yml", new YmlConfigType());
        ((ConfigTypesImpl)config.getTypes()).put("yaml", new YmlConfigType());
        
        config.getValidators().add(new ProjectsValidator());
        
        
        URI uri = URI.create("file:conductor.yml");
        config.configure(uri, con, null);

        String mvnPath = ConUtil.cmdLocationOrNull(con, "mvn");
        if (mvnPath != null) {
	        ((MProperties)con.getProperties()).put("conductor.version", TestUtil.conrentVersion());
	        ((SchemesImpl)con.getSchemes()).put("mvn",new MavenScheme());
	        
	        ExecutorDefault executor = new ExecutorDefault();
	        executor.execute(con, "default");
	        
        } else {
        	System.err.println("Maven not found, skip test: " + mvnPath);
        }
    }
    
    // @Test
    public void testCmdExecute() throws IOException {
        String mvnPath = ConUtil.cmdLocationOrNull(null, "mvn");
        if (mvnPath != null) {
    		ConUtil.execute("TEST", new File("../conductor-api"), mvnPath + " install");
    	} else {
        	System.err.println("Maven not found, skip test: " + mvnPath);
        }
    }
    
	//@Test
	public void testConPing() throws IOException {
		ConUtil.execute("TEST",new File("."), "ping -c 3 -i 2 google.com");
	}
	
	//@Test
	public void testDirectPing() {
		ProcessBuilder processBuilder = new ProcessBuilder();
		if (MSystem.isWindows())
			// Windows
			processBuilder.command("cmd.exe", "/c", "ping -n 3 google.com");
		else
			// Unix
			processBuilder.command("/bin/bash", "-c", "ping -c 3 google.com");
			
        try {

            Process process = processBuilder.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("\nExited with error code : " + exitCode);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
		
	}
	
}