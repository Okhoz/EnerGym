package com.energym.app.domain;

import java.util.Objects;

/** Origen y permiso de progresión del snapshot; no dependen del perfil general ni de la vista. */
public final class RoutineMetadata {
	public enum Origin {AI, LOCAL_RULES, MANUAL}

	private final Origin origin;
	private final boolean progressionEnabled;

	public RoutineMetadata(Origin origin, boolean progressionEnabled) {
		this.origin = Objects.requireNonNull(origin, "origin");
		this.progressionEnabled = progressionEnabled;
	}

	public Origin getOrigin() {
		return origin;
	}

	public boolean isProgressionEnabled() {
		return progressionEnabled;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RoutineMetadata))
			return false;
		RoutineMetadata value = (RoutineMetadata) other;
		return origin == value.origin && progressionEnabled == value.progressionEnabled;
	}

	@Override
	public int hashCode() {
		return Objects.hash(origin, progressionEnabled);
	}
}
