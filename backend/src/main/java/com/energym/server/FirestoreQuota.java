package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Reserva persistente y atómica de usuario + global. Sólo guarda contadores; no contiene conversaciones. */
public final class FirestoreQuota implements Quota {
	private final String endpoint, documentRoot, scope, salt;
	private final Limits limits;
	private final TokenSupplier tokens;
	private final ServiceAccountAccessToken.Transport transport;
	private final Clock clock;
	private final java.util.function.LongSupplier nanoTime;

	record Limits(int uidDay, int globalDay, int uidMinute, int globalMinute) {
		Limits {
			if (uidDay < 1 || globalDay < 1 || uidMinute < 1 || globalMinute < 1)
				throw new IllegalArgumentException("Los límites de cuota deben ser positivos");
		}
	}
	@FunctionalInterface interface TokenSupplier { String accessToken() throws Exception; }
	private record Counter(String day, long daily, long minute, long perMinute, String updateTime) {}
	private static final class Aborted extends IOException {}

	public static FirestoreQuota fromEnvironment(Map<String, String> env) throws Exception {
		String project = ServiceAccountAccessToken.projectId(required(env, "FIREBASE_PROJECT_ID"));
		String salt = required(env, "QUOTA_UID_SALT");
		String scope = env.getOrDefault("QUOTA_SCOPE", "energym");
		Limits limits = new Limits(limit(env, "QUOTA_UID_PER_DAY", 20), limit(env, "QUOTA_GLOBAL_PER_DAY", 200),
			limit(env, "QUOTA_UID_PER_MINUTE", 3), limit(env, "QUOTA_GLOBAL_PER_MINUTE", 20));
		ServiceAccountAccessToken access = new ServiceAccountAccessToken(Path.of(required(env, "GOOGLE_APPLICATION_CREDENTIALS")), project);
		return new FirestoreQuota(project, scope, salt, limits, access::accessToken, ServiceAccountAccessToken::post, Clock.systemUTC());
	}

	FirestoreQuota(String project, String scope, String salt, Limits limits, TokenSupplier tokens,
		ServiceAccountAccessToken.Transport transport, Clock clock) {
		this(project, scope, salt, limits, tokens, transport, clock, System::nanoTime);
	}
	FirestoreQuota(String project, String scope, String salt, Limits limits, TokenSupplier tokens,
		ServiceAccountAccessToken.Transport transport, Clock clock, java.util.function.LongSupplier nanoTime) {
		ServiceAccountAccessToken.projectId(project);
		if (scope == null || !scope.matches("[A-Za-z0-9_-]{1,64}") || salt == null || salt.isBlank() || salt.length() < 32 || salt.length() > 1024)
			throw new IllegalArgumentException("Scope o secreto de cuota inválido");
		this.scope = scope;
		this.salt = salt;
		this.limits = Objects.requireNonNull(limits);
		this.tokens = Objects.requireNonNull(tokens);
		this.transport = Objects.requireNonNull(transport);
		this.clock = Objects.requireNonNull(clock);
		this.nanoTime = Objects.requireNonNull(nanoTime);
		String database = "projects/" + project + "/databases/(default)";
		endpoint = "https://firestore.googleapis.com/v1/" + database + "/documents";
		documentRoot = database + "/documents/assistant_quotas/";
	}

	@Override public boolean reserve(String uid) throws Exception {
		if (uid == null || uid.isBlank() || uid.length() > 128) throw unavailable();
		long started = nanoTime.getAsLong();
		String uidName = documentRoot + scope + "_u_" + hash(uid);
		String globalName = documentRoot + scope + "_global";
		for (int attempt = 0; attempt <= 3; attempt++) {
			String transaction = null;
			String bearer = null;
			boolean committed = false;
			try {
				checkBudget(started);
				bearer = tokens.accessToken();
				JsonNode begin = request(":beginTransaction", Map.of("options", Map.of("readWrite", Map.of())), bearer, started);
				transaction = Json.text(begin.get("transaction"), 8192, false);
				JsonNode rows = request(":batchGet", Map.of("documents", List.of(globalName, uidName), "transaction", transaction), bearer, started);
				Map<String, Counter> counters = counters(rows, Set.of(globalName, uidName));
				Instant instant = clock.instant();
				String day = instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
				long minute = Math.floorDiv(instant.getEpochSecond(), 60);
				Counter global = current(counters.get(globalName), day, minute);
				Counter user = current(counters.get(uidName), day, minute);
				if (global.daily >= limits.globalDay || global.perMinute >= limits.globalMinute ||
					user.daily >= limits.uidDay || user.perMinute >= limits.uidMinute) return false;
				JsonNode result = request(":commit", Map.of("transaction", transaction, "writes",
					List.of(write(globalName, global), write(uidName, user))), bearer, started);
				if (!result.path("writeResults").isArray() || result.path("writeResults").size() != 2) throw unavailable();
				Instant.parse(Json.text(result.get("commitTime"), 64, false));
				committed = true;
				return true;
			} catch (Aborted aborted) {
				if (attempt == 3) throw unavailable();
			} catch (Exception failure) {
				throw unavailable();
			} finally {
				if (transaction != null && !committed) {
					try { request(":rollback", Map.of("transaction", transaction), bearer, started); }
					catch (Exception ignored) { /* Nunca se concede acceso a causa del resultado de rollback. */ }
				}
			}
		}
		throw unavailable();
	}

