package com.energym.app.assistant;

import com.energym.app.domain.*;

import java.util.*;
import java.math.BigDecimal;

/**
 * MODEL: valida el contrato v1 completo antes de permitir historial, propuesta o comandos.
 */
public final class ChatProtocol {
	public static final class Reply {
		public final String text;
		public final Routine proposal;
		public final boolean open;

		public Reply(String t, Routine p, boolean o) {
			text = t;
			proposal = p;
			open = o;
		}
	}

	public static Map<String, Object> profile(TrainingProfile p) {
		Map<String, Object> m = new LinkedHashMap<>();
		if (p == null)
			return m;
		m.put("sportId", p.getSportId());
		m.put("goal", p.getGoal().name());
		m.put("environment", p.getEnvironment().name());
		m.put("focus", p.getFocus().name());
		m.put("category", p.getCategory().name());
		List<String> rs = new ArrayList<>();
		for (TrainingProfile.Resource r : p.getResources())
			rs.add(r.name());
		m.put("resources", rs);
		m.put("daysPerWeek", p.getDaysPerWeek());
		m.put("durationMinutes", p.getDurationMinutes());
		m.put("level", p.getLevel());
		m.put("experience", p.getExperience());
		m.put("space", p.getSpace());
		m.put("preferences", p.getPreferences());
		m.put("limitations", p.getLimitations());
		if (!p.getDetails().isEmpty())
			m.put("details", new LinkedHashMap<>(p.getDetails()));
		return m;
	}

	public static String request(ChatClient.Request r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("conversationId", r.conversationId);
		m.put("requestId", r.requestId);
		m.put("exerciseId", r.exerciseId);
		// El servidor opcional admite hasta 2000 caracteres; el chat no limita lo que se escribe.
		m.put("message", r.message.length() <= 2000 ? r.message : r.message.substring(0, 2000));
		m.put("profile", r.profileData);
		m.put("historyConsent", r.historyConsent);
		List<Object> h = new ArrayList<>();
		if (r.historyConsent)
			for (ConversationStore.Message x : r.history)
				h.add(StrictJson.map("role", x.role, "text", x.text));
		m.put("history", h);
		return StrictJson.write(m);
	}

	public static Reply reply(String json, ChatClient.Request request, List<Exercise> catalog, int version) {
		Map<String, Object> root = StrictJson.object(StrictJson.parse(json));
		StrictJson.keys(root, "text", "missingFields", "proposal", "commands", "catalogVersion", "proposalNotice",
			"conversationId", "requestId", "exerciseId");
		if (!request.conversationId.equals(root.get("conversationId")) ||
			!request.requestId.equals(root.get("requestId")) ||
			!Objects.equals(request.exerciseId, root.get("exerciseId")))
			throw new IllegalArgumentException("Contexto diferente");
		StrictJson.integer(root.get("catalogVersion"), version, version);
		String text = StrictJson.text(root.get("text"), 4000);
		String notice = StrictJson.text(root.get("proposalNotice"), 1000);
		Set<String> fields = new HashSet<>();
		for (Object x : StrictJson.array(root.get("missingFields"), 13)) {
			String key = StrictJson.text(x, 40);
			if (!profile(new TrainingProfile.Builder().build()).containsKey(key) || !fields.add(key))
				throw new IllegalArgumentException("Pregunta inválida");
		}
		Routine proposal = null;
		if (root.get("proposal") != null) {
			if (request.profile == null)
				throw new IllegalArgumentException("Perfil incompleto");
			Map<String, Object> p = StrictJson.object(root.get("proposal"));
			StrictJson.keys(p, "title", "items");
			List<RoutineItem> items = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (Object x : StrictJson.array(p.get("items"), 5)) {
				Map<String, Object> item = StrictJson.object(x);
				StrictJson.keys(item, "exerciseId", "estimatedMinutes", "prescription", "justification");
				String id = StrictJson.text(item.get("exerciseId"), 80);
				Exercise e = find(catalog, id);
				if (e == null || !ids.add(id))
					throw new IllegalArgumentException("Ejercicio inválido");
				Map<String, String> prescription = new LinkedHashMap<>();
				for (Object v : StrictJson.array(item.get("prescription"), 5)) {
					Map<String, Object> pair = StrictJson.object(v);
					StrictJson.keys(pair, "key", "value");
					String key = StrictJson.text(pair.get("key"), 40);
					if (prescription.put(key, StrictJson.text(pair.get("value"), 64)) != null)
						throw new IllegalArgumentException("Métrica repetida");
				}
				int minutes = StrictJson.integer(item.get("estimatedMinutes"), 1, 240);
				validatePrescription(e, prescription, minutes);
				items.add(new RoutineItem(e, prescription, minutes, StrictJson.text(item.get("justification"), 1000)));
			}
			proposal =
				new Routine(UUID.randomUUID().toString(), 0, StrictJson.text(p.get("title"), 200), request.profile,
					items, notice);
			if (!proposal.isValidFor(request.profile))
				throw new IllegalArgumentException("Propuesta incompatible");
		}
		boolean open = false;
		for (Object x : StrictJson.array(root.get("commands"), 3)) {
			Map<String, Object> command = StrictJson.object(x);
			StrictJson.keys(command, "type", "exerciseId");
			if (!"OPEN_EXERCISE".equals(command.get("type")) || request.exerciseId == null ||
				!request.exerciseId.equals(command.get("exerciseId")))
				throw new IllegalArgumentException("Comando inválido");
			open = true;
		}
		return new Reply(text, proposal, open);
	}

