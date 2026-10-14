package com.energym.app.visual;

/**
 * Cuenta solo los fotogramas de carga activos; volver del fondo no consume el plazo.
 */
final class ForegroundLoadClock {
	private long elapsed, previous;

	void frame(long nanos) {
		if (previous != 0)
			elapsed += Math.max(0, nanos - previous);
		previous = nanos;
	}

	void pause() {
		previous = 0;
	}

	boolean expired() {
		return elapsed > 30_000_000_000L;
	}
}
