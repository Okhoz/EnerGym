package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La única frontera sustituida es HTTPS del proveedor: fixtures servidos en loopback, sin claves reales.
 */
class GeminiGatewayTest {
	static final Map<String, String> ENV = Map.of("ENERGYM_AI_PROVIDER", "gemini", "GEMINI_ENABLED", "true", "GEMINI_API_KEY", "fixture-key", "GEMINI_MODEL", "gemini-3.8-flash");

	static ObjectNode envelope(JsonNode answer) {
		ObjectNode out = Json.object().put("object", "interaction").put("id", "fixture-id").put("status", "completed");
		out.putArray("steps").addObject().put("type", "model_output").putArray("content").addObject().put("type", "text").put("text", answer.toString());
		return out;
	}

	static GeminiGateway gateway(URI endpoint, Duration timeout) {
		return new GeminiGateway(ENV, endpoint, timeout, timeout.plusMillis(300));
	}

	static ChatService.Result chat(Provider provider, JsonNode request) {
		SessionTokenStore tokens = new SessionTokenStore(ChatServiceTest.CODE, Clock.systemUTC(), Duration.ofHours(1), 10);
		return new ChatService(tokens, provider, new ProposalValidator()).chat(tokens.pair(ChatServiceTest.CODE).token(), request);
	}

	@Test
	void selectionAndExactOptInPreventAccidentalPaidFallback() throws Exception {
		Map<String, String> all = new HashMap<>(ENV);
		all.putAll(Map.of("OPENAI_ENABLED", "true", "OPENAI_API_KEY", "fixture-openai-key", "OPENAI_MODEL", "fixture-model"));
		for (String selection : List.of("", "none", "unknown", "Gemini")) {
			all.put("ENERGYM_AI_PROVIDER", selection);
			Provider provider = ProviderFactory.fromEnvironment(all);
			assertFalse(provider.configured());
			assertEquals("PROVIDER_NOT_CONFIGURED", chat(provider, ChatServiceTest.request()).body().path("error").asText());
		}
		all.remove("ENERGYM_AI_PROVIDER");
		assertFalse(ProviderFactory.fromEnvironment(all).configured());
		all.put("ENERGYM_AI_PROVIDER", "gemini");
		assertInstanceOf(GeminiGateway.class, ProviderFactory.fromEnvironment(all));
		assertTrue(ProviderFactory.fromEnvironment(all).configured());
		for (String optIn : List.of("false", "TRUE", "1", "")) {
			all.put("GEMINI_ENABLED", optIn);
			assertFalse(ProviderFactory.fromEnvironment(all).configured());
		}
		all.put("GEMINI_ENABLED", "true");
		all.put("GEMINI_API_KEY", "");
		assertFalse(ProviderFactory.fromEnvironment(all).configured());
		all.put("GEMINI_API_KEY", "fixture-key");
		all.put("GEMINI_MODEL", "");
		assertFalse(ProviderFactory.fromEnvironment(all).configured());
		all.put("GEMINI_MODEL", "bad model");
		assertFalse(ProviderFactory.fromEnvironment(all).configured());
		all.put("ENERGYM_AI_PROVIDER", "openai");
		assertInstanceOf(OpenAiGateway.class, ProviderFactory.fromEnvironment(all));
		assertTrue(ProviderFactory.fromEnvironment(all).configured());
	}

