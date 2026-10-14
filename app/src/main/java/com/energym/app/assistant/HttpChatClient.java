package com.energym.app.assistant;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * ADAPTADOR HTTP: opt-in explícito, sin reintentos ni secretos persistidos; IO siempre fuera de main.
 */
public final class HttpChatClient implements ChatClient {
	private final Executor worker, delivery;
	private final boolean debug;
	private String endpoint, token;
	private long epoch;

	public HttpChatClient(Executor worker, Executor delivery, boolean debug) {
		this.worker = worker;
		this.delivery = delivery;
		this.debug = debug;
	}

	/**
	 * Release exige HTTPS. HTTP debug solo llega a loopback literal, nunca a una IP privada remota.
	 */
	public static String endpoint(String raw, boolean debug) {
		try {
			URI uri = new URI(raw.trim());
			String host = uri.getHost();
			if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
				uri.getRawFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
				throw new IllegalArgumentException();
			boolean https = "https".equals(uri.getScheme());
			boolean loop =
				StrictJson.set("localhost", "127.0.0.1", "[::1]", "::1").contains(host.toLowerCase(Locale.ROOT));
			if (!https && !(debug && "http".equals(uri.getScheme()) && loop))
				throw new IllegalArgumentException();
			String e = uri.toString();
			return e.endsWith("/") ? e.substring(0, e.length() - 1) : e;
		} catch (Exception e) {
			throw new IllegalArgumentException("URL inválida: usa HTTPS; solo debug permite HTTP en localhost.");
		}
	}

	public synchronized boolean connected() {
		return token != null;
	}

	public void connect(String raw, String pairing, Callback callback) {
		final String address;
		try {
			address = endpoint(raw, debug);
			if (pairing == null || pairing.length() < 24 || pairing.length() > 128)
				throw new IllegalArgumentException();
		} catch (Exception e) {
			delivery.execute(() -> callback.failure("CONFIGURACION_INVALIDA"));
			return;
		}
		disconnect();
		final long generation;
		synchronized (this) {
			generation = epoch;
		}
		worker.execute(() -> {
			try {
				Response r = http(address, "/api/session", "POST", null,
					StrictJson.write(StrictJson.map("pairingCode", pairing)));
				if (r.status != 201) {
					error(r, null);
					delivery.execute(() -> callback.failure(code(r)));
					return;
				}
				Map<String, Object> m = StrictJson.object(StrictJson.parse(r.json));
				StrictJson.keys(m, "token", "expiresAtEpochSeconds");
				String credential = StrictJson.text(m.get("token"), 256);
				if (!credential.matches("[A-Za-z0-9_-]{32,256}") ||
					!StrictJson.integral(m.get("expiresAtEpochSeconds")) ||
					((java.math.BigDecimal) m.get("expiresAtEpochSeconds")).longValueExact() <=
						System.currentTimeMillis() / 1000)
					throw new IllegalArgumentException();
				// El monitor protege solamente memoria: revocar una credencial tardía nunca debe bloquear main por IO.
				boolean stale;
				synchronized (this) {
					stale = epoch != generation;
					if (!stale) {
						endpoint = address;
						token = credential;
					}
				}
				if (stale) {
					revoke(address, credential);
					return;
				}
				delivery.execute(() -> {
					synchronized (this) {
						if (epoch != generation)
							return;
					}
					callback.success("");
				});
			} catch (Exception e) {
				delivery.execute(() -> callback.failure("CONEXION_O_RESPUESTA_INVALIDA"));
			}
		});
	}

	public void send(Request request, Callback callback) {
		final String address, credential;
		final long generation;
		synchronized (this) {
			address = endpoint;
			credential = token;
			generation = epoch;
		}
		if (credential == null) {
			delivery.execute(() -> callback.failure("UNAUTHORIZED"));
			return;
		}
		worker.execute(() -> {
			try {
				Response r = http(address, "/api/chat", "POST", credential, ChatProtocol.request(request));
				String failure = null;
				if (r.status != 200) {
					error(r, request);
					failure = code(r);
					if (r.status == 401)
						synchronized (this) {
							if (epoch == generation)
								token = null;
						}
				}
				String result = failure;
				delivery.execute(() -> {
					synchronized (this) {
						if (epoch != generation)
							return;
					}
					if (result == null)
						callback.success(r.json);
					else
						callback.failure(result);
				});
			} catch (Exception e) {
				delivery.execute(() -> {
					synchronized (this) {
						if (epoch != generation)
							return;
					}
					callback.failure("CONEXION_O_RESPUESTA_INVALIDA");
				});
			}
		});
	}

