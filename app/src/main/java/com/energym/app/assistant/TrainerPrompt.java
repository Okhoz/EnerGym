package com.energym.app.assistant;

import com.energym.app.domain.*;

import java.util.*;

/**
 * MODELO del entrenador personal local: arma un prompt compacto para Gemini Nano (entrada de unos 4000 tokens)
 * y traduce su respuesta al mismo JSON que devuelve el servidor, para que ChatProtocol.reply lo valide igual.
 * El modelo elige ejercicios, orden y explicación; series, tiempos y descansos se acotan aquí con reglas seguras
 * y dejan margen para que la sobrecarga progresiva suba la carga sesión a sesión.
 */
public final class TrainerPrompt {
	static final int MAX_EXERCISES = 30, MAX_RECENT = 6, MAX_RECENT_CHARS = 300, MAX_DESCRIPTION = 800,
		MAX_MESSAGE = 1200;
	private static final List<String> REQUIRED = Arrays.asList("sportId", "goal", "environment", "focus", "category",
		"resources", "daysPerWeek", "durationMinutes", "level");
	static final String NOTICE = "Rutina creada por la IA de tu móvil (Gemini Nano), sin enviar datos a ningún " +
		"servidor. Es un ejemplo sin revisión profesional: adapta la intensidad y para si notas dolor.";

	private TrainerPrompt() {
	}

	/**
	 * Ejercicios del catálogo compatibles con el perfil completo, en el orden del catálogo y acotados en número.
	 */
	static List<Exercise> eligible(TrainingProfile profile, List<Exercise> catalog) {
		List<Exercise> result = new ArrayList<>();
		if (profile == null)
			return result;
		CompatibilityEngine engine = new CompatibilityEngine();
		for (Exercise e : catalog)
			if (engine.isCompatible(e, profile) && result.size() < MAX_EXERCISES)
				result.add(e);
		return result;
	}

	public static String build(ChatClient.Request r, List<Exercise> catalog) {
		StringBuilder p = new StringBuilder();
		p.append("Eres el entrenador personal de la app EnerGym y hablas con tu cliente dentro de la app. ")
			.append("Responde en español, cercano y motivador, con frases cortas y concretas. ")
			.append("Ayudas con rutinas, técnica, progresión, motivación, descanso y hábitos. ")
			.append("No diagnosticas ni tratas lesiones: ante dolor, mareo o una lesión recomienda parar y consultar ")
			.append("a un profesional sanitario. No inventes datos del cliente ni material que no tenga.\n")
			.append("Responde SOLO con un objeto JSON, sin texto fuera de él, con esta forma:\n")
			.append("{\"texto\":\"tu respuesta\",\"rutina\":null}\n");
		List<Exercise> eligible = eligible(r.profile, catalog);
		if (r.profile != null && !eligible.isEmpty())
			p.append("Si el cliente pide una rutina, un entrenamiento o qué hacer hoy, usa en su lugar:\n")
				.append("{\"texto\":\"explicación breve\",\"rutina\":{\"titulo\":\"nombre corto\",\"ejercicios\":[")
				.append("{\"id\":\"id exacto de EJERCICIOS DISPONIBLES\",\"series\":3,\"repeticiones\":10,")
				.append("\"motivo\":\"por qué encaja con su perfil\"}]}}\n")
				.append("Elige de 3 a 5 ejercicios solo de la lista, en buen orden (movilidad o calentamiento primero ")
				.append("si los hay). Empieza conservador, con un esfuerzo de 6 o 7 sobre 10, para que la app pueda ")
				.append("subir series, repeticiones, carga o tiempo en cada sesión (sobrecarga progresiva). ")
				.append("Ten en cuenta el nivel, la experiencia, las limitaciones y los datos de su disciplina.\n");
		p.append("\nPERFIL DEL CLIENTE\n").append(profile(r.profileData)).append('\n');
		if (r.profile == null) {
			List<String> missing = missing(r.profileData);
			p.append("Perfil incompleto (falta: ").append(String.join(", ", missing)).append("). No propongas rutina: ")
				.append("responde a lo que pregunte y pídele amablemente que complete esos datos en Perfil.\n");
		}
		if (!eligible.isEmpty()) {
			p.append("\nEJERCICIOS DISPONIBLES (id | nombre | tipo)\n");
			for (Exercise e : eligible)
				p.append(e.getId()).append(" | ").append(e.getName()).append(" | ").append(metric(e.getMetricType()))
					.append('\n');
		}
		Exercise asked = r.exerciseId == null ? null : ChatProtocol.find(catalog, r.exerciseId);
		if (asked != null)
			p.append("\nEJERCICIO QUE CONSULTA: ").append(asked.getName()).append(". Técnica: ")
				.append(cut(asked.getDescription(), MAX_DESCRIPTION)).append('\n');
		if (!r.recent.isEmpty()) {
			p.append("\nCONVERSACIÓN RECIENTE\n");
			for (ConversationStore.Message m : r.recent)
				p.append(m.role.equals("user") ? "Cliente: " : "Entrenador: ").append(cut(m.text, MAX_RECENT_CHARS))
					.append('\n');
		}
		p.append("\nMENSAJE DEL CLIENTE: ").append(cut(r.message, MAX_MESSAGE)).append('\n');
		return p.toString();
	}

