package de.mhus.cur.core;

import java.util.LinkedList;

import de.mhus.cur.api.Cli;
import de.mhus.cur.api.MainOption;
import de.mhus.cur.api.MainOptionHandler;

@MainOption(alias="--")
public class MainOptionReset implements MainOptionHandler {

	@Override
	public void execute(Cli cli, String cmd, LinkedList<String> queue) {
		((MainCli)cli).resetCur();
	}

	@Override
	public String getUsage(String cmd) {
		return null;
	}

	@Override
	public String getDescription(String cmd) {
		return "Reset the Conductor engine, this will cause to reload the configuration and all properties";
	}

}