package de.mhus.deploy.api;

import de.mhus.lib.core.util.MUri;

public interface Schemes extends ICollection<Scheme> {

    Scheme get(MUri uri);

}