	/**
	 * Convierte la salida del modelo al JSON del protocolo. Una salida sin JSON se trata como respuesta de texto.
	 */
	public static String reply(String output, ChatClient.Request r, List<Exercise> catalog, int catalogVersion) {
		String text = output == null ? "" : output.trim();
		Map<String, Object> routine = null;
		Map<String, Object> parsed = json(text);
		if (parsed != null) {
			Object t = parsed.get("texto");
			text = t instanceof String ? ((String) t).trim() : "";
			if (parsed.get("rutina") instanceof Map)
				routine = cast(parsed.get("rutina"));
		} else
			text = text.replaceAll("```[a-zA-Z]*", "").trim();
		Map<String, Object> proposal = r.profile == null || routine == null ? null : proposal(routine, r, catalog);
		if (text.isEmpty())
			text = proposal != null ? "Aquí tienes tu entrenamiento de hoy. Revísalo antes de aceptarlo." :
				"No he podido preparar una respuesta. ¿Puedes repetírmelo con otras palabras?";
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("text", cut(text, 4000));
		m.put("missingFields", r.profile == null ? new ArrayList<Object>(missing(r.profileData)) : new ArrayList<>());
		m.put("proposal", proposal);
		m.put("commands", new ArrayList<>());
		m.put("catalogVersion", catalogVersion);
		m.put("proposalNotice", NOTICE);
		m.put("conversationId", r.conversationId);
		m.put("requestId", r.requestId);
		m.put("exerciseId", r.exerciseId);
		return StrictJson.write(m);
	}

	private static Map<String, Object> proposal(Map<String, Object> routine, ChatClient.Request r,
		List<Exercise> catalog) {
		List<Exercise> eligible = eligible(r.profile, catalog);
		if (eligible.isEmpty())
			return null;
		Map<String, Exercise> byId = new LinkedHashMap<>();
		for (Exercise e : eligible)
			byId.put(e.getId(), e);
		List<Exercise> chosen = new ArrayList<>();
		Map<String, Map<String, Object>> asked = new HashMap<>();
		if (routine.get("ejercicios") instanceof List)
			for (Object x : (List<?>) routine.get("ejercicios")) {
				if (!(x instanceof Map))
					continue;
				Map<String, Object> item = cast(x);
				Exercise e = byId.get(String.valueOf(item.get("id")).trim());
				if (e != null && !asked.containsKey(e.getId()) && chosen.size() < 5) {
					chosen.add(e);
					asked.put(e.getId(), item);
				}
			}
		// Si el modelo no acertó ningún ID, la rutina se arma con los primeros compatibles del catálogo.
		if (chosen.isEmpty())
			chosen.addAll(eligible.subList(0, Math.min(3, eligible.size())));
		// El bloque de resistencia consume el tiempo restante: siempre al final.
		List<Exercise> ordered = new ArrayList<>();
		for (Exercise e : chosen)
			if (e.getMetricType() != MetricType.ENDURANCE && e.getMetricType() != MetricType.SPORT_PRACTICE)
				ordered.add(e);
		for (Exercise e : chosen)
			if (!ordered.contains(e))
				ordered.add(e);
		TrainingProfile profile = r.profile;
		boolean beginner = beginner(profile.getLevel());
		// Margen libre para que la progresión pueda añadir series o minutos sin pasarse del tiempo declarado.
		int remaining = Math.max(5, (int) Math.floor(profile.getDurationMinutes() * 0.85));
		List<Object> items = new ArrayList<>();
		for (Exercise e : ordered) {
			Map<String, Object> hint = asked.getOrDefault(e.getId(), Collections.emptyMap());
			Prescription p = prescribe(e, hint, beginner, profile.getGoal(), remaining);
			if (p == null || p.minutes > remaining)
				continue;
			remaining -= p.minutes;
			List<Object> pairs = new ArrayList<>();
			for (Map.Entry<String, String> v : p.values.entrySet())
				pairs.add(StrictJson.map("key", v.getKey(), "value", v.getValue()));
			Object why = hint.get("motivo");
			String justification = why instanceof String && !((String) why).trim().isEmpty() ?
				cut(((String) why).trim(), 900) : "Encaja con tu disciplina, objetivo, lugar y material.";
			items.add(StrictJson.map("exerciseId", e.getId(), "estimatedMinutes", p.minutes, "prescription", pairs,
				"justification", justification));
			if (e.getMetricType() == MetricType.ENDURANCE || e.getMetricType() == MetricType.SPORT_PRACTICE)
				break;
		}
		if (items.isEmpty())
			return null;
		Object title = routine.get("titulo");
		return StrictJson.map("title", title instanceof String && !((String) title).trim().isEmpty() ?
			cut(((String) title).trim(), 200) : "Entrenamiento de tu entrenador", "items", items);
	}