	public static Exercise find(List<Exercise> catalog, String id) {
		for (Exercise e : catalog)
			if (e.getId().equals(id))
				return e;
		return null;
	}

	private static BigDecimal number(Map<String, String> p, String key) {
		return new BigDecimal(p.getOrDefault(key, "0").replace(',', '.'));
	}

	private static int maximum(String key) {
		switch (key) {
			case "sets":
			case "intervals":
			case "blocks":
				return 20;
			case "repetitions":
			case "distanceKm":
				return 100;
			case "loadKg":
				return 500;
			case "restSeconds":
			case "durationSeconds":
				return 3600;
			default:
				return 240;
		}
	}

	public static void validatePrescription(Exercise e, Map<String, String> p, int minutes) {
		MetricValidator validator = new MetricValidator();
		Set<String> allowed = new HashSet<>();
		for (MetricValidator.Field f : validator.fields(e.getMetricType()))
			if (!f.key.equals("effort"))
				allowed.add(f.key);
		if (!allowed.containsAll(p.keySet()) || !validator.validate(e.getMetricType(), p).isEmpty())
			throw new IllegalArgumentException("Prescripción inválida");
		if (number(p, "loadKg").signum() > 0 && Collections.disjoint(e.getRequiredResources(),
			EnumSet.of(TrainingProfile.Resource.DUMBBELLS, TrainingProfile.Resource.BARBELL,
				TrainingProfile.Resource.MACHINE)))
			throw new IllegalArgumentException("Carga sin material");
		for (Map.Entry<String, String> v : p.entrySet())
			if (!v.getKey().equals("side")) {
				if (!v.getValue().matches("\\d{1,4}(?:[.,]\\d{1,2})?") ||
					number(p, v.getKey()).compareTo(BigDecimal.valueOf(maximum(v.getKey()))) > 0)
					throw new IllegalArgumentException("Prescripción fuera de límite");
			}
		BigDecimal seconds = BigDecimal.ZERO;
		switch (e.getMetricType()) {
			case STRENGTH:
				seconds = number(p, "sets").subtract(BigDecimal.ONE).multiply(number(p, "restSeconds"));
				break;
			case ISOMETRIC:
				seconds = number(p, "sets").multiply(number(p, "durationSeconds"))
					.add(number(p, "sets").subtract(BigDecimal.ONE).multiply(number(p, "restSeconds")));
				break;
			case ENDURANCE:
				seconds = number(p, "timeMinutes").multiply(BigDecimal.valueOf(60));
				break;
			case MOBILITY:
				seconds = number(p, "durationSeconds");
				break;
			case SPORT_PRACTICE:
				seconds = number(p, "durationMinutes").multiply(BigDecimal.valueOf(60));
				break;
		}
		if (seconds.compareTo(BigDecimal.valueOf(minutes * 60L)) > 0)
			throw new IllegalArgumentException("Tiempo prescrito excesivo");
	}
}




