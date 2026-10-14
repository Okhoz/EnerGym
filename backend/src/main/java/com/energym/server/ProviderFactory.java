package com.energym.server;

import java.util.Map;

/**
 * La selección de proveedor es explícita; nunca utiliza otro proveedor como fallback.
 */
public final class ProviderFactory {
	private ProviderFactory() {
	}

	public static Provider fromEnvironment(Map<String, String> env) {
		return switch (env.getOrDefault("ENERGYM_AI_PROVIDER", "none")) {
			case "gemini" -> GeminiGateway.fromEnvironment(env);
			case "openai" -> OpenAiGateway.fromEnvironment(env);
			default -> disabled();
		};
	}

	private static Provider disabled() {
		return new Provider() {
			public boolean configured() {
				return false;
			}

			public com.fasterxml.jackson.databind.JsonNode respond(com.fasterxml.jackson.databind.JsonNode request) {
				throw new IllegalStateException("Proveedor no configurado");
			}
		};
	}
}
