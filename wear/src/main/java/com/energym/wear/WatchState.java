package com.energym.wear;

import java.util.*;

/**
 * MODELO del reloj: lee el estado que publica el móvil (mismas claves que com.energym.app.wear.WearState) y
 * calcula el descanso que queda.
 */
public final class WatchState {
	public static final String PATH = "/energym/session", REPEAT = "/energym/repeat";

	public final boolean active;
	public final String exercise, last;
	public final int set;
	public final long restEnds;

	WatchState(boolean active, String exercise, int set, String last, long restEnds) {
		this.active = active;
		this.exercise = exercise;
		this.set = set;
		this.last = last;
		this.restEnds = restEnds;
	}

	public static WatchState idle() {
		return new WatchState(false, "", 0, "", 0);
	}

	public static WatchState from(Map<String, String> values) {
		if (values == null || !"true".equals(values.get("active")))
			return idle();
		return new WatchState(true, values.getOrDefault("exercise", ""), (int) number(values.get("set")),
			values.getOrDefault("last", ""), number(values.get("restEnds")));
	}

	/** Segundos de descanso que quedan (0 si no hay descanso o ya acabó). */
	public long restLeft(long now) {
		return restEnds > now ? (restEnds - now + 999) / 1000 : 0;
	}

	public static String clock(long seconds) {
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	private static long number(String text) {
		try {
			return text == null ? 0 : Long.parseLong(text.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
