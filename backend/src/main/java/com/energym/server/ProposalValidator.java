package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Modelo: catálogo versionado propio y reglas compatibles con el dominio Android; no confía en IDs del cliente/proveedor.
 */
public final class ProposalValidator {
	private static final Set<String> PROFILE =
		Set.of("sportId", "goal", "environment", "focus", "category", "resources", "daysPerWeek", "durationMinutes",
			"level", "experience", "space", "preferences", "limitations");
	static final int MAX_ELIGIBLE = 48;
	private final JsonNode catalog = Json.resource("catalog-v2.json");
	private final Map<String, JsonNode> exercises = new HashMap<>(), sports = new HashMap<>();

	public ProposalValidator() {
		for (JsonNode e : catalog.path("exercises"))
			exercises.put(e.path("id").asText(), e);
		for (JsonNode s : catalog.path("sports"))
			sports.put(s.path("id").asText(), s);
	}

	public int catalogVersion() {
		return catalog.path("version").asInt();
	}

	/**
	 * Acepta perfil parcial para preguntar campos faltantes; propuestas exigen un perfil completo.
	 */
	public void validateRequest(JsonNode r) {
		Set<String> keys =
			Set.of("conversationId", "requestId", "exerciseId", "message", "profile", "historyConsent", "history");
		Json.keys(r, keys, keys);
		for (String key : List.of("conversationId", "requestId"))
			if (!Json.text(r.get(key), 80, false).matches("[A-Za-z0-9_-]+"))
				throw new IllegalArgumentException("ID inválido");
		Json.text(r.get("message"), 2000, false);
		if (!r.get("exerciseId").isNull() && !exercises.containsKey(Json.text(r.get("exerciseId"), 80, false)))
			throw new IllegalArgumentException("Ejercicio inexistente");
		if (!r.get("historyConsent").isBoolean())
			throw new IllegalArgumentException("Consentimiento inválido");
		Json.array(r.get("history"), 12);
		for (JsonNode h : r.path("history")) {
			Json.keys(h, Set.of("role", "text"), Set.of("role", "text"));
			Json.choice(h.get("role"), "user", "assistant");
			Json.text(h.get("text"), 1000, false);
		}
		JsonNode p = r.get("profile");
		Json.keys(p, PROFILE, Set.of());
		for (String key : List.of("level", "experience", "space", "preferences", "limitations"))
			if (p.has(key))
				Json.text(p.get(key), 500, !key.equals("level"));
		if (p.has("sportId") && !sports.containsKey(Json.text(p.get("sportId"), 64, false)))
			throw new IllegalArgumentException("Deporte inexistente");
		if (p.has("goal"))
			Json.choice(p.get("goal"), "STRENGTH", "GENERAL_FITNESS", "ENDURANCE", "MOBILITY", "SPORT_PRACTICE");
		if (p.has("environment"))
			Json.choice(p.get("environment"), "HOME", "GYM", "OUTDOORS", "POOL");
		if (p.has("focus"))
			Json.choice(p.get("focus"), "SPECIFIC", "COMPLEMENTARY");
		if (p.has("category"))
			Json.choice(p.get("category"), "GYM", "HOME_EQUIPMENT", "NO_EQUIPMENT", "SPORT");
		if (p.has("daysPerWeek"))
			Json.integer(p.get("daysPerWeek"), 1, 7);
		if (p.has("durationMinutes"))
			Json.integer(p.get("durationMinutes"), 5, 240);
		if (p.has("resources")) {
			Json.array(p.get("resources"), 11);
			Set<String> seen = new HashSet<>();
			for (JsonNode x : p.get("resources")) {
				Json.choice(x, "DUMBBELLS", "BARBELL", "BANDS", "MAT", "BENCH", "MACHINE", "CHAIR", "WALL", "SUPPORT",
					"BICYCLE", "POOL");
				if (!seen.add(x.asText()))
					throw new IllegalArgumentException("Recurso repetido");
			}
			if (p.path("category").asText().equals("NO_EQUIPMENT") && !seen.isEmpty())
				throw new IllegalArgumentException("Sin material incompatible con recursos");
		}
	}

