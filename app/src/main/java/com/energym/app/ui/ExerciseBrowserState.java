package com.energym.app.ui;

import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.TrainingProfile.*;
import java.util.Objects;

/** Estado del buscador conservado entre pantallas y separado por cuenta y borrador. */
public final class ExerciseBrowserState {
	public String query = "", discipline = "", environment = "", material = "", goal = "";
	public boolean expanded, focused, onlyCompatible = true;
	public int visible = 12, selection;
	private String owner;
	private long draftRevision;
	private long bindingRevision;

	public void resetFor(String nextOwner, long nextDraftRevision) {
		if (Objects.equals(owner, nextOwner) && draftRevision == nextDraftRevision) return;
		owner = nextOwner;
		draftRevision = nextDraftRevision;
		bindingRevision++;
		query = "";
		clearFilters();
		expanded = focused = false;
		onlyCompatible = true;
		selection = 0;
	}

	/** Una vista sólo puede capturar el estado de la cuenta y borrador a los que se vinculó. */
	public long bindingRevision() {
		return bindingRevision;
	}

	public void capture(long bindingRevision, boolean focused, int selection) {
		if (bindingRevision != this.bindingRevision) return;
		this.focused = focused;
		this.selection = Math.max(0, selection);
	}

	public void clearFilters() {
		discipline = environment = material = goal = "";
		visible = 12;
	}

	public ExerciseSearch.Filters filters() {
		String resource = "NONE".equals(material) || value(Resource.class, material) != null ? material : "";
		return new ExerciseSearch.Filters(discipline, value(Environment.class, environment), resource,
			value(Goal.class, goal));
	}

	public int activeCount() {
		ExerciseSearch.Filters filters = filters();
		return (filters.discipline.isEmpty() ? 0 : 1) + (filters.environment == null ? 0 : 1) +
			(filters.material.isEmpty() ? 0 : 1) + (filters.goal == null ? 0 : 1);
	}

	private static <E extends Enum<E>> E value(Class<E> type, String name) {
		if (name == null || name.isEmpty()) return null;
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException unknown) {
			return null;
		}
	}
}
