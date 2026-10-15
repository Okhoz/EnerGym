package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures sintéticas: se sustituye exclusivamente la frontera HTTP externa. */
class GroqGatewayTest {
	static ObjectNode envelope(JsonNode answer) {
		ObjectNode out = Json.object().put("id", "fixture-id").put("object", "chat.completion")
			.put("model", "openai/gpt-oss-120b").put("created", 1792000000);
		ObjectNode choice = out.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop");
		choice.putNull("logprobs");
		choice.putObject("message").put("role", "assistant").put("content", answer.toString()).putNull("refusal");
		out.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 50).put("total_tokens", 150);
		out.putObject("x_groq").put("id", "fixture-trace");
		return out;
	}
	static GroqGateway gateway(URI uri, Duration timeout, Duration deadline) {
		return new GroqGateway(GroqProviderFactoryTest.ENV, uri, timeout, deadline);
	}
	static ObjectNode message(ObjectNode out) { return (ObjectNode) out.path("choices").get(0).path("message"); }
	static ObjectNode item(ObjectNode answer) { return (ObjectNode) answer.path("proposal").path("items").get(0); }

	@Test
	void expandedCatalogStaysCompactAndWithinFreePlanGuardForBroadestProfile() throws Exception {
		GroqGateway provider = (GroqGateway) ProviderFactory.fromEnvironment(GroqProviderFactoryTest.ENV);
		ObjectNode req = (ObjectNode) ChatServiceTest.request();
		req.put("exerciseId", "barbell-back-squat");
		req.put("message", "x".repeat(600));
		ObjectNode p = (ObjectNode) req.get("profile");
		p.put("goal", "GENERAL_FITNESS").put("environment", "GYM").put("category", "GYM");
		p.putArray("resources").add("DUMBBELLS").add("BARBELL").add("BANDS").add("MAT").add("BENCH").add("MACHINE")
			.add("CHAIR").add("WALL").add("SUPPORT").add("BICYCLE").add("POOL");
		new ProposalValidator().validateRequest(req);
		JsonNode request = new ProposalValidator().providerRequest(req);
		JsonNode eligible = request.path("eligibleExercises");
		assertEquals(ProposalValidator.MAX_ELIGIBLE, eligible.size());
		Set<String> groups = new HashSet<>();
		for (JsonNode e : eligible) {
			assertEquals(Set.of("id", "metricType", "requiredResources"), Set.copyOf(iterable(e.fieldNames())));
			groups.add(e.path("requiredResources").toString());
		}
		assertTrue(groups.contains("[\"MACHINE\"]"));
		assertTrue(groups.contains("[\"BANDS\"]"));
		assertFalse(request.path("exerciseContext").path("description").asText().isEmpty());
		assertTrue(provider.payload(request).toString().getBytes(StandardCharsets.UTF_8).length < 18000);
	}

	private static List<String> iterable(Iterator<String> names) {
		List<String> list = new ArrayList<>();
		names.forEachRemaining(list::add);
		return list;
	}

	@Test
	void payloadKeepsStrictSchemaConsentAndMetricContractWithoutUnsupportedStore() throws Exception {
		GroqGateway provider = (GroqGateway) ProviderFactory.fromEnvironment(GroqProviderFactoryTest.ENV);
		JsonNode request = new ProposalValidator().providerRequest(ChatServiceTest.request());
		JsonNode body = provider.payload(request);
		assertEquals("openai/gpt-oss-120b", body.path("model").asText());
		assertFalse(body.has("store"));
		assertFalse(body.has("tools"));
		assertFalse(body.has("reasoning_format"));
		assertFalse(body.path("stream").asBoolean(true));
		assertFalse(body.path("include_reasoning").asBoolean(true));
		assertEquals("low", body.path("reasoning_effort").asText());
		assertEquals(2000, body.path("max_completion_tokens").asInt());
		assertEquals("json_schema", body.path("response_format").path("type").asText());
		JsonNode format = body.path("response_format").path("json_schema");
		assertTrue(format.path("strict").asBoolean());
		assertEquals("energym_proposal", format.path("name").asText());
		assertEquals(Json.resource("proposal-schema.json"), format.path("schema"));
		assertEquals(2, body.path("messages").size());
		assertEquals("system", body.path("messages").get(0).path("role").asText());
		assertEquals("user", body.path("messages").get(1).path("role").asText());
		assertEquals(request, Json.parse(body.path("messages").get(1).path("content").asText().getBytes(StandardCharsets.UTF_8)));
		assertFalse(body.toString().contains("fixture-key"));
	}

	@Test
	void parserRejectsRefusalToolsTruncationAmbiguityMarkdownDuplicatesAndNullContent() throws Exception {
		assertEquals(ChatServiceTest.answer(), GroqGateway.parseResponse(envelope(ChatServiceTest.answer())));
		for (String finish : List.of("length", "tool_calls", "content_filter", "function_call")) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			((ObjectNode) out.path("choices").get(0)).put("finish_reason", finish);
			assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(out));
		}
		for (String field : List.of("refusal", "tool_calls", "function_call", "reasoning")) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			message(out).put(field, "untrusted");
			assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(out), field);
		}
		for (String content : List.of("```json\n{}\n```", "{bad", "{} {}", "{\"text\":1,\"text\":2}", "null", "[]")) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			message(out).put("content", content);
			assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(out));
		}
		ObjectNode out = envelope(ChatServiceTest.answer());
		message(out).putNull("content");
		assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(out));
		ObjectNode ambiguous = envelope(ChatServiceTest.answer());
		ambiguous.withArray("choices").add(ambiguous.path("choices").get(0).deepCopy());
		assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(ambiguous));
		ObjectNode error = envelope(ChatServiceTest.answer());
		error.putObject("error").put("message", "secret");
		assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(error));
		ObjectNode wrongRole = envelope(ChatServiceTest.answer());
		message(wrongRole).put("role", "tool");
		assertThrows(IllegalArgumentException.class, () -> GroqGateway.parseResponse(wrongRole));
	}

	@Test
	void localHttpKeepsCorrelationConsentExerciseContextAndDomainValidation() throws Exception {
		AtomicReference<JsonNode> sent = new AtomicReference<>();
		AtomicReference<String> auth = new AtomicReference<>();
		AtomicReference<String> query = new AtomicReference<>();
		AtomicReference<ObjectNode> answer = new AtomicReference<>((ObjectNode) ChatServiceTest.answer());
		try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> {
			sent.set(Json.parse(exchange.getRequestBody().readAllBytes()));
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			query.set(exchange.getRequestURI().getRawQuery());
			return new GeminiGatewayTest.Reply(200, envelope(answer.get()).toString());
		})) {
			GroqGateway provider = gateway(fixture.uri(), Duration.ofSeconds(2), Duration.ofSeconds(3));
			ObjectNode req = (ObjectNode) ChatServiceTest.request();
			req.withArray("history").addObject().put("role", "user").put("text", "consented-synthetic-history");
			ChatService.Result result = GeminiGatewayTest.chat(provider, req);
			assertEquals(200, result.status());
			assertEquals("request-1", result.body().path("requestId").asText());
			assertEquals("Bearer fixture-key", auth.get());
			assertNull(query.get());
			JsonNode input = Json.parse(sent.get().path("messages").get(1).path("content").asText().getBytes(StandardCharsets.UTF_8));
			assertTrue(input.path("history").isEmpty());
			assertEquals("bodyweight-squat", input.path("exerciseContext").path("id").asText());
			req.put("historyConsent", true);
			assertEquals(200, GeminiGatewayTest.chat(provider, req).status());
			input = Json.parse(sent.get().path("messages").get(1).path("content").asText().getBytes(StandardCharsets.UTF_8));
			assertEquals("consented-synthetic-history", input.path("history").get(0).path("text").asText());
			ObjectNode valid = (ObjectNode) ChatServiceTest.answer();
			valid.set("proposal", Json.MAPPER.readTree("""
				{"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
				"prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"60"}],"justification":"Compatible"}]}
				"""));
			answer.set(valid);
			result = GeminiGatewayTest.chat(provider, req);
			assertEquals(200, result.status());
			assertFalse(result.body().path("proposalNotice").asText().isEmpty());
			assertEquals(new ProposalValidator().catalogVersion(), result.body().path("catalogVersion").asInt());
			for (String invalid : List.of("load", "incomplete", "too-many", "duration", "unknown-id")) {
				ObjectNode bad = valid.deepCopy();
				switch (invalid) {
					case "load" -> ((ObjectNode) item(bad).path("prescription").get(2)).put("value", "10");
					case "incomplete" -> item(bad).withArray("prescription").remove(2);
					case "too-many" -> { item(bad).withArray("prescription").add(Json.object().put("key", "sets").put("value", "2")); item(bad).withArray("prescription").add(Json.object().put("key", "sets").put("value", "2")); }
					case "duration" -> item(bad).put("estimatedMinutes", 40);
					case "unknown-id" -> item(bad).put("exerciseId", "invented-id");
				}
				answer.set(bad);
				assertEquals("INVALID_PROVIDER_RESPONSE", GeminiGatewayTest.chat(provider, req).body().path("error").asText(), invalid);
			}
		}
	}

	@Test
	void authQuotaAndServerErrorsAreSafeCompatibleAndNeverRetried() throws Exception {
		for (int status : List.of(401, 403, 429, 400, 500, 503, 302)) {
			try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> {
				exchange.getResponseHeaders().set("Location", "https://api.groq.com/forbidden");
				return new GeminiGatewayTest.Reply(status, "fixture-key secret-context");
			})) {
				ChatService.Result result = GeminiGatewayTest.chat(gateway(fixture.uri(), Duration.ofSeconds(2), Duration.ofSeconds(3)), ChatServiceTest.request());
				assertEquals(status == 429 ? 429 : Set.of(401,403,503).contains(status) ? 503 : 502, result.status());
				assertEquals(status == 429 ? "RATE_LIMITED" : Set.of(401,403).contains(status) ? "PROVIDER_NOT_CONFIGURED" : "PROVIDER_UNAVAILABLE", result.body().path("error").asText());
				assertFalse(result.body().toString().contains("fixture-key"));
				assertFalse(result.body().toString().contains("secret-context"));
				assertEquals(1, fixture.calls.get());
			}
		}
	}

	@Test
	void errorStatusSurvivesOversizedOrSlowBodyWithoutWaitingOrRetrying() throws Exception {
		for (int status : List.of(401, 403, 429)) {
			for (boolean slow : List.of(false, true)) {
				try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> {
					if (slow) {
						exchange.sendResponseHeaders(status, 65537);
						exchange.getResponseBody().write('x');
						exchange.getResponseBody().flush();
						Thread.sleep(700);
						return null;
					}
					return new GeminiGatewayTest.Reply(status, "fixture-key secret-context" + "x".repeat(65537));
				})) {
					ChatService.Result result = GeminiGatewayTest.chat(
						gateway(fixture.uri(), Duration.ofMillis(150), Duration.ofMillis(350)),
						ChatServiceTest.request());
					assertEquals(status == 429 ? 429 : 503, result.status(), "status=" + status + ", slow=" + slow);
					assertEquals(status == 429 ? "RATE_LIMITED" : "PROVIDER_NOT_CONFIGURED",
						result.body().path("error").asText());
					assertEquals("request-1", result.body().path("requestId").asText());
					assertFalse(result.body().toString().contains("fixture-key"));
					assertFalse(result.body().toString().contains("secret-context"));
					assertEquals(1, fixture.calls.get());
				}
			}
		}
	}

	@Test
	void excessiveUtf8InputRejectedBeforeNetworkWithoutDroppingContext() throws Exception {
		try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> new GeminiGatewayTest.Reply(200, envelope(ChatServiceTest.answer()).toString()))) {
			ObjectNode req = (ObjectNode) ChatServiceTest.request();
			req.put("historyConsent", true);
			for (int i = 0; i < 6; i++)
				req.withArray("history").addObject().put("role", "user").put("text", "界".repeat(1000));
			GroqGateway provider = gateway(fixture.uri(), Duration.ofSeconds(2), Duration.ofSeconds(3));
			String serialized = provider.payload(new ProposalValidator().providerRequest(req)).toString();
			assertTrue(serialized.length() < 18000, "La fixture cabe por caracteres pero excede el límite UTF-8");
			assertTrue(serialized.getBytes(StandardCharsets.UTF_8).length > 18000);
			ChatService.Result result = GeminiGatewayTest.chat(provider, req);
			assertEquals(429, result.status());
			assertEquals("RATE_LIMITED", result.body().path("error").asText());
			assertEquals("request-1", result.body().path("requestId").asText());
			assertEquals(0, fixture.calls.get());
		}
	}

	@Test
	void malformedAndOversizedDownloadsAreInvalid() throws Exception {
		for (String body : List.of("x".repeat(65537), "{bad", "{\"choices\":null}")) {
			try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> new GeminiGatewayTest.Reply(200, body))) {
				assertEquals("INVALID_PROVIDER_RESPONSE", GeminiGatewayTest.chat(gateway(fixture.uri(), Duration.ofSeconds(2), Duration.ofSeconds(3)), ChatServiceTest.request()).body().path("error").asText());
				assertEquals(1, fixture.calls.get());
			}
		}
	}

	@Test
	void headerTimeoutAndPartialBodyDeadlineCancelWithNoRetry() throws Exception {
		for (boolean headers : List.of(false, true)) {
			try (GeminiGatewayTest.Fixture fixture = new GeminiGatewayTest.Fixture(exchange -> {
				byte[] bytes = envelope(ChatServiceTest.answer()).toString().getBytes(StandardCharsets.UTF_8);
				if (headers) { exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes,0,1); exchange.getResponseBody().flush(); }
				Thread.sleep(700);
				if (headers) { exchange.getResponseBody().write(bytes,1,bytes.length-1); return null; }
				return new GeminiGatewayTest.Reply(200,new String(bytes,StandardCharsets.UTF_8));
			})) {
				ChatService.Result result = GeminiGatewayTest.chat(gateway(fixture.uri(), Duration.ofMillis(150), Duration.ofMillis(350)), ChatServiceTest.request());
				assertEquals(504, result.status());
				assertEquals("PROVIDER_TIMEOUT", result.body().path("error").asText());
				assertEquals(1,fixture.calls.get());
			}
		}
	}

	@Test
	void inheritedProviderFailureMappingsRemainCompatible() {
		assertEquals("RATE_LIMITED", new ProviderFailure(429).code());
		assertEquals(503,new ProviderFailure(503).status());
		assertEquals("PROVIDER_UNAVAILABLE",new ProviderFailure(401).code());
		assertEquals(502,new ProviderFailure(401).status());
	}
}