	/**
	 * Recompone contexto desde el catálogo; el texto del usuario nunca concede capacidades visuales.
	 */
	public JsonNode providerRequest(JsonNode request) {
		ObjectNode result = request.deepCopy();
		if (!request.path("historyConsent").asBoolean())
			result.set("history", Json.MAPPER.createArrayNode());
		result.put("catalogVersion", catalogVersion());
		result.set("exerciseContext",
			exercises.getOrDefault(request.path("exerciseId").asText(), NullNode.getInstance()));
		// Forma compacta y acotada: el catálogo ampliado no debe agotar la guarda de bytes del proveedor gratuito.
		// La técnica completa solo viaja en exerciseContext; la validación posterior usa siempre el catálogo íntegro.
		// Reparto por grupos de material (round-robin) para que el tope no deje fuera máquinas o bandas declaradas.
		Map<String, Deque<JsonNode>> groups = new LinkedHashMap<>();
		for (JsonNode e : catalog.path("exercises"))
			if (compatible(e, request.path("profile")))
				groups.computeIfAbsent(e.path("metricType").asText() + Json.strings(e.path("requiredResources")),
					k -> new ArrayDeque<>()).add(e);
		ArrayNode eligible = result.putArray("eligibleExercises");
		while (eligible.size() < MAX_ELIGIBLE && !groups.isEmpty())
			for (Iterator<Deque<JsonNode>> it = groups.values().iterator(); it.hasNext() && eligible.size() < MAX_ELIGIBLE; ) {
				Deque<JsonNode> group = it.next();
				JsonNode e = group.poll();
				ObjectNode compact = eligible.addObject();
				compact.set("id", e.path("id"));
				compact.set("metricType", e.path("metricType"));
				compact.set("requiredResources", e.path("requiredResources"));
				if (group.isEmpty())
					it.remove();
			}
		return result;
	}

	/**
	 * Rechaza toda respuesta inválida: no aplica silenciosamente una parte de una propuesta.
	 */
	public void validateResponse(JsonNode out, JsonNode request) {
		Set<String> keys = Set.of("text", "missingFields", "proposal", "commands");
		Json.keys(out, keys, keys);
		Json.text(out.get("text"), 4000, false);
		Json.array(out.get("missingFields"), 13);
		Set<String> missing = new HashSet<>();
		for (JsonNode x : out.path("missingFields"))
			if (!PROFILE.contains(Json.text(x, 64, false)) || !missing.add(x.asText()))
				throw new IllegalArgumentException("Campo faltante inválido");
		Json.array(out.get("commands"), 3);
		for (JsonNode cmd : out.path("commands")) {
			Json.keys(cmd, Set.of("type", "exerciseId"), Set.of("type", "exerciseId"));
			Json.choice(cmd.get("type"), "OPEN_EXERCISE");
			String id = Json.text(cmd.get("exerciseId"), 80, false);
			if (!exercises.containsKey(id) || !id.equals(request.path("exerciseId").asText()))
				throw new IllegalArgumentException("Comando fuera de contexto");
		}
		JsonNode proposal = out.get("proposal");
		if (proposal.isNull())
			return;
		Json.keys(request.path("profile"), PROFILE, PROFILE);
		Json.keys(proposal, Set.of("title", "items"), Set.of("title", "items"));
		Json.text(proposal.get("title"), 120, false);
		Json.array(proposal.get("items"), 5);
		if (proposal.path("items").isEmpty())
			throw new IllegalArgumentException("Propuesta vacía");
		Set<String> seen = new HashSet<>();
		int total = 0;
		for (JsonNode item : proposal.path("items")) {
			Set<String> fields = Set.of("exerciseId", "estimatedMinutes", "prescription", "justification");
			Json.keys(item, fields, fields);
			String id = Json.text(item.get("exerciseId"), 80, false);
			JsonNode exercise = exercises.get(id);
			if (exercise == null || !seen.add(id) || !compatible(exercise, request.path("profile")))
				throw new IllegalArgumentException("Ejercicio incompatible");
			total += Json.integer(item.get("estimatedMinutes"), 1, 240);
			Json.text(item.get("justification"), 500, false);
			validatePrescription(item.get("prescription"), exercise, item.path("estimatedMinutes").asInt());
		}
		if (total > request.path("profile").path("durationMinutes").asInt())
			throw new IllegalArgumentException("Duración excesiva");
	}

