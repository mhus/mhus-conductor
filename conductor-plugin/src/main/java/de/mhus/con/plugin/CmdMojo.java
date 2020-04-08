package de.mhus.con.plugin;

import de.mhus.con.api.AMojo;
import de.mhus.con.api.ConUtil;
import de.mhus.con.api.Context;
import de.mhus.con.api.MojoException;
import de.mhus.lib.core.MString;

@AMojo(name="cmd")
public class CmdMojo extends AbstractExecute {

	@Override
	public void execute2(Context context) throws Exception {
		String cmd = MString.join(context.getStep().getArguments(), " "); //TODO add quotes and or escapes
		String[] res = ConUtil.execute(context.getStep().getTitle() + " " + context.getProject().getName(), context.getProject().getRootDir(), cmd);
		if (!res[2].equals("0"))
		    throw new MojoException(context, "not successful",cmd,res[1],res[2]);
	}

}