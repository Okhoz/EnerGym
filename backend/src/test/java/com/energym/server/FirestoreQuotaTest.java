package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FirestoreQuotaTest {
	private static final String SALT = "fixed-test-salt-at-least-32-characters";
	private static final FirestoreQuota.Limits LIMITS = new FirestoreQuota.Limits(2, 3, 2, 3);

	@Test void reservationsRemainDurableAcrossInstancesAndEnforceGlobalAndUserLimitsAtomically() throws Exception {
		Fixture store = new Fixture();
		ServiceAccountAccessTokenTest.MutableClock clock = clock();
		assertTrue(quota(store, clock, LIMITS).reserve("private-user-a@example.com"));
		assertTrue(quota(store, clock, LIMITS).reserve("private-user-a@example.com"));
		assertFalse(quota(store, clock, LIMITS).reserve("private-user-a@example.com"));
		assertEquals(2, store.commits);
		assertTrue(quota(store, clock, LIMITS).reserve("user-b"));
		assertFalse(quota(store, clock, LIMITS).reserve("user-c"));
		assertEquals(3, store.documents.size(), "Rejected user must not get a counter document");
		assertEquals(3, store.global().path("fields").path("dayCount").path("integerValue").asInt());
		assertFalse(store.requestBodies.toString().contains("private-user-a@example.com"));
		assertTrue(store.documents.keySet().stream().allMatch(name -> name.matches("projects/test-project/databases/\\(default\\)/documents/assistant_quotas/energym_(global|u_[0-9a-f]{64})")));
	}

	@Test void minuteWindowResetsWithoutErasingTheDailyBudgetAndUtcDayResetsBoth() throws Exception {
		Fixture store = new Fixture();
		ServiceAccountAccessTokenTest.MutableClock clock = clock();
		FirestoreQuota quota = quota(store, clock, new FirestoreQuota.Limits(2, 20, 1, 10));
		assertTrue(quota.reserve("user-a"));
		assertFalse(quota.reserve("user-a"));
		clock.advance(Duration.ofMinutes(1));
		assertTrue(quota.reserve("user-a"));
		clock.advance(Duration.ofMinutes(1));
		assertFalse(quota.reserve("user-a"));
		clock.advance(Duration.ofDays(1));
		assertTrue(quota.reserve("user-a"));
		assertEquals("2026-10-16", store.global().path("fields").path("day").path("stringValue").asText());
		assertEquals("1", store.global().path("fields").path("dayCount").path("integerValue").asText());
	}

	@Test void abortedCommitsRetryFreshTransactionsAtMostThreeTimes() throws Exception {
		Fixture store = new Fixture();
		store.abortCommits = 3;
		assertTrue(quota(store, clock(), LIMITS).reserve("user-a"));
		assertEquals(4, store.begins);
		assertEquals(1, store.commits);
		Fixture alwaysAborted = new Fixture();
		alwaysAborted.abortCommits = 20;
		assertThrows(Exception.class, () -> quota(alwaysAborted, clock(), LIMITS).reserve("user-a"));
		assertEquals(4, alwaysAborted.begins);
		assertTrue(alwaysAborted.documents.isEmpty());
	}

	@Test void globalMinuteBudgetIsSharedAcrossUsersAndRecoversWithoutResettingDailyTotal() throws Exception {
		Fixture store = new Fixture();
		ServiceAccountAccessTokenTest.MutableClock clock = clock();
		FirestoreQuota quota = quota(store, clock, new FirestoreQuota.Limits(20, 20, 10, 1));
		assertTrue(quota.reserve("user-a"));
		assertFalse(quota.reserve("user-b"));
		clock.advance(Duration.ofMinutes(1));
		assertTrue(quota.reserve("user-b"));
		assertEquals("2", store.global().path("fields").path("dayCount").path("integerValue").asText());
	}

	@Test void dailyPeriodUsesUtcEvenWhenTheClockHasAnotherZone() throws Exception {
		Fixture store = new Fixture();
		Clock clock = Clock.fixed(Instant.parse("2026-10-14T23:15:00Z"), ZoneId.of("Europe/Madrid"));
		assertTrue(quota(store, clock, LIMITS).reserve("user-a"));
		assertEquals("2026-10-14", store.global().path("fields").path("day").path("stringValue").asText());
	}

	@Test void uncertainCommitDoesNotRetryOrAllowProviderCall() throws Exception {
		Fixture store = new Fixture();
		store.uncertainCommit = true;
		Exception failure = assertThrows(Exception.class, () -> quota(store, clock(), LIMITS).reserve("private-user"));
		assertFalse(failure.getMessage().contains("private-user"));
		assertEquals(1, store.begins);
		assertEquals(1, store.commits, "The server may have applied the reservation despite a lost reply");
	}

	@Test void missingDuplicateMalformedOrUnauthorizedReadsFailClosed() throws Exception {
		for (String mode : List.of("missing-response", "duplicate", "bad-count", "unauthorized", "oversized", "duplicate-json")) {
			Fixture store = new Fixture();
			assertTrue(quota(store, clock(), LIMITS).reserve("user-a"));
			store.readFailure = mode;
			assertThrows(Exception.class, () -> quota(store, clock(), LIMITS).reserve("user-a"), mode);
			assertEquals(1, store.commits, mode);
		}
	}

	@Test void timeGoingBackwardsAndInvalidConfigurationNeverResetStoredBudgets() throws Exception {
		Fixture store = new Fixture();
		ServiceAccountAccessTokenTest.MutableClock clock = clock();
		FirestoreQuota quota = quota(store, clock, LIMITS);
		assertTrue(quota.reserve("user-a"));
		clock.advance(Duration.ofDays(-1));
		assertThrows(Exception.class, () -> quota.reserve("user-a"));
		assertThrows(Exception.class, () -> new FirestoreQuota("test-project", "energym", "too-short", LIMITS, () -> "token", store, clock));
		assertThrows(Exception.class, () -> quota.reserve(""));
	}
	@Test void elapsedBudgetStopsBeforeStartingAnotherRemoteOperation() throws Exception {
		Fixture store = new Fixture();
		java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
		ServiceAccountAccessToken.Transport slow = (uri, bearer, type, bytes) -> {
			var response = store.post(uri, bearer, type, bytes);
			nanos.addAndGet(Duration.ofSeconds(30).toNanos());
			return response;
		};
		FirestoreQuota quota = new FirestoreQuota("test-project", "energym", SALT, LIMITS,
			() -> "token", slow, clock(), nanos::get);
		assertThrows(Exception.class, () -> quota.reserve("user-a"));
		assertEquals(1, store.begins);
		assertEquals(1, store.requestBodies.size(), "No read, commit or rollback starts after the deadline");
		assertEquals(0, store.commits);
	}
	@Test void elapsedOauthBudgetDoesNotBeginATransaction() throws Exception {
		Fixture store = new Fixture();
		java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
		FirestoreQuota quota = new FirestoreQuota("test-project", "energym", SALT, LIMITS,
			() -> { nanos.set(Duration.ofSeconds(30).toNanos()); return "token"; }, store, clock(), nanos::get);
		assertThrows(Exception.class, () -> quota.reserve("user-a"));
		assertEquals(0, store.begins);
	}

	private FirestoreQuota quota(Fixture store, Clock clock, FirestoreQuota.Limits limits) {
		return new FirestoreQuota("test-project", "energym", SALT, limits, () -> "token", store, clock);
	}
	private ServiceAccountAccessTokenTest.MutableClock clock() {
		return new ServiceAccountAccessTokenTest.MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
	}

	/** Firestore HTTP fixture keeps documents between quota instances and applies both writes atomically. */
	private static final class Fixture implements ServiceAccountAccessToken.Transport {
		final Map<String, JsonNode> documents = new HashMap<>();
		final List<String> requestBodies = new ArrayList<>();
		int begins, commits, abortCommits;
		boolean uncertainCommit;
		String readFailure;
		@Override public ServiceAccountAccessToken.Response post(URI uri, String bearer, String type, byte[] bytes) throws Exception {
			assertEquals("https", uri.getScheme());
			assertEquals("firestore.googleapis.com", uri.getHost());
			assertEquals("token", bearer);
			assertEquals("application/json", type);
			String body = new String(bytes, StandardCharsets.UTF_8);
			requestBodies.add(body);
			JsonNode request = Json.parse(bytes);
			if (uri.getPath().endsWith(":beginTransaction")) {
				assertTrue(request.path("options").has("readWrite"));
				begins++;
				return response(200, Map.of("transaction", "dHgt" + begins));
			}
			if (uri.getPath().endsWith(":rollback")) return response(200, Map.of());
			assertEquals("dHgt" + begins, request.path("transaction").asText());
			if (uri.getPath().endsWith(":batchGet")) {
				if ("unauthorized".equals(readFailure)) return response(403, Map.of("error", Map.of("status", "PERMISSION_DENIED")));
				if ("oversized".equals(readFailure)) return new ServiceAccountAccessToken.Response(200, new byte[65537]);
				if ("duplicate-json".equals(readFailure)) return new ServiceAccountAccessToken.Response(200, "[{\"missing\":\"first\",\"missing\":\"second\"}]".getBytes(StandardCharsets.UTF_8));
				ArrayNode rows = Json.MAPPER.createArrayNode();
				for (JsonNode name : request.path("documents")) {
					JsonNode doc = documents.get(name.asText());
					if (doc == null) rows.addObject().put("missing", name.asText()).put("readTime", "2026-10-15T10:00:00Z");
					else {
						ObjectNode found = doc.deepCopy();
						if ("bad-count".equals(readFailure)) ((ObjectNode) found.path("fields").path("dayCount")).put("integerValue", "invalid");
						rows.addObject().set("found", found);
					}
				}
				if ("missing-response".equals(readFailure)) rows.remove(0);
				if ("duplicate".equals(readFailure)) rows.set(1, rows.get(0));
				ArrayNode unordered = Json.MAPPER.createArrayNode();
				for (int index = rows.size() - 1; index >= 0; index--) unordered.add(rows.get(index));
				return response(200, unordered);
			}
			assertTrue(uri.getPath().endsWith(":commit"));
			if (abortCommits-- > 0) return response(409, Map.of("error", Map.of("status", "ABORTED", "code", 409)));
			assertEquals(2, request.path("writes").size());
			Map<String, JsonNode> next = new HashMap<>();
			String updateTime = "2026-10-15T10:00:00." + String.format("%06d", commits + 1) + "Z";
			for (JsonNode write : request.path("writes")) {
				ObjectNode doc = write.path("update").deepCopy();
				String name = doc.path("name").asText();
				JsonNode previous = documents.get(name);
				JsonNode condition = write.path("currentDocument");
				if (previous == null) assertEquals(false, condition.path("exists").asBoolean(true));
				else assertEquals(previous.path("updateTime").asText(), condition.path("updateTime").asText());
				doc.put("updateTime", updateTime);
				next.put(name, doc);
			}
			assertEquals(2, next.size());
			documents.putAll(next);
			commits++;
			if (uncertainCommit) throw new IOException("Reply lost");
			return response(200, Map.of("commitTime", updateTime, "writeResults", List.of(Map.of("updateTime", updateTime), Map.of("updateTime", updateTime))));
		}
		JsonNode global() { return documents.entrySet().stream().filter(e -> e.getKey().endsWith("_global")).findFirst().orElseThrow().getValue(); }
		private ServiceAccountAccessToken.Response response(int status, Object body) throws Exception {
			return new ServiceAccountAccessToken.Response(status, Json.MAPPER.writeValueAsBytes(body));
		}
	}
}
