package de.mhus.cur.core;

import java.util.LinkedList;
import java.util.List;

public class ProjectsImpl extends XCollection<Project> implements Projects {

	@SuppressWarnings("unused")
    private Conductor cur;

    @Override
	public List<Project> select(Labels labels) {
		LinkedList<Project> ret = new LinkedList<>();
		collection.forEach((k,v) -> {
			if (v.getLabels().matches(labels))
				ret.add(v);
		});
		return ret;
	}

    public void init(Conductor cur) {
        this.cur = cur;
    }

    @Override
    public List<Project> getAll() {
        return new LinkedList<Project>(collection.values());
    }

}