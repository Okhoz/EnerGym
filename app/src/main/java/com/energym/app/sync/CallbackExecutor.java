package com.energym.app.sync;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Frontera cerrable para SDK externos: cerrar antes del worker descarta entregas tardías.
 */
public final class CallbackExecutor implements Executor, AutoCloseable {
	private final Executor worker;
	private boolean closed;

	public CallbackExecutor(Executor worker) {
		this.worker = Objects.requireNonNull(worker);
	}

	public synchronized void execute(Runnable callback) {
		if (closed)
			return;
		// No captura rechazos activos: una mala configuración del worker debe seguir siendo visible.
		worker.execute(() -> {
			synchronized (CallbackExecutor.this) {
				if (!closed)
					callback.run();
			}
		});
	}

	public synchronized void close() {
		closed = true;
	}
}
