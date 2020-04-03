package de.mhus.cur.core;

import java.io.File;

public interface Project {

	Labels getLabels();

    String getName();

    String getPath();

    File getRootDir();
	
}