	/**
	 * Revocación best-effort: el token deja de ser utilizable inmediatamente en el cliente, también offline.
	 */
	public void disconnect() {
		final String address, credential;
		synchronized (this) {
			epoch++;
			address = endpoint;
			credential = token;
			token = null;
			endpoint = null;
		}
		if (credential != null)
			worker.execute(() -> revoke(address, credential));
	}

	private void revoke(String endpoint, String token) {
		try {
			http(endpoint, "/api/session", "DELETE", token, null);
		} catch (Exception ignored) {/* Si no hay red, el servidor conserva su caducidad de una hora. */}
	}

	private static final class Response {
		final int status;
		final String json;

		Response(int s, String j) {
			status = s;
			json = j;
		}
	}

	private static Response http(String address, String path, String method, String credential,
		String body) throws IOException {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(27);
		HttpURLConnection c = (HttpURLConnection) new URL(address + path).openConnection();
		try {
			c.setInstanceFollowRedirects(false);
			c.setConnectTimeout(5000);
			c.setReadTimeout(22000);
			c.setRequestMethod(method);
			c.setRequestProperty("Accept", "application/json");
			if (credential != null)
				c.setRequestProperty("Authorization", "Bearer " + credential);
			if (body != null) {
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				if (bytes.length > 32768)
					throw new IOException();
				c.setDoOutput(true);
				c.setRequestProperty("Content-Type", "application/json");
				c.setFixedLengthStreamingMode(bytes.length);
				try (OutputStream out = c.getOutputStream()) {
					out.write(bytes);
				}
			}
			int status = c.getResponseCode();
			String type = c.getContentType();
			if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json"))
				throw new IOException();
			InputStream input = status >= 400 ? c.getErrorStream() : c.getInputStream();
			if (input == null)
				throw new IOException();
			try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] b = new byte[4096];
				int count;
				while (true) {
					long remaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
					if (remaining <= 0)
						throw new SocketTimeoutException();
					c.setReadTimeout((int) Math.min(22000, Math.max(1, remaining)));
					count = in.read(b);
					if (count == -1)
						break;
					if (out.size() + count > 65536)
						throw new IOException();
					out.write(b, 0, count);
				}
				return new Response(status,
					StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
						.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
						.decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString());
			}
		} finally {
			c.disconnect();
		}
	}

	private static String code(Response r) {
		return StrictJson.text(StrictJson.object(StrictJson.parse(r.json)).get("error"), 64);
	}

	private static void error(Response r, Request request) {
		Map<String, Object> m = StrictJson.object(StrictJson.parse(r.json));
		Set<String> keys = new HashSet<>(m.keySet());
		if (keys.contains("conversationId")) {
			StrictJson.keys(m, "error", "recoverable", "conversationId", "requestId", "exerciseId");
			if (request == null || !request.conversationId.equals(m.get("conversationId")) ||
				!request.requestId.equals(m.get("requestId")) ||
				!Objects.equals(request.exerciseId, m.get("exerciseId")))
				throw new IllegalArgumentException();
		} else
			StrictJson.keys(m, "error", "recoverable");
		String code = StrictJson.text(m.get("error"), 64);
		Map<Integer, Set<String>> allowed =
			StrictJson.map(400, StrictJson.set("INVALID_REQUEST"), 401, StrictJson.set("UNAUTHORIZED"), 404,
				StrictJson.set("NOT_FOUND"), 405, StrictJson.set("METHOD_NOT_ALLOWED"), 413,
				StrictJson.set("REQUEST_TOO_LARGE"), 415, StrictJson.set("JSON_REQUIRED"), 429,
				StrictJson.set("RATE_LIMITED", "SERVER_BUSY"), 502,
				StrictJson.set("INVALID_PROVIDER_RESPONSE", "PROVIDER_UNAVAILABLE"), 503,
				StrictJson.set("PROVIDER_NOT_CONFIGURED", "PROVIDER_UNAVAILABLE"), 504,
				StrictJson.set("PROVIDER_TIMEOUT"), 500, StrictJson.set("SERVER_ERROR"));
		if (!allowed.getOrDefault(r.status, Collections.emptySet()).contains(code) ||
			!Objects.equals(m.get("recoverable"), r.status == 429 || r.status >= 500))
			throw new IllegalArgumentException();
	}
}



