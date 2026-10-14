package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato de fuerza: el esquema orienta y el dominio sigue exigiendo todos los valores explícitos. */
class PrescriptionContractTest {
	private ObjectNode complete()throws Exception{
		ObjectNode out=(ObjectNode)ChatServiceTest.answer();
		out.set("proposal",Json.MAPPER.readTree("""
			{"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
			"prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"60"}],"justification":"Compatible"}]}
			"""));return out;
	}
	@Test void strengthNeedsEveryExplicitPrescriptionIncludingZeroLoad()throws Exception{
		ProposalValidator validator=new ProposalValidator();ObjectNode valid=complete();
		assertDoesNotThrow(()->validator.validateResponse(valid,ChatServiceTest.request()));
		for(int removed=0;removed<4;removed++){
			ObjectNode incomplete=valid.deepCopy();
			((com.fasterxml.jackson.databind.node.ArrayNode)incomplete.path("proposal").path("items").get(0).path("prescription")).remove(removed);
			IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->validator.validateResponse(incomplete,ChatServiceTest.request()));
			assertEquals("Prescripción incompleta",failure.getMessage());
		}
	}
	@Test void emittedSchemasExcludeEmptyAndSingletonPrescriptionsButPermitCompleteStrength()throws Exception{
		JsonNode input=new ProposalValidator().providerRequest(ChatServiceTest.request());
		GeminiGateway gemini=(GeminiGateway)ProviderFactory.fromEnvironment(GeminiGatewayTest.ENV);
		OpenAiGateway openai=(OpenAiGateway)OpenAiGateway.fromEnvironment(java.util.Map.of("OPENAI_ENABLED","true","OPENAI_API_KEY","fixture","OPENAI_MODEL","fixture"));
		for(JsonNode schema:List.of(gemini.payload(input).path("response_format").path("schema"),openai.payload(input).path("text").path("format").path("schema"))){
			JsonNode prescription=schema.path("properties").path("proposal").path("anyOf").get(0).path("properties").path("items").path("items").path("properties").path("prescription");
			assertTrue(prescription.path("minItems").asInt()>1,"El esquema actual permite prescripción vacía o singleton");
			assertEquals(5,prescription.path("maxItems").asInt());
			int count=complete().path("proposal").path("items").get(0).path("prescription").size();
			assertTrue(count>=prescription.path("minItems").asInt()&&count<=prescription.path("maxItems").asInt());
		}
	}
}