	private void checkBudget(long started) throws IOException {
		if (Thread.currentThread().isInterrupted() || nanoTime.getAsLong() - started >= Duration.ofSeconds(25).toNanos())
			throw unavailable();
	}
	private JsonNode request(String operation, Object payload, String bearer, long started) throws Exception {
		checkBudget(started);
		ServiceAccountAccessToken.Response response = transport.post(URI.create(endpoint + operation), bearer,
			"application/json", Json.MAPPER.writeValueAsBytes(payload));
		checkBudget(started);
		if (response.body() == null || response.body().length > 65536) throw unavailable();
		JsonNode body = Json.parse(response.body());
		if (response.status() != 200) {
			if ("ABORTED".equals(body.path("error").path("status").asText())) throw new Aborted();
			throw unavailable();
		}
		if (body == null) throw unavailable();
		return body;
	}

	private Map<String, Counter> counters(JsonNode rows, Set<String> names) throws Exception {
		if (!rows.isArray() || rows.size() != 2) throw unavailable();
		Map<String, Counter> result = new HashMap<>();
		for (JsonNode row : rows) {
			String name;
			Counter counter;
			if (row.has("found") && !row.has("missing")) {
				JsonNode found = row.get("found"), fields = found.get("fields");
				name = Json.text(found.get("name"), 1024, false);
				String update = Json.text(found.get("updateTime"), 64, false);
				Instant.parse(update);
				Json.keys(fields, Set.of("day", "dayCount", "minutePeriod", "minuteCount"), Set.of("day", "dayCount", "minutePeriod", "minuteCount"));
				String day = Json.text(fields.path("day").get("stringValue"), 10, false);
				LocalDate.parse(day);
				counter = new Counter(day, count(fields.get("dayCount")), count(fields.get("minutePeriod")), count(fields.get("minuteCount")), update);
			} else if (row.has("missing") && !row.has("found")) {
				name = Json.text(row.get("missing"), 1024, false);
				counter = new Counter("", 0, 0, 0, null);
			} else throw unavailable();
			if (!names.contains(name) || result.putIfAbsent(name, counter) != null) throw unavailable();
		}
		return result;
	}

	private static long count(JsonNode field) throws Exception {
		Json.keys(field, Set.of("integerValue"), Set.of("integerValue"));
		String value = Json.text(field.get("integerValue"), 19, false);
		if (!value.matches("[0-9]+")) throw unavailable();
		return Long.parseLong(value);
	}

	private static Counter current(Counter stored, String day, long minute) throws Exception {
		if (stored == null || stored.day.compareTo(day) > 0 || stored.minute > minute) throw unavailable();
		return new Counter(day, stored.day.equals(day) ? stored.daily : 0, minute,
			stored.minute == minute && stored.day.equals(day) ? stored.perMinute : 0, stored.updateTime);
	}

	private ObjectNode write(String name, Counter counter) {
		ObjectNode write = Json.object();
		ObjectNode document = write.putObject("update").put("name", name);
		ObjectNode fields = document.putObject("fields");
		fields.putObject("day").put("stringValue", counter.day);
		fields.putObject("dayCount").put("integerValue", Long.toString(Math.addExact(counter.daily, 1)));
		fields.putObject("minutePeriod").put("integerValue", Long.toString(counter.minute));
		fields.putObject("minuteCount").put("integerValue", Long.toString(Math.addExact(counter.perMinute, 1)));
		ObjectNode condition = write.putObject("currentDocument");
		if (counter.updateTime == null) condition.put("exists", false);
		else condition.put("updateTime", counter.updateTime);
		return write;
	}

	private String hash(String uid) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(salt.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		digest.update(scope.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		return HexFormat.of().formatHex(digest.digest(uid.getBytes(StandardCharsets.UTF_8)));
	}

	private static int limit(Map<String, String> env, String key, int fallback) {
		try {
			int value = Integer.parseInt(env.getOrDefault(key, Integer.toString(fallback)));
			if (value < 1 || value > 1_000_000) throw new NumberFormatException();
			return value;
		} catch (RuntimeException failure) { throw new IllegalArgumentException("Configuración de cuota inválida"); }
	}
	private static String required(Map<String, String> env, String key) {
		String value = env.get(key);
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta configuración del servicio");
		return value;
	}
	private static IOException unavailable() { return new IOException("Servicio de cuotas no disponible"); }
}
