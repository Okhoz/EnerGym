package com.energym.app.ui;

import java.util.Objects;

/** Estado de presentación de Entreno, conservado al navegar y separado por cuenta. */
public final class TrainingViewState {
	public enum Pane { MINE, AI, EXERCISES }

	public Pane pane = Pane.MINE;
	public boolean draftsExpanded, optionsExpanded;
	private String owner;
	private long bindingRevision;

	public void resetFor(String nextOwner) {
		if (Objects.equals(owner, nextOwner)) return;
		owner = nextOwner;
		bindingRevision++;
		pane = Pane.MINE;
		draftsExpanded = optionsExpanded = false;
	}

	public void select(Pane next) {
		pane = Objects.requireNonNull(next);
	}

	/** Invalida una restauración pendiente cuando se sale de una cuenta y después se vuelve a ella. */
	public long bindingRevision() {
		return bindingRevision;
	}
}
