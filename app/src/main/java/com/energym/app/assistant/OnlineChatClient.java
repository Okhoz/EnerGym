package com.energym.app.assistant;

import java.util.concurrent.Executor;
import java.util.Objects;

/** Cliente del servicio público, autenticado por la cuenta Firebase vigente. */
public final class OnlineChatClient implements ChatClient {
	private final Executor worker, delivery;
	private final TokenSource tokens;
	private final String endpoint, configurationError;
	private String owner;
	private long epoch;
	public interface TokenSource {
		String currentUid();
		void getToken(String expectedUid, Callback callback);
	}

	public OnlineChatClient(Executor worker, Executor delivery, boolean debug, String endpoint, TokenSource tokens) {
		this.worker = Objects.requireNonNull(worker);
		this.delivery = Objects.requireNonNull(delivery);
		this.tokens = Objects.requireNonNull(tokens);
		String address = null;
		String error = "";
		if (endpoint == null || endpoint.trim().isEmpty())
			error = "BACKEND_NOT_CONFIGURED";
		else {
			try {
				address = HttpChatClient.endpoint(endpoint, debug);
			} catch (IllegalArgumentException invalid) {
				error = "CONFIGURACION_INVALIDA";
			}
		}
		this.endpoint = address;
		configurationError = error;
	}

	public synchronized void activate(String owner) {
		if (Objects.equals(this.owner, owner))
			return;
		epoch++;
		this.owner = owner;
	}

	public boolean automaticConnection() {
		return true;
	}

	public boolean connected() {
		return unavailableCode().isEmpty();
	}

	public synchronized String unavailableCode() {
		if (!configurationError.isEmpty())
			return configurationError;
		return owner != null && owner.equals(tokens.currentUid()) ? "" : "SIGN_IN_REQUIRED";
	}

	public void send(Request request, Callback callback) {
		final String uid;
		final long generation;
		final String unavailable;
		synchronized (this) {
			uid = owner;
			generation = epoch;
			unavailable = unavailableCode();
		}
		if (!unavailable.isEmpty()) {
			delivery.execute(() -> {
				synchronized (OnlineChatClient.this) {
					if (epoch != generation)
						return;
				}
				callback.failure(unavailable);
			});
			return;
		}
		try {
			tokens.getToken(uid, new Callback() {
				public void success(String token) {
					if (!live(uid, generation))
						return;
					if (token == null || token.isEmpty() || token.length() > 16384 ||
						token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
						failure("AUTHENTICATION_UNAVAILABLE");
						return;
					}
					worker.execute(() -> {
						if (!live(uid, generation))
							return;
						try {
							String json = HttpChatClient.sendBearer(endpoint, token, request);
							deliver(uid, generation, callback, json, null);
						} catch (HttpChatClient.RemoteFailure failure) {
							deliver(uid, generation, callback, null, failure.code);
						} catch (Exception failure) {
							deliver(uid, generation, callback, null, "CONEXION_O_RESPUESTA_INVALIDA");
						}
					});
				}

				public void failure(String error) {
					deliver(uid, generation, callback, null, error);
				}
			});
		} catch (RuntimeException failure) {
			deliver(uid, generation, callback, null, "AUTHENTICATION_UNAVAILABLE");
		}
	}

	public void connect(String endpoint, String code, Callback callback) {
		delivery.execute(() -> callback.failure("PAIRING_DISABLED"));
	}

	public synchronized void disconnect() {
		epoch++;
		owner = null;
	}

	@Override
	public synchronized void cancelPending() {
		epoch++;
	}

	private synchronized boolean live(String uid, long generation) {
		return epoch == generation && Objects.equals(owner, uid) && Objects.equals(tokens.currentUid(), uid);
	}

	private void deliver(String uid, long generation, Callback callback, String json, String error) {
		delivery.execute(() -> {
			if (!live(uid, generation))
				return;
			if (error == null)
				callback.success(json);
			else
				callback.failure(error);
		});
	}
}
