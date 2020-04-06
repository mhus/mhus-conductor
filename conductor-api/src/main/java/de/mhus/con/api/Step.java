package de.mhus.con.api;

import java.util.LinkedList;

import de.mhus.lib.core.IProperties;

public interface Step {

    LinkedList<String> getArguments();

    Labels getSelector();

    String getSortBy();

    boolean isOrderAsc();

    String getTarget();

    String getCondition();

	boolean matchCondition(Context context);

	String getTitle();

	IProperties getProperties();
    
}