	private boolean compatible(JsonNode e, JsonNode p) {
		if (!PROFILE.stream().allMatch(p::has))
			return false;
		JsonNode sport = sports.get(p.path("sportId").asText());
		if (sport == null)
			return false;
		String focus = p.path("focus").asText();
		if (!sport.path(focus.equals("SPECIFIC") ? "specificCoverage" : "complementaryCoverage").asText()
			.equals("EXAMPLE_UNREVIEWED"))
			return false;
		return Json.strings(e.path("goals")).contains(p.path("goal").asText()) &&
			Json.strings(e.path("environments")).contains(p.path("environment").asText()) &&
			Json.strings(p.path("resources")).containsAll(Json.strings(e.path("requiredResources"))) &&
			Json.strings(e.path("sportIds")).contains(p.path("sportId").asText()) &&
			e.path("coverage").asText().equals(focus);
	}

	private void validatePrescription(JsonNode entries, JsonNode exercise, int estimatedMinutes) {
		String metric = exercise.path("metricType").asText();
		Set<String> required;
		Set<String> optional;
		switch (metric) {
			case "STRENGTH" -> {
				required = Set.of("sets", "repetitions", "loadKg", "restSeconds");
				optional = Set.of();
			}
			case "ISOMETRIC" -> {
				required = Set.of("sets", "durationSeconds", "restSeconds");
				optional = Set.of();
			}
			case "ENDURANCE" -> {
				required = Set.of("timeMinutes", "intervals");
				optional = Set.of("distanceKm");
			}
			case "MOBILITY" -> {
				required = Set.of("durationSeconds", "repetitions", "side");
				optional = Set.of();
			}
			case "SPORT_PRACTICE" -> {
				required = Set.of("blocks", "durationMinutes");
				optional = Set.of();
			}
			default -> throw new IllegalArgumentException("Métrica desconocida");
		}
		Json.array(entries, 5);
		Map<String, String> values = new HashMap<>();
		for (JsonNode entry : entries) {
			Json.keys(entry, Set.of("key", "value"), Set.of("key", "value"));
			String key = Json.text(entry.get("key"), 40, false), value = Json.text(entry.get("value"), 24, false);
			if ((!required.contains(key) && !optional.contains(key)) || values.put(key, value) != null)
				throw new IllegalArgumentException("Prescripción desconocida o repetida");
			if (key.equals("side")) {
				if (!Set.of("ambos", "izquierdo", "derecho").contains(value))
					throw new IllegalArgumentException("Lado inválido");
				continue;
			}
			if (!value.matches("\\d{1,4}(?:[.,]\\d{1,2})?"))
				throw new IllegalArgumentException("Número inválido");
			BigDecimal n = new BigDecimal(value.replace(',', '.'));
			boolean zero = Set.of("loadKg", "restSeconds", "distanceKm").contains(key);
			if (n.signum() < 0 || (!zero && n.signum() == 0) || n.compareTo(BigDecimal.valueOf(maximum(key))) > 0)
				throw new IllegalArgumentException("Prescripción fuera de límite");
			if (Set.of("sets", "repetitions", "intervals", "blocks").contains(key) &&
				n.stripTrailingZeros().scale() > 0)
				throw new IllegalArgumentException("Entero requerido");
		}
		if (!values.keySet().containsAll(required))
			throw new IllegalArgumentException("Prescripción incompleta");
		Set<String> equipment = Json.strings(exercise.path("requiredResources"));
		if (number(values, "loadKg") > 0 && Collections.disjoint(equipment, Set.of("DUMBBELLS", "BARBELL", "MACHINE")))
			throw new IllegalArgumentException("Carga externa no catalogada");
		// Las unidades explícitas impiden ocultar una prescripción larga con una estimación corta.
		double seconds = switch (metric) {
			case "STRENGTH" -> Math.max(0, number(values, "sets") - 1) * number(values, "restSeconds");
			case "ISOMETRIC" -> number(values, "sets") * number(values, "durationSeconds") +
				Math.max(0, number(values, "sets") - 1) * number(values, "restSeconds");
			case "ENDURANCE" -> number(values, "timeMinutes") * 60;
			case "SPORT_PRACTICE" -> number(values, "durationMinutes") * 60;
			default -> number(values, "durationSeconds");
		};
		if (seconds > estimatedMinutes * 60)
			throw new IllegalArgumentException("Prescripción excede duración estimada");
	}

	private double number(Map<String, String> values, String key) {
		return new BigDecimal(values.getOrDefault(key, "0").replace(',', '.')).doubleValue();
	}

	private int maximum(String key) {
		return switch (key) {
			case "sets", "intervals", "blocks" -> 20;
			case "repetitions" -> 100;
			case "loadKg" -> 500;
			case "restSeconds", "durationSeconds" -> 3600;
			case "distanceKm" -> 100;
			default -> 240;
		};
	}
}
