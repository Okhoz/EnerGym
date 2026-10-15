package com.energym.server;

import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import javax.net.ssl.*;

/**
 * Adaptador HTTP (vista/entrada MVC): delega reglas al servicio y jamás registra mensajes, perfiles, claves o tokens.
 */
public final class Server implements AutoCloseable {
	private static final int MAX_BODY = 32768;
	private final HttpServer server;
	private final ChatService service;
	private final SessionTokenStore tokens;
	private final RenderIngress ingress;
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16));

	private Server(HttpServer server, ChatService service, SessionTokenStore tokens) {
		this(server, service, tokens, null);
	}

	private Server(HttpServer server, ChatService service, SessionTokenStore tokens, RenderIngress ingress) {
		this.server = server;
		this.service = service;
		this.tokens = tokens;
		this.ingress = ingress;
		server.setExecutor(executor);
		server.createContext("/", this::handle);
	}

	/**
	 * HTTP de desarrollo solo enlaza loopback; emuladores acceden mediante redirección adb.
	 */
	public static Server local(InetSocketAddress address, ChatService service, SessionTokenStore tokens) throws IOException {
		if (address.getAddress() == null || !address.getAddress().isLoopbackAddress())
			throw new IllegalArgumentException("HTTP solo loopback");
		return new Server(HttpServer.create(address, 16), service, tokens);
	}

	/**
	 * Fuera de desarrollo/loopback utiliza TLS directo; no confía en X-Forwarded-Proto del cliente.
	 */
	static Server configured(Map<String, String> env, ChatService service, SessionTokenStore tokens) throws Exception {
		if ("render".equals(env.get("ENERGYM_TRANSPORT"))) {
			RenderIngress ingress = RenderIngress.fromEnvironment(env);
			InetSocketAddress bind = new InetSocketAddress("0.0.0.0", Integer.parseInt(env.getOrDefault("PORT", "10000")));
			return new Server(HttpServer.create(bind, 16), service, null, ingress);
		}
		InetSocketAddress bind = new InetSocketAddress(env.getOrDefault("ENERGYM_HOST", "127.0.0.1"), Integer.parseInt(env.getOrDefault("ENERGYM_PORT", "8080")));
		boolean dev = "true".equals(env.getOrDefault("ENERGYM_DEV_MODE", "true"));
		String path = env.getOrDefault("ENERGYM_TLS_KEYSTORE", ""), password = env.getOrDefault("ENERGYM_TLS_PASSWORD", "");
		if (path.isBlank()) {
			if (!dev)
				throw new IllegalArgumentException("TLS obligatorio");
			return local(bind, service, tokens);
		}
		if (password.isBlank())
			throw new IllegalArgumentException("Contraseña TLS obligatoria");
		KeyStore store = KeyStore.getInstance("PKCS12");
		try (InputStream input = new FileInputStream(path)) {
			store.load(input, password.toCharArray());
		}
		KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		km.init(store, password.toCharArray());
		SSLContext ssl = SSLContext.getInstance("TLS");
		ssl.init(km.getKeyManagers(), null, null);
		HttpsServer https = HttpsServer.create(bind, 16);
		https.setHttpsConfigurator(new HttpsConfigurator(ssl) {
			@Override
			public void configure(HttpsParameters parameters) {
				SSLParameters defaults = ssl.getDefaultSSLParameters();
				defaults.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
				parameters.setSSLParameters(defaults);
			}
		});
		return new Server(https, service, tokens);
	}

	public void start() {
		server.start();
	}

	public int port() {
		return server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
			if (exchange.getRequestURI().getRawQuery() != null) {
				send(exchange, ChatService.error(400, "INVALID_REQUEST", false));
				return;
			}
			if (path.equals("/health")) {
				if (!method.equals("GET")) {
					send(exchange, ChatService.error(405, "METHOD_NOT_ALLOWED", false));
					return;
				}
				send(exchange, new ChatService.Result(200, Json.object().put("status", "ok").put("catalogVersion", service.catalogVersion())));
				return;
			}
			if (!Set.of("/api/session", "/api/chat").contains(path)) {
				send(exchange, ChatService.error(404, "NOT_FOUND", false));
				return;
			}
			if (ingress != null && !ingress.accepts(exchange.getRequestHeaders())) {
				send(exchange, ChatService.error(403, "HTTPS_REQUIRED", false));
				return;
			}
			if (path.equals("/api/session") && tokens == null) {
				send(exchange, ChatService.error(404, "NOT_FOUND", false));
				return;
			}
			String token = bearer(exchange);
			if (path.equals("/api/session") && method.equals("DELETE")) {
				if (!tokens.authenticated(token)) {
					send(exchange, ChatService.error(401, "UNAUTHORIZED", false));
					return;
				}
				tokens.revoke(token);
				send(exchange, new ChatService.Result(200, Json.object().put("revoked", true)));
				return;
			}
			if (!method.equals("POST")) {
				send(exchange, ChatService.error(405, "METHOD_NOT_ALLOWED", false));
				return;
			}
			if (path.equals("/api/session") && !tokens.allowPairingAttempt()) {
				send(exchange, ChatService.error(429, "RATE_LIMITED", true));
				return;
			}
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			if (contentType == null || !contentType.split(";")[0].trim().equalsIgnoreCase("application/json")) {
				send(exchange, ChatService.error(415, "JSON_REQUIRED", false));
				return;
			}
			byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
			if (raw.length > MAX_BODY) {
				send(exchange, ChatService.error(413, "REQUEST_TOO_LARGE", false));
				return;
			}
			JsonNode request = Json.parse(raw);
			if (path.equals("/api/session")) {
				Json.keys(request, Set.of("pairingCode"), Set.of("pairingCode"));
				String code = Json.text(request.get("pairingCode"), 128, false);
				SessionTokenStore.Token session = tokens.pair(code);
				if (session == null) {
					send(exchange, ChatService.error(401, "UNAUTHORIZED", false));
					return;
				}
				send(exchange, new ChatService.Result(201, Json.object().put("token", session.token()).put("expiresAtEpochSeconds", session.expiresAtEpochSeconds())));
				return;
			}
			send(exchange, service.chat(token, request));
		} catch (IllegalArgumentException | com.fasterxml.jackson.core.JacksonException e) {
			send(exchange, ChatService.error(400, "INVALID_REQUEST", false));
		} catch (Exception e) {
			send(exchange, ChatService.error(500, "SERVER_ERROR", true));
		} finally {
			exchange.close();
		}
	}

	private static String bearer(HttpExchange exchange) {
		List<String> values = exchange.getRequestHeaders().get("Authorization");
		if (values == null || values.size() != 1)
			return null;
		String value = values.get(0);
		return value.startsWith("Bearer ") && value.length() > 7 && value.length() <= 8199 &&
			!value.substring(7).matches(".*\\s.*") ? value.substring(7) : null;
	}

	private static void send(HttpExchange exchange, ChatService.Result result) throws IOException {
		byte[] bytes = result.body().toString().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		if (result.status() == 429)
			exchange.getResponseHeaders().set("Retry-After", "60");
		exchange.sendResponseHeaders(result.status(), bytes.length);
		exchange.getResponseBody().write(bytes);
	}

	public static void main(String[] args) {
		Map<String, String> env = System.getenv();
		// Límites del servidor JDK para clientes lentos y cabeceras excesivas; definidos antes de crear el servidor.
		System.setProperty("sun.net.httpserver.maxReqTime", "10");
		System.setProperty("sun.net.httpserver.maxRspTime", "render".equals(env.get("ENERGYM_TRANSPORT")) ? "90" : "25");
		System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
		System.setProperty("sun.net.httpserver.maxConnections", "32");
		try {
			boolean firebase = "firebase".equals(env.get("ENERGYM_AUTH_MODE"));
			if ("render".equals(env.get("ENERGYM_TRANSPORT")) && !firebase) throw new IllegalArgumentException("Firebase obligatorio");
			SessionTokenStore tokens = firebase ? null : new SessionTokenStore(env.get("ENERGYM_PAIRING_CODE"), Clock.systemUTC(), Duration.ofHours(1), 10);
			ChatService service = firebase ? new ChatService(new FirebaseIdTokenVerifier(env.get("FIREBASE_PROJECT_ID")),
				FirestoreQuota.fromEnvironment(env), ProviderFactory.fromEnvironment(env), new ProposalValidator()) :
				new ChatService(tokens, ProviderFactory.fromEnvironment(env), new ProposalValidator());
			Server server = configured(env, service, tokens);
			Runtime.getRuntime().addShutdownHook(new Thread(server::close));
			server.start();
			System.out.println("EnerGym backend iniciado; proveedor opcional desactivado salvo configuración explícita.");
		} catch (Exception e) {
			System.err.println("Configuración del backend inválida. Revisa README y variables sin publicar secretos.");
			System.exit(1);
		}
	}
}
