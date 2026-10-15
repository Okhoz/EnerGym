package com.energym.app.data;

/** Per-account bootstrap state. The serial database worker performs the cache inspection and restore. */
public final class InitialRestoreGate {
	private boolean pending, inspectCache;

	public void requireRestore() {
		pending = true;
		inspectCache = true;
	}

	public boolean needsCacheInspection() {
		return inspectCache;
	}

	public void cacheInspected(boolean hasRecords) {
		if (!inspectCache)
			return;
		inspectCache = false;
		if (hasRecords)
			pending = false;
	}

	/** Call only after a complete, validated SERVER batch has been committed, including an empty batch. */
	public void serverRestored() {
		pending = false;
		inspectCache = false;
	}

	public boolean pending() {
		return pending;
	}

	public void requireWritable() {
		if (pending)
			throw new IllegalStateException("Espera a que termine la restauración inicial. Puedes reintentar desde Cuenta.");
	}
}