	@Test
	void structuredPayloadIsStatelessAndUsesTrustedCatalog() throws Exception {
		JsonNode request = new ProposalValidator().providerRequest(ChatServiceTest.request());
		JsonNode payload = assertInstanceOf(GeminiGateway.class, ProviderFactory.fromEnvironment(ENV)).payload(request);
		assertEquals("gemini-3.8-flash", payload.path("model").asText());
		assertFalse(payload.path("store").asBoolean(true));
		assertFalse(payload.path("stream").asBoolean(true));
		assertFalse(payload.path("background").asBoolean(true));
		assertFalse(payload.has("previous_interaction_id"));
		assertFalse(payload.has("tools"));
		assertFalse(payload.toString().contains("fixture-key"));
		assertEquals("application/json", payload.path("response_format").path("mime_type").asText());
		assertEquals("text", payload.path("response_format").path("type").asText());
		assertEquals(Json.resource("proposal-schema.json"), payload.path("response_format").path("schema"));
		assertEquals(2000, payload.path("generation_config").path("max_output_tokens").asInt());
		assertEquals("none", payload.path("generation_config").path("thinking_summaries").asText());
		assertEquals("low", payload.path("generation_config").path("thinking_level").asText());
		JsonNode input = Json.parse(payload.path("input").asText().getBytes(StandardCharsets.UTF_8));
		assertEquals("bodyweight-squat", input.path("exerciseContext").path("id").asText());
		assertFalse(input.path("eligibleExercises").isEmpty());
	}

	@Test
	void flashLiteKeepsItsThinkingDefaultsWithoutUnsupportedParameters() throws Exception {
		Map<String, String> env = new HashMap<>(ENV);
		env.put("GEMINI_MODEL", "gemini-3.5-flash-lite");
		GeminiGateway provider = assertInstanceOf(GeminiGateway.class, ProviderFactory.fromEnvironment(env));
		assertTrue(provider.configured());
		JsonNode payload = provider.payload(new ProposalValidator().providerRequest(ChatServiceTest.request()));
		assertEquals("gemini-3.5-flash-lite", payload.path("model").asText());
		assertFalse(payload.path("generation_config").has("thinking_level"));
		assertFalse(payload.has("tools"));
		assertFalse(payload.path("store").asBoolean(true));
	}

	@Test
	void onlyTheSelectedFreeTierModelsCanBeEnabled() throws Exception {
		for (String model : List.of("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-2.5-pro",
			"gemini-3.5-flash-lite-paid", "custom-model", "models/gemini-3.5-flash-lite", "")) {
			Map<String, String> env = new HashMap<>(ENV);
			env.put("GEMINI_MODEL", model);
			Provider provider = ProviderFactory.fromEnvironment(env);
			boolean freeModel = model.equals("gemini-3.5-flash-lite") || model.equals("gemini-3.8-flash");
			assertEquals(freeModel, provider.configured(), model);
			if (!freeModel)
				assertThrows(IllegalStateException.class, () -> provider.respond(ChatServiceTest.request()), model);
		}
	}

