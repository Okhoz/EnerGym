package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato de tono conversacional sin red ni credenciales reales. */
final class GeminiTrainerPromptTest {
	@Test void coachProvidesUsefulAdviceWithoutRepeatedPlanOffersOrMandatoryQuestions() {
		GeminiGateway gateway = new GeminiGateway(Map.of(), URI.create("https://example.invalid"),
			Duration.ofSeconds(1), Duration.ofSeconds(1));
		String instructions = gateway.payload(Json.object()).path("system_instruction").asText();
		assertTrue(instructions.contains("consejos concretos"));
		assertTrue(instructions.contains("No repitas saludos ni ofertas de preparar un plan"));
		assertTrue(instructions.contains("No cierres siempre con una pregunta"));
		assertTrue(instructions.contains("sin una ficha ni un plan"));
	}

	@Test void spanishCoachRespondsToConversationWithoutForcingRoutineQuestions() {
		GeminiGateway gateway = new GeminiGateway(Map.of(), URI.create("https://example.invalid"),
			Duration.ofSeconds(1), Duration.ofSeconds(1));
		String instructions = gateway.payload(Json.object()).path("system_instruction").asText();
		assertTrue(instructions.contains("entrenador personal"));
		assertTrue(instructions.contains("saludos"));
		assertTrue(instructions.contains("missingFields=[]"));
		assertTrue(instructions.contains("solo cuando"));
	}

	@Test void twoTurnsRemainDataInStrictStatelessPayload() throws Exception {
		ObjectNode request = (ObjectNode) ChatServiceTest.request();
		request.putNull("exerciseId");
		request.put("message", "¿Cómo puedo mantener ese hábito?");
		request.put("historyConsent", true);
		request.set("profile", Json.object());
		request.putArray("history").addObject().put("role", "user").put("text", "Me cuesta ser constante");
		request.withArray("history").addObject().put("role", "assistant").put("text", "Empieza con una meta pequeña.");
		ProposalValidator validator = new ProposalValidator();
		validator.validateRequest(request);
		GeminiGateway gateway = new GeminiGateway(Map.of(), URI.create("https://example.invalid"),
			Duration.ofSeconds(1), Duration.ofSeconds(1));
		JsonNode payload = gateway.payload(validator.providerRequest(request));
		JsonNode input = Json.MAPPER.readTree(payload.path("input").asText());
		assertEquals(2, input.path("history").size());
		assertTrue(input.path("eligibleExercises").isEmpty());
		assertTrue(input.path("exerciseContext").isNull());
		assertFalse(payload.path("store").asBoolean(true));
		assertEquals("application/json", payload.path("response_format").path("mime_type").asText());
		assertTrue(payload.path("response_format").path("schema").has("required"));
		assertFalse(payload.has("previous_interaction_id"));
		request.put("historyConsent", false);
		assertTrue(validator.providerRequest(request).path("history").isEmpty());
	}
}
