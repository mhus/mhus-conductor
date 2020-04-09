package de.mhus.con.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.reflections.Reflections;

import de.mhus.con.api.AMojo;
import de.mhus.con.api.AScheme;
import de.mhus.con.api.ConUtil;
import de.mhus.con.api.Conductor;
import de.mhus.con.api.ConductorPlugin;
import de.mhus.con.api.DirectLoadScheme;
import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MLog;
import de.mhus.lib.core.util.MUri;
import de.mhus.lib.errors.NotFoundException;

@AScheme(name="vm")
public class VmScheme extends MLog implements DirectLoadScheme {

	@Override
	public File load(Conductor con, MUri uri) throws IOException, NotFoundException {
		return null;
	}

	@Override
	public ConductorPlugin loadPlugin(MUri uri, String mojoName) throws NotFoundException, IOException {
        String pack = ConUtil.getMainPackageName();
        log().t("Scan Package", pack);
        
        Reflections reflections = new Reflections(pack);

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(AMojo.class) ) {
            AMojo def = clazz.getAnnotation(AMojo.class);
            log().t("AMojo",clazz,def);
            if (def != null && def.name().equals(mojoName)) {
                try {
                    Object inst = clazz.getConstructor().newInstance();
                    return (ConductorPlugin) inst;
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    throw new IOException(e);
                }
            }
        }
        throw new NotFoundException("Plugin not found", "vm", mojoName );
	}

	@Override
	public String loadContent(MUri uri) {
		String content = MFile.readFile(getClass().getResourceAsStream(uri.getPath()));
		return content;
	}

}