	static final class Prescription {
		final Map<String, String> values = new LinkedHashMap<>();
		int minutes;
	}

	/**
	 * Prescripción conservadora dentro de los límites del validador; series y repeticiones del modelo se acotan.
	 */
	static Prescription prescribe(Exercise e, Map<String, Object> hint, boolean beginner, TrainingProfile.Goal goal,
		int remaining) {
		Prescription p = new Prescription();
		switch (e.getMetricType()) {
			case STRENGTH: {
				int sets = clamp(number(hint.get("series"), beginner ? 2 : 3), 1, beginner ? 3 : 4);
				int reps = clamp(number(hint.get("repeticiones"), 10), 5, 15);
				int rest = goal == TrainingProfile.Goal.STRENGTH && !beginner ? 90 : 60;
				p.values.put("sets", Integer.toString(sets));
				p.values.put("repetitions", Integer.toString(reps));
				p.values.put("loadKg", "0");
				p.values.put("restSeconds", Integer.toString(rest));
				p.minutes = (int) Math.ceil((sets * reps * 4 + (sets - 1) * rest) / 60.0) + 1;
				return p;
			}
			case ISOMETRIC: {
				int sets = clamp(number(hint.get("series"), 2), 1, 4);
				int seconds = beginner ? 20 : 30;
				p.values.put("sets", Integer.toString(sets));
				p.values.put("durationSeconds", Integer.toString(seconds));
				p.values.put("restSeconds", "60");
				p.minutes = (int) Math.ceil((sets * seconds + (sets - 1) * 60) / 60.0) + 1;
				return p;
			}
			case MOBILITY: {
				int reps = clamp(number(hint.get("repeticiones"), 5), 3, 10);
				p.values.put("durationSeconds", "30");
				p.values.put("repetitions", Integer.toString(reps));
				p.values.put("side", "ambos");
				p.minutes = 3;
				return p;
			}
			case ENDURANCE: {
				int minutes = Math.min(beginner ? 25 : 45, remaining - 2);
				if (minutes < 5)
					return null;
				p.values.put("timeMinutes", Integer.toString(minutes));
				p.values.put("intervals", "1");
				p.minutes = minutes + 2;
				return p;
			}
			case SPORT_PRACTICE: {
				int minutes = Math.min(20, remaining - 1);
				if (minutes < 5)
					return null;
				p.values.put("blocks", "2");
				p.values.put("durationMinutes", Integer.toString(minutes));
				p.minutes = minutes + 1;
				return p;
			}
			default:
				return null;
		}
	}

	private static boolean beginner(String level) {
		String l = level == null ? "" : level.toLowerCase(Locale.ROOT);
		return l.isEmpty() || l.contains("princip") || l.contains("inici") || l.contains("novat");
	}

	private static String profile(Map<String, Object> data) {
		StringBuilder s = new StringBuilder();
		String[][] labels = {{"sportId", "Disciplina"}, {"goal", "Objetivo"}, {"focus", "Enfoque"},
			{"environment", "Dónde entrena"}, {"category", "Tipo"}, {"resources", "Material"},
			{"daysPerWeek", "Días por semana"}, {"durationMinutes", "Minutos por sesión"}, {"level", "Nivel"},
			{"experience", "Experiencia"}, {"space", "Espacio"}, {"preferences", "Preferencias"},
			{"limitations", "Limitaciones"}, {"details", "Datos de su disciplina"}};
		for (String[] l : labels) {
			Object v = data.get(l[0]);
			if (v == null || v.toString().trim().isEmpty() || v.toString().equals("[]") || v.toString().equals("{}"))
				continue;
			s.append("- ").append(l[1]).append(": ").append(cut(v.toString(), 300)).append('\n');
		}
		return s.length() == 0 ? "- Sin datos todavía\n" : s.toString();
	}

	private static List<String> missing(Map<String, Object> data) {
		List<String> result = new ArrayList<>();
		for (String key : REQUIRED)
			if (!data.containsKey(key))
				result.add(key);
		return result;
	}

	private static String metric(MetricType type) {
		switch (type) {
			case STRENGTH:
				return "fuerza";
			case ISOMETRIC:
				return "isométrico";
			case ENDURANCE:
				return "resistencia";
			case MOBILITY:
				return "movilidad";
			default:
				return "práctica";
		}
	}

	/**
	 * Primer objeto JSON de la salida (tolera vallas ``` y texto alrededor); null si no hay uno legible.
	 */
	private static Map<String, Object> json(String text) {
		int start = text.indexOf('{'), end = text.lastIndexOf('}');
		if (start < 0 || end <= start)
			return null;
		try {
			Object value = StrictJson.parse(text.substring(start, end + 1));
			return value instanceof Map ? cast(value) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object value) {
		return (Map<String, Object>) value;
	}

	private static int number(Object value, int fallback) {
		try {
			return value == null ? fallback : (int) Math.round(Double.parseDouble(value.toString().trim()));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String cut(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}
}
