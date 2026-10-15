package com.energym.app.ui;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

/** Carga la foto fuera de la vista y descarta respuestas de otra cuenta o de una vista destruida. */
public final class RemoteAvatarController<I> implements AutoCloseable {
	public interface Fetch<I> { I get(String url) throws Exception; }
	private final Executor worker, main;
	private final Fetch<I> fetch;
	private final Consumer<I> changed;
	private String owner, url;
	private long revision, generation;
	private boolean initialized, closed;
	private I image;
	private FutureTask<Void> request;

	public RemoteAvatarController(Executor worker, Executor main, Fetch<I> fetch, Consumer<I> changed) {
		this.worker = worker;
		this.main = main;
		this.fetch = fetch;
		this.changed = changed;
	}

	/** La vista llama desde main: actualizar datos ajenos a la foto no repite la descarga. */
	public void update(String nextOwner, long nextRevision, String nextUrl) {
		if (closed) return;
		String source = nextOwner == null || nextUrl == null ? "" : nextUrl.trim();
		if (initialized && Objects.equals(owner, nextOwner) && revision == nextRevision && Objects.equals(url, source)) return;
		initialized = true;
		owner = nextOwner;
		revision = nextRevision;
		url = source;
		long token = ++generation;
		if (request != null) request.cancel(true);
		image = null;
		changed.accept(null);
		if (source.isEmpty()) return;
		request = new FutureTask<>(() -> {
			I result;
			try { result = fetch.get(source); }
			catch (Exception unavailable) { result = null; }
			I delivered = result;
			if (!Thread.currentThread().isInterrupted()) main.execute(() -> {
				if (!closed && token == generation) {
					image = delivered;
					changed.accept(delivered);
				}
			});
			return null;
		});
		try { worker.execute(request); }
		catch (RuntimeException stopped) { request.cancel(true); }
	}

	public I image() { return image; }

	/** También valida al leer: una Activity pausada puede haber omitido el render de fragmentos. */
	public I imageFor(String currentOwner, long currentRevision, String currentUrl) {
		return !closed && Objects.equals(owner, currentOwner) && revision == currentRevision &&
			Objects.equals(url, currentUrl == null ? "" : currentUrl.trim()) ? image : null;
	}

	@Override public void close() {
		closed = true;
		generation++;
		if (request != null) request.cancel(true);
		image = null;
	}
}
