package com.energym.app.wear;

import com.energym.app.domain.SetLog;

import java.util.*;

/**
 * MODELO de lo que ve el reloj durante un entreno: ejercicio, número de serie, la última serie apuntada y cuándo
 * acaba el descanso (hora de reloj de pared, porque el reloj no comparte el «desde el arranque» del móvil).
 * Mismas claves que lee el módulo :wear.
 */
public final class WearState {
	public static final String PATH = "/energym/session", REPEAT = "/energym/repeat";
	public static final String EXERCISE = "exercise", SET = "set", LAST = "last", REST_ENDS = "restEnds", ACTIVE = "active";

	public final boolean active;
	public final String exercise, last;
	public final int set;
	public final long restEnds;

	private WearState(boolean active, String exercise, int set, String last, long restEnds) {
		this.active = active;
		this.exercise = exercise;
		this.set = set;
		this.last = last;
		this.restEnds = restEnds;
	}

	public static WearState idle() {
		return new WearState(false, "", 0, "", 0);
	}

	/**
	 * @param restEndsElapsed fin del descanso en reloj desde el arranque (0 sin descanso).
	 * @param unit            «kg» o «lb» para la última serie.
	 */
	public static WearState of(String exercise, List<SetLog.Entry> sets, double factor, String unit,
		long restEndsElapsed, long nowElapsed, long nowWall) {
		String last = "";
		if (sets != null && !sets.isEmpty()) {
			SetLog.Entry e = sets.get(sets.size() - 1);
			last = (e.kg > 0 ? SetLog.display(Math.round(e.kg * factor * 100) / 100.0) + " " + unit + " × " : "") + e.reps;
		}
		long ends = restEndsElapsed > nowElapsed ? nowWall + (restEndsElapsed - nowElapsed) : 0;
		return new WearState(true, exercise == null ? "" : exercise, (sets == null ? 0 : sets.size()) + 1, last, ends);
	}

	/** Mapa plano para el Data Layer. */
	public Map<String, String> toMap() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put(ACTIVE, Boolean.toString(active));
		m.put(EXERCISE, exercise);
		m.put(SET, Integer.toString(set));
		m.put(LAST, last);
		m.put(REST_ENDS, Long.toString(restEnds));
		return m;
	}
}
