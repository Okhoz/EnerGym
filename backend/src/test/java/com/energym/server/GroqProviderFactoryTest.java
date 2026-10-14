package com.energym.server;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroqProviderFactoryTest {
	static final Map<String, String> ENV = Map.of("ENERGYM_AI_PROVIDER", "groq", "GROQ_ENABLED", "true",
		"GROQ_API_KEY", "fixture-key", "GROQ_MODEL", "openai/gpt-oss-120b");

	@Test
	void explicitSelectionAndFreeModelAllowlistAreRequired() {
		assertTrue(ProviderFactory.fromEnvironment(ENV).configured());
		assertEquals("GroqGateway", ProviderFactory.fromEnvironment(ENV).getClass().getSimpleName());
		for (String model : List.of("", "openai/gpt-oss-20b", "paid-model", "openai/gpt-oss-120b-extra")) {
			Map<String, String> env = new HashMap<>(ENV);
			env.put("GROQ_MODEL", model);
			assertEquals(model.equals("openai/gpt-oss-20b"), ProviderFactory.fromEnvironment(env).configured());
		}
	}

	@Test
	void missingOptInKeyOrSelectionNeverFallsBack() {
		for (String field : ENV.keySet()) {
			Map<String, String> env = new HashMap<>(ENV);
			env.remove(field);
			env.putAll(Map.of("OPENAI_ENABLED", "true", "OPENAI_API_KEY", "fixture-paid-key", "OPENAI_MODEL", "paid-model"));
			assertFalse(ProviderFactory.fromEnvironment(env).configured(), field);
		}
		for (String flag : List.of("TRUE", "false", "1", "")) {
			Map<String, String> env = new HashMap<>(ENV);
			env.put("GROQ_ENABLED", flag);
			assertFalse(ProviderFactory.fromEnvironment(env).configured());
		}
		for (String key : List.of("", "bad\nkey", "bad key", "x".repeat(1025))) {
			Map<String, String> env = new HashMap<>(ENV);
			env.put("GROQ_API_KEY", key);
			assertFalse(ProviderFactory.fromEnvironment(env).configured());
		}
	}

	@Test
	void disabledProviderRejectsBeforeNetworkEvenWithAKey() {
		Provider provider = ProviderFactory.fromEnvironment(Map.of("ENERGYM_AI_PROVIDER", "groq", "GROQ_API_KEY", "fixture-key"));
		assertFalse(provider.configured());
		assertThrows(IllegalStateException.class, () -> provider.respond(ChatServiceTest.request()));
	}
}
