package com.energym.server;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.util.*;

/**
 * JSON con duplicados, profundidad y campos desconocidos rechazados antes del dominio.
 */
final class Json {
	static final ObjectMapper MAPPER = new ObjectMapper(
		JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(
				StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(8192).maxNumberLength(20).build())
			.build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	static JsonNode parse(byte[] bytes) throws IOException {
		return MAPPER.readTree(bytes);
	}

	static JsonNode resource(String name) {
		try (InputStream in = Json.class.getResourceAsStream("/" + name)) {
			if (in == null)
				throw new IllegalStateException("Recurso ausente");
			return MAPPER.readTree(in);
		} catch (IOException e) {
			throw new IllegalStateException("Recurso inválido", e);
		}
	}

	static ObjectNode object() {
		return MAPPER.createObjectNode();
	}

	static void keys(JsonNode n, Set<String> allowed, Set<String> required) {
		if (n == null || !n.isObject())
			throw new IllegalArgumentException("Objeto requerido");
		n.fieldNames().forEachRemaining(k -> {
			if (!allowed.contains(k))
				throw new IllegalArgumentException("Campo desconocido");
		});
		for (String k : required)
			if (!n.has(k))
				throw new IllegalArgumentException("Campo obligatorio");
	}

	static String text(JsonNode n, int max, boolean blank) {
		if (n == null || !n.isTextual() || n.textValue().length() > max || (!blank && n.textValue().isBlank()))
			throw new IllegalArgumentException("Texto inválido");
		return n.textValue();
	}

	static void array(JsonNode n, int max) {
		if (n == null || !n.isArray() || n.size() > max)
			throw new IllegalArgumentException("Lista inválida");
	}

	static int integer(JsonNode n, int min, int max) {
		if (n == null || !n.isIntegralNumber() || !n.canConvertToInt() || n.intValue() < min || n.intValue() > max)
			throw new IllegalArgumentException("Entero inválido");
		return n.intValue();
	}

	static void choice(JsonNode n, String... allowed) {
		if (!Set.of(allowed).contains(text(n, 64, false)))
			throw new IllegalArgumentException("Valor desconocido");
	}

	static Set<String> strings(JsonNode n) {
		Set<String> s = new HashSet<>();
		for (JsonNode x : n)
			s.add(x.asText());
		return s;
	}
}