	@Test
	void completedWithNonemptyOrMalformedErrorsIsRejected() throws Exception {
		for (String errors : List.of("[{\"code\":\"BLOCKED\",\"message\":\"fixture fault\"}]", "[{}]", "[null]", "null", "{}", "\"fault\"", "1", "true")) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			out.set("errors", Json.parse(errors.getBytes(StandardCharsets.UTF_8)));
			assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(out), errors);
		}
	}

	@Test
	void completedWithEmptyErrorsRetainsValidTypedOutput() throws Exception {
		ObjectNode out = envelope(ChatServiceTest.answer());
		out.putArray("errors");
		assertEquals(ChatServiceTest.answer(), assertDoesNotThrow(() -> GeminiGateway.parseResponse(out)));
	}

	@Test
	void completedDocumentedThoughtIsIgnoredAndOneFinalTextIsRequired() throws Exception {
		ObjectNode out = envelope(ChatServiceTest.answer());
		out.withArray("steps").insert(0, Json.object().put("type", "thought").put("signature", "fixture-signature"));
		((ObjectNode) out.path("steps").get(0)).putArray("summary").addObject().put("type", "text").put("text", "Auxiliar");
		assertEquals(ChatServiceTest.answer(), assertDoesNotThrow(() -> GeminiGateway.parseResponse(out)));
		((ObjectNode) out.path("steps").get(0)).put("tool_call", "forbidden");
		assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(out));
	}

	@Test
	void refusesIncompleteToolsAmbiguousMalformedOrRefusalOutputs() throws Exception {
		for (String status : List.of("in_progress", "incomplete", "requires_action", "failed", "cancelled")) {
			ObjectNode out = envelope(ChatServiceTest.answer()).put("status", status);
			assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(out));
		}
		for (String type : List.of("function_call", "google_search_call", "refusal", "unknown")) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			out.withArray("steps").addObject().put("type", type);
			assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(out));
		}
		ObjectNode ambiguous = envelope(ChatServiceTest.answer());
		ambiguous.withArray("steps").add(ambiguous.path("steps").get(0).deepCopy());
		assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(ambiguous));
		for (JsonNode content : List.of(Json.object(), Json.MAPPER.createArrayNode(), Json.MAPPER.createArrayNode().add(Json.object().put("type", "refusal").put("text", "rechazo")))) {
			ObjectNode out = envelope(ChatServiceTest.answer());
			((ObjectNode) out.path("steps").get(0)).set("content", content);
			assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(out));
		}
		ObjectNode broken = envelope(ChatServiceTest.answer());
		((ObjectNode) broken.path("steps").get(0).path("content").get(0)).put("text", "{bad");
		assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(broken));
		ObjectNode error = envelope(ChatServiceTest.answer());
		error.putObject("error").put("code", "BLOCKED");
		assertThrows(IllegalArgumentException.class, () -> GeminiGateway.parseResponse(error));
	}

	@Test
	void realHttpAdapterUsesHeaderAndExistingProtocolConsentAndDomainChecks() throws Exception {
		AtomicReference<JsonNode> sent = new AtomicReference<>();
		AtomicReference<String> auth = new AtomicReference<>();
		AtomicReference<String> query = new AtomicReference<>();
		AtomicReference<JsonNode> answer = new AtomicReference<>(ChatServiceTest.answer());
		try (Fixture fixture = new Fixture(exchange -> {
			sent.set(Json.parse(exchange.getRequestBody().readAllBytes()));
			auth.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			query.set(exchange.getRequestURI().getRawQuery());
			return new Reply(200, envelope(answer.get()).toString());
		})) {
			GeminiGateway provider = gateway(fixture.uri(), Duration.ofSeconds(2));
			ObjectNode req = (ObjectNode) ChatServiceTest.request();
			req.withArray("history").addObject().put("role", "user").put("text", "private-history");
			ChatService.Result result = chat(provider, req);
			assertEquals(200, result.status());
			assertEquals("request-1", result.body().path("requestId").asText());
			assertEquals("fixture-key", auth.get());
			assertNull(query.get());
			assertEquals(1, fixture.calls.get());
			JsonNode input = Json.parse(sent.get().path("input").asText().getBytes(StandardCharsets.UTF_8));
			assertTrue(input.path("history").isEmpty());
			req.put("historyConsent", true);
			assertEquals(200, chat(provider, req).status());
			input = Json.parse(sent.get().path("input").asText().getBytes(StandardCharsets.UTF_8));
			assertEquals("private-history", input.path("history").get(0).path("text").asText());
			ObjectNode invented = (ObjectNode) ChatServiceTest.answer();
			invented.withArray("commands").addObject().put("type", "OPEN_EXERCISE").put("exerciseId", "invented-id");
			answer.set(invented);
			assertEquals("INVALID_PROVIDER_RESPONSE", chat(provider, req).body().path("error").asText());
		}
	}

	@Test
	void quotaAndErrorsRemainRecoverableWithNoRetriesOrSecretLeaks() throws Exception {
		for (int status : List.of(429, 401, 400, 500, 503)) {
			try (Fixture fixture = new Fixture(exchange -> new Reply(status, "{\"error\":\"fixture-key secret-context\"}"))) {
				ChatService.Result result = chat(gateway(fixture.uri(), Duration.ofSeconds(2)), ChatServiceTest.request());
				assertEquals(status == 429 ? 429 : status == 503 ? 503 : 502, result.status());
				assertEquals(status == 429 ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE", result.body().path("error").asText());
				assertTrue(result.body().path("recoverable").asBoolean());
				assertEquals("request-1", result.body().path("requestId").asText());
				assertFalse(result.body().toString().contains("fixture-key"));
				assertFalse(result.body().toString().contains("secret-context"));
				assertEquals(1, fixture.calls.get());
			}
		}
	}

	@Test
	void downloadLimitAndMalformedEnvelopeNeverBecomeAnAnswer() throws Exception {
		for (String body : List.of("x".repeat(65537), "{bad", "{\"status\":\"completed\",\"steps\":{}}")) {
			try (Fixture fixture = new Fixture(exchange -> new Reply(200, body))) {
				ChatService.Result result = chat(gateway(fixture.uri(), Duration.ofSeconds(2)), ChatServiceTest.request());
				assertEquals(502, result.status());
				assertEquals("INVALID_PROVIDER_RESPONSE", result.body().path("error").asText());
				assertEquals(1, fixture.calls.get());
			}
		}
	}

	@Test
	void requestTimeoutCancelsInsteadOfRetrying() throws Exception {
		try (Fixture fixture = new Fixture(exchange -> {
			Thread.sleep(500);
			return new Reply(200, envelope(ChatServiceTest.answer()).toString());
		})) {
			ChatService.Result result = chat(gateway(fixture.uri(), Duration.ofMillis(100)), ChatServiceTest.request());
			assertEquals(504, result.status());
			assertEquals("PROVIDER_TIMEOUT", result.body().path("error").asText());
			assertEquals(1, fixture.calls.get());
		}
	}

	@Test
	void totalDeadlineAlsoBoundsABodyAfterHeadersArrive() throws Exception {
		try (Fixture fixture = new Fixture(exchange -> {
			byte[] body = envelope(ChatServiceTest.answer()).toString().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body, 0, 1);
			exchange.getResponseBody().flush();
			Thread.sleep(700);
			exchange.getResponseBody().write(body, 1, body.length - 1);
			return null;
		})) {
			ChatService.Result result = chat(gateway(fixture.uri(), Duration.ofMillis(100)), ChatServiceTest.request());
			assertEquals(504, result.status());
			assertEquals("PROVIDER_TIMEOUT", result.body().path("error").asText());
			assertEquals(1, fixture.calls.get());
		}
	}

	@Test
	void typedProposalSurvivesOnlyWhileTrustedDomainRulesPass() throws Exception {
		ObjectNode answer = (ObjectNode) ChatServiceTest.answer();
		answer.set("proposal", Json.MAPPER.readTree("""
			{"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
			"prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"60"}],"justification":"Compatible"}]}
			"""));
		try (Fixture fixture = new Fixture(exchange -> new Reply(200, envelope(answer).toString()))) {
			GeminiGateway provider = gateway(fixture.uri(), Duration.ofSeconds(2));
			ChatService.Result result = chat(provider, ChatServiceTest.request());
			assertEquals(200, result.status());
			assertEquals("bodyweight-squat", result.body().path("proposal").path("items").get(0).path("exerciseId").asText());
			assertEquals(new ProposalValidator().catalogVersion(), result.body().path("catalogVersion").asInt());
			((ObjectNode) answer.path("proposal").path("items").get(0).path("prescription").get(2)).put("value", "10");
			assertEquals("INVALID_PROVIDER_RESPONSE", chat(provider, ChatServiceTest.request()).body().path("error").asText());
		}
	}

	record Reply(int status, String body) {
	}

	@FunctionalInterface
	interface Handler {
		Reply handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
	}

	static final class Fixture implements AutoCloseable {
		final HttpServer server;
		final AtomicInteger calls = new AtomicInteger();

		Fixture(Handler handler) throws Exception {
			server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/interactions", exchange -> {
				calls.incrementAndGet();
				try {
					Reply reply = handler.handle(exchange);
					if (reply != null) {
						byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
						exchange.sendResponseHeaders(reply.status(), body.length);
						exchange.getResponseBody().write(body);
					}
				} catch (Exception ignored) {
				} finally {
					exchange.close();
				}
			});
			server.start();
		}

		URI uri() {
			return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/interactions");
		}

		public void close() {
			server.stop(0);
		}
	}
}
