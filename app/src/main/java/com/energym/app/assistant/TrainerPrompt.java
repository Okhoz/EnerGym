package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.domain.*;

import java.util.*;

/**
 * MODELO del entrenador personal local: arma un prompt compacto para Gemini Nano (entrada de unos 4000 tokens)
 * y traduce su respuesta al mismo JSON que devuelve el servidor, para que ChatProtocol.reply lo valide igual.
 * El modelo elige ejercicios, orden y explicación; series, tiempos y descansos se acotan aquí con reglas seguras
 * y dejan margen para que la sobrecarga progresiva suba la carga sesión a sesión.
 */
public final class TrainerPrompt {
	/**
	 * El mensaje del cliente no tiene límite al escribirlo; al modelo le llega entero si cabe en el prompt y, si no,
	 * su principio y su final (MIN_MESSAGE es lo mínimo que se le reserva aunque haya mucho contexto).
	 */
	static final int MAX_EXERCISES = 30, MAX_RECENT = 6, MAX_RECENT_CHARS = 300, MAX_DESCRIPTION = 800,
		MAX_TRAINING = 3800, MIN_TRAINING = 600, MIN_MESSAGE = 1500, MAX_PROMPT = 11500;
	private static final List<String> REQUIRED = Arrays.asList("sportId", "goal", "environment", "focus", "category",
		"resources", "daysPerWeek", "durationMinutes", "level");

	static String notice() {
		return com.energym.app.i18n.Txt.t(com.energym.app.R.string.tp_notice);
	}

	/** Idioma de la respuesta: el de la app (el prompt sigue en español, el modelo lo entiende igual). */
	private static String language() {
		return "en".equals(com.energym.app.i18n.Txt.locale().getLanguage()) ?
			"Responde SIEMPRE en inglés (English), aunque estas instrucciones estén en español. " :
			"Responde en español. ";
	}

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

	/** Todos los ejercicios compatibles con el perfil, en el orden del catálogo. */
	static List<Exercise> compatible(TrainingProfile profile, List<Exercise> catalog) {
		List<Exercise> result = new ArrayList<>();
		if (profile == null)
			return result;
		CompatibilityEngine engine = new CompatibilityEngine();
		for (Exercise e : catalog)
			if (engine.isCompatible(e, profile))
				result.add(e);
		return result;
	}

	/**
	 * Lo que pide en este mensaje; si no concreta nada (p. ej. «sí, venga»), lo de sus últimos mensajes.
	 */
	static RoutineRequest request(ChatClient.Request r) {
		RoutineRequest asked = RoutineRequest.parse(r.message);
		if (!asked.isEmpty())
			return asked;
		StringBuilder before = new StringBuilder();
		int seen = 0;
		for (int i = r.recent.size() - 1; i >= 0 && seen < 2; i--)
			if (r.recent.get(i).role.equals("user")) {
				before.insert(0, r.recent.get(i).text + " ");
				seen++;
			}
		RoutineRequest earlier = RoutineRequest.parse(before.toString());
		if (!earlier.isEmpty())
			return earlier;
		// Si no concreta nada, se priorizan los grupos que lleva 2 semanas sin trabajar (lo haría un entrenador).
		return RoutineRequest.parse(neglected(r.training));
	}

	/** Grupos descuidados según el análisis del entrenador incluido en el contexto; "" si no hay. */
	static String neglected(String training) {
		if (training == null)
			return "";
		int at = training.indexOf(com.energym.app.progress.CoachInsights.NEGLECTED);
		if (at < 0)
			return "";
		int end = training.indexOf('\n', at);
		return training.substring(at + com.energym.app.progress.CoachInsights.NEGLECTED.length(),
			end < 0 ? training.length() : end);
	}

	static long seed(ChatClient.Request r) {
		return r.requestId == null ? 0 : r.requestId.hashCode();
	}

	/**
	 * Lista para el modelo: los compatibles más afines a lo pedido (varían entre peticiones), no siempre los
	 * primeros del catálogo.
	 */
	static List<Exercise> eligible(ChatClient.Request r, List<Exercise> catalog) {
		List<Exercise> ranked = request(r).rank(compatible(r.profile, catalog), seed(r));
		return ranked.size() > MAX_EXERCISES ? new ArrayList<>(ranked.subList(0, MAX_EXERCISES)) : ranked;
	}

	public static String build(ChatClient.Request r, List<Exercise> catalog) {
		StringBuilder p = new StringBuilder();
		p.append("Eres el entrenador personal de la app EnerGym y hablas con tu cliente dentro de la app. ")
			.append(language()).append("Tono cercano y motivador, con frases cortas y concretas. ")
			.append("Redacta bien: buena ortografía y puntuación (¿?, ¡!, comas y puntos), párrafos cortos separados ")
			.append("por una línea en blanco (\\n\\n dentro del JSON), listas con «• » y cada punto en su línea (\\n), ")
			.append("y de 1 a 3 emoticonos que encajen (💪 🔥 ✅ 🧘 👏). Resalta con **negrita** solo 1 o 2 ideas clave; " +
				"ningún otro Markdown. ")
			.append("Ayudas con rutinas, técnica, progresión, motivación, descanso y hábitos. ")
			.append("No diagnosticas ni tratas lesiones: ante dolor, mareo o una lesión recomienda parar y consultar ")
			.append("a un profesional sanitario. No inventes datos del cliente ni material que no tenga.\n")
			.append("Ante una duda, responde directamente con 2 a 4 consejos prácticos y concretos (técnica, ")
			.append("ejercicios de apoyo, errores frecuentes). No pidas datos si no hacen falta para responder.\n")
			.append("Piensa como un entrenador con experiencia: \n")
			.append("• RPE = esfuerzo de 1 a 10 (8 = le quedaban 2 repeticiones; 10 = al fallo). Lo ideal es 7–8.\n")
			.append("• Si el ANÁLISIS DEL ENTRENADOR avisa de molestias, no cargues esa zona, propón alternativas suaves ")
			.append("y recomienda consultar a un profesional si persisten.\n")
			.append("• Si hay ejercicios estancados, propón un cambio concreto (otro rango de repeticiones, una variante ")
			.append("o una semana de descarga) en lugar de repetir lo mismo.\n")
			.append("• Si el esfuerzo va muy alto o lleva días sin entrenar, baja la intensidad; si va sobrado, súbela.\n")
			.append("• Si no pide nada concreto, prioriza los grupos que lleva tiempo sin trabajar.\n")
			.append("• Usa sus números reales (kilos, repeticiones, RPE, fechas) y explica el porqué en una frase.\n")
			.append("Ejemplo de buena respuesta a «¿subo peso en sentadilla?» con datos 80 kg × 8 a RPE 7:\n")
			.append("{\"texto\":\"¡Sí! 💪 El martes hiciste 80 kg × 8 con RPE 7: te sobraban unas 3 repeticiones.\\n\\n")
			.append("• Sube a 82,5 kg y busca 8 repeticiones a RPE 8.\\n• Si la técnica se rompe, vuelve a 80 kg.\",")
			.append("\"rutina\":null}\n")
			.append("Responde SOLO con un objeto JSON, sin texto fuera de él, con esta forma:\n")
			.append("{\"texto\":\"tu respuesta\",\"rutina\":null}\n");
		List<Exercise> eligible = eligible(r, catalog);
		RoutineRequest wants = request(r);
		if (r.profile != null && !eligible.isEmpty())
			p.append("Si el cliente pide una rutina, un entrenamiento o qué hacer hoy, usa en su lugar:\n")
				.append("{\"texto\":\"explicación breve\",\"rutina\":{\"titulo\":\"nombre corto\",\"ejercicios\":[")
				.append("{\"id\":\"id exacto de EJERCICIOS DISPONIBLES\",\"series\":3,\"repeticiones\":10,")
				.append("\"motivo\":\"por qué encaja con su perfil\"}]}}\n")
				.append("Elige de 3 a 5 ejercicios solo de la lista, en buen orden (movilidad o calentamiento primero ")
				.append("si los hay). La rutina va SOLO en \"rutina\": nunca copies la rutina, IDs ni JSON dentro de ")
				.append("\"texto\". En \"texto\" (2 o 3 párrafos cortos) explica el enfoque y da consejos de técnica, pero no escribas ")
				.append("series, repeticiones ni segundos: la app los calcula y los muestra. Empieza conservador, con un esfuerzo de 6 o 7 sobre 10. ")
				.append("Para la sobrecarga progresiva, conserva los ejercicios que ya hizo si encajan: la app parte de su ")
				.append("última carga y sube sola la rutina tras cada sesión (repeticiones, luego carga). ")
				.append("Ten en cuenta el nivel, la experiencia, las limitaciones y los datos de su disciplina.\n");
		if (r.profile != null && !wants.describe().isEmpty())
			p.append("LO QUE PIDE PARA HOY: ").append(wants.describe()).append(". La rutina debe trabajar exactamente ")
				.append("eso: elige ejercicios de esas zonas (la lista está ordenada de más a menos afín) y respeta lo que ")
				.append("quiere evitar. Ponle un título que lo refleje.\n");
		p.append("\nPERFIL DEL CLIENTE\n").append(profile(r.profileData)).append('\n');
		StringBuilder tail = new StringBuilder();
		List<String> missing = missing(r.profileData);
		// Sin perfil guardado (o con datos a medias) no se pueden preparar rutinas; con el perfil completo, sí.
		if (r.profile == null && !missing.isEmpty())
			tail.append("Perfil incompleto (falta: ").append(String.join(", ", missing)).append("). Responde a su ")
				.append("pregunta igualmente. Solo si pide una rutina, explica que primero complete su perfil en Perfil.\n");
		else if (r.profile == null)
			tail.append("Si pide una rutina, dile que la pida con el botón «🆕 Rutina nueva» del chat.\n");
		if (!eligible.isEmpty()) {
			tail.append("\nEJERCICIOS DISPONIBLES (id | nombre | tipo)\n");
			for (Exercise e : eligible)
				tail.append(e.getId()).append(" | ").append(e.getName()).append(" | ").append(metric(e.getMetricType()))
					.append('\n');
		}
		Exercise asked = r.exerciseId == null ? null : ChatProtocol.find(catalog, r.exerciseId);
		if (asked != null)
			tail.append("\nEJERCICIO QUE CONSULTA: ").append(asked.getName()).append(". Técnica: ")
				.append(cut(asked.getDescription(), MAX_DESCRIPTION)).append('\n');
		String trainingIntro = "\nSU ENTRENAMIENTO Y TODO LO QUE HA HECHO EN LA APP (datos reales)\n";
		String trainingRules = "Conoces todo esto: úsalo en cada respuesta y cita sus datos concretos (sesiones, cargas, " +
			"peso, comidas, objetivos). No inventes datos que no aparezcan. Relaciona entreno y dieta con su objetivo, " +
			"no repitas lo que ya le cuesta y sube poco a poco lo que domina. Si hay una subida de nivel reciente, " +
			"felicítale y explica qué subió; si hay un entreno a medias, díselo (botones del chat).\n";
		// Reparto del espacio que queda: primero el mensaje (el cliente puede escribir sin límite), luego sus datos
		// y, con lo que sobre, la conversación reciente. Así el prompt nunca supera lo que admite el modelo.
		String user = r.message == null ? "" : r.message.trim();
		boolean hasTraining = r.training != null && !r.training.isEmpty();
		int room = MAX_PROMPT - p.length() - tail.length() - 60 -
			(hasTraining ? trainingIntro.length() + trainingRules.length() : 0);
		int forMessage = Math.min(user.length(), Math.max(MIN_MESSAGE, room - (hasTraining ? MIN_TRAINING : 0)));
		String message = "\nMENSAJE DEL CLIENTE: " + middleCut(user, forMessage) + '\n';
		room -= message.length();
		if (hasTraining) {
			int forTraining = Math.min(MAX_TRAINING, room);
			if (forTraining >= 200) {
				String training = cut(r.training, forTraining);
				p.append(trainingIntro).append(training).append('\n').append(trainingRules);
				room -= training.length();
			}
		}
		p.append(tail);
		// La conversación reciente usa solo el espacio que queda: se conservan los mensajes más nuevos.
		List<String> lines = new ArrayList<>();
		for (int i = r.recent.size() - 1; i >= 0 && lines.size() < MAX_RECENT; i--) {
			ConversationStore.Message m = r.recent.get(i);
			String line = (m.role.equals("user") ? "Cliente: " : "Entrenador: ") + cut(m.text, MAX_RECENT_CHARS) + '\n';
			if (line.length() > room)
				break;
			room -= line.length();
			lines.add(0, line);
		}
		if (!lines.isEmpty()) {
			p.append("\nCONVERSACIÓN RECIENTE\n");
			for (String line : lines)
				p.append(line);
		}
		p.append(message);
		return p.toString();
	}

	/**
	 * Convierte la salida del modelo al JSON del protocolo. Una salida sin JSON se trata como respuesta de texto.
	 */
	public static String reply(String output, ChatClient.Request r, List<Exercise> catalog, int catalogVersion) {
		String text = output == null ? "" : output.trim();
		Map<String, Object> routine = null;
		Map<String, Object> parsed = extractJson(text);
		if (parsed != null) {
			Object t = parsed.get("texto");
			text = t instanceof String ? ((String) t).trim() : "";
			if (parsed.get("rutina") instanceof Map)
				routine = cast(parsed.get("rutina"));
		} else if (text.contains("\"texto\"")) {
			// JSON roto (p. ej. una llave de más): nunca se enseña crudo; se rescata solo el texto si se puede.
			java.util.regex.Matcher t = java.util.regex.Pattern.compile("\"texto\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
				.matcher(text);
			String found = t.find() ? t.group(1) : null;
			// Respuesta cortada por el límite de salida: el texto no llega a cerrarse, pero lo escrito se aprovecha.
			if (found == null) {
				java.util.regex.Matcher open = java.util.regex.Pattern.compile("\"texto\"\\s*:\\s*\"").matcher(text);
				found = open.find() ? text.substring(open.end()).replaceAll("\\\\$", "") : "";
				if (!found.isEmpty())
					found = found.trim() + "…";
			}
			text = found.replace("\\n", "\n").replace("\\\"", "\"");
		} else
			text = text.replaceAll("```[a-zA-Z]*", "").trim();
		// A veces el modelo mete la rutina en JSON dentro del propio texto (incluso cortada): se quita del texto y,
		// si trae ejercicios, se usa como rutina.
		String source = text;
		int brace = text.indexOf('{');
		if (brace >= 0) {
			Map<String, Object> embedded = extractJson(text.substring(brace));
			if (routine == null && embedded != null)
				routine = embedded.get("rutina") instanceof Map ? cast(embedded.get("rutina")) :
					embedded.get("ejercicios") instanceof List ? embedded : null;
			text = text.substring(0, brace).replaceAll("(?i)(^|\\n)[^\\n]{0,20}rutina[^\\n]{0,10}:\\s*$", "").trim();
		}
		boolean asked = asksForRoutine(r.message);
		if (routine == null && r.profile != null && (!r.conversation || asked))
			routine = fromNames(source, eligible(r, catalog));
		// Si pidió una rutina y el modelo no la dio, se arma con los primeros ejercicios compatibles del catálogo.
		if (routine == null && r.profile != null && asked)
			routine = new LinkedHashMap<>();
		Map<String, Object> proposal = r.profile == null || routine == null ? null : proposal(routine, r, catalog);
		if (text.isEmpty())
			text = com.energym.app.i18n.Txt.t(proposal != null ? com.energym.app.R.string.tp_here_is_routine :
				com.energym.app.R.string.tp_no_answer);
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("text", cut(plain(text), 4000));
		m.put("missingFields", r.profile == null ? new ArrayList<Object>(missing(r.profileData)) : new ArrayList<>());
		m.put("proposal", proposal);
		m.put("commands", new ArrayList<>());
		m.put("catalogVersion", catalogVersion);
		m.put("proposalNotice", notice());
		m.put("conversationId", r.conversationId);
		m.put("requestId", r.requestId);
		m.put("exerciseId", r.exerciseId);
		return StrictJson.write(m);
	}

	static boolean asksForRoutine(String message) {
		String m = java.text.Normalizer.normalize(message == null ? "" : message.toLowerCase(Locale.ROOT),
			java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return m.matches("(?s).*\\b(rutina|entrenamiento|entreno|sesion|que (hago|entreno) hoy|plan de|routine|workout|" +
			"what (should|do) i (do|train) today)\\b.*");
	}

	/**
	 * Los modelos pequeños a veces describen la rutina en el texto sin rellenar «rutina»: si nombra al menos dos
	 * ejercicios disponibles, se arma con ellos en el orden en que aparecen. Si no, null (solo conversación).
	 */
	static Map<String, Object> fromNames(String text, List<Exercise> eligible) {
		String lower = text.toLowerCase(Locale.ROOT);
		TreeMap<Integer, Exercise> found = new TreeMap<>();
		boolean byId = false;
		for (Exercise e : eligible) {
			int at = lower.indexOf(e.getName().toLowerCase(Locale.ROOT));
			// Un ID entre comillas procede de un JSON de rutina (aunque esté cortado): basta con uno.
			int id = lower.indexOf("\"" + e.getId() + "\"");
			if (id >= 0)
				byId = true;
			at = at < 0 ? id : id < 0 ? at : Math.min(at, id);
			if (at >= 0)
				found.putIfAbsent(at, e);
		}
		if (found.size() < (byId ? 1 : 2))
			return null;
		List<Object> items = new ArrayList<>();
		for (Exercise e : found.values())
			items.add(StrictJson.map("id", e.getId()));
		return StrictJson.map("ejercicios", items);
	}

	private static Map<String, Object> proposal(Map<String, Object> routine, ChatClient.Request r,
		List<Exercise> catalog) {
		List<Exercise> eligible = eligible(r, catalog);
		if (eligible.isEmpty())
			return null;
		RoutineRequest wants = request(r);
		int max = wants.count > 0 ? wants.count : 5;
		Map<String, Exercise> byId = new LinkedHashMap<>();
		for (Exercise e : eligible)
			byId.put(e.getId(), e);
		List<Exercise> chosen = new ArrayList<>();
		Map<String, Map<String, Object>> hints = new HashMap<>();
		if (routine.get("ejercicios") instanceof List)
			for (Object x : (List<?>) routine.get("ejercicios")) {
				if (!(x instanceof Map))
					continue;
				Map<String, Object> item = cast(x);
				Exercise e = byId.get(String.valueOf(item.get("id")).trim());
				if (e != null && !hints.containsKey(e.getId()) && chosen.size() < max) {
					chosen.add(e);
					hints.put(e.getId(), item);
				}
			}
		// Si el modelo no acertó ningún ID, la rutina se arma con lo que pidió (o de cuerpo completo), no con los
		// primeros del catálogo.
		if (chosen.isEmpty())
			chosen.addAll(wants.compose(compatible(r.profile, catalog), seed(r), max));
		// El bloque de resistencia consume el tiempo restante: siempre al final.
		List<Exercise> ordered = new ArrayList<>();
		for (Exercise e : chosen)
			if (e.getMetricType() != MetricType.ENDURANCE && e.getMetricType() != MetricType.SPORT_PRACTICE)
				ordered.add(e);
		for (Exercise e : chosen)
			if (!ordered.contains(e))
				ordered.add(e);
		TrainingProfile profile = r.profile;
		boolean beginner = beginner(profile.getLevel()) || wants.intensity == RoutineRequest.Intensity.EASY;
		// Margen libre para que la progresión pueda añadir series o minutos sin pasarse del tiempo declarado.
		int remaining = Math.max(5, (int) Math.floor(profile.getDurationMinutes() * 0.85));
		List<Object> items = new ArrayList<>();
		for (Exercise e : ordered) {
			Map<String, Object> hint = hints.getOrDefault(e.getId(), Collections.emptyMap());
			Prescription p = prescribe(e, hint, beginner, profile.getGoal(), remaining);
			// Pidió algo intenso: una serie más en fuerza, sin pasar de 4.
			if (p != null && wants.intensity == RoutineRequest.Intensity.HARD && e.getMetricType() == MetricType.STRENGTH &&
				!beginner(profile.getLevel())) {
				int sets = Math.min(4, Integer.parseInt(p.values.get("sets")) + 1);
				p.minutes += (sets - Integer.parseInt(p.values.get("sets"))) * 2;
				p.values.put("sets", Integer.toString(sets));
			}
			if (p == null || p.minutes > remaining)
				continue;
			remaining -= p.minutes;
			List<Object> pairs = new ArrayList<>();
			for (Map.Entry<String, String> v : p.values.entrySet())
				pairs.add(StrictJson.map("key", v.getKey(), "value", v.getValue()));
			Object why = hint.get("motivo");
			String justification = why instanceof String && !((String) why).trim().isEmpty() ?
				cut(((String) why).trim(), 900) : com.energym.app.i18n.Txt.t(com.energym.app.R.string.tp_fits);
			items.add(StrictJson.map("exerciseId", e.getId(), "estimatedMinutes", p.minutes, "prescription", pairs,
				"justification", justification));
			if (e.getMetricType() == MetricType.ENDURANCE || e.getMetricType() == MetricType.SPORT_PRACTICE)
				break;
		}
		if (items.isEmpty())
			return null;
		Object title = routine.get("titulo");
		String fallback = wants.title().isEmpty() ? com.energym.app.i18n.Txt.t(com.energym.app.R.string.tp_title) : wants.title();
		return StrictJson.map("title", title instanceof String && !((String) title).trim().isEmpty() ?
			cut(((String) title).trim(), 200) : fallback, "items", items);
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

	static boolean beginner(String level) {
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

	/**
	 * El chat solo entiende **negritas** y viñetas «• »: los títulos pasan a negrita y el resto del Markdown que
	 * suelen añadir los modelos pequeños se normaliza (ChatFormat lo pinta).
	 */
	static String plain(String text) {
		return text.replace("__", "**").replaceAll("(?m)^\\s*[*-]\\s+", "• ")
			.replaceAll("(?m)^#++\\s*+(?!\\*\\*)(\\S.*)$", "**$1**").replaceAll("(?m)^#+\\s*", "")
			// Listas en línea («… 1. Sentadilla … 2. Puente …» o «… • Técnica …»): cada punto en su propia línea.
			.replaceAll("([^\\n])\\s+(\\d{1,2}\\.\\s+\\p{Lu})", "$1\n$2").replaceAll("([^\\n])\\s*•\\s*", "$1\n• ")
			.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
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
	public static Map<String, Object> extractJson(String text) {
		int start = text.indexOf('{');
		if (start < 0)
			return null;
		// Primer objeto con llaves equilibradas (ignorando las de dentro de cadenas): tolera llaves sobrantes al final.
		int depth = 0, end = -1;
		boolean string = false, escaped = false;
		for (int i = start; i < text.length() && end < 0; i++) {
			char c = text.charAt(i);
			if (string) {
				if (escaped)
					escaped = false;
				else if (c == '\\')
					escaped = true;
				else if (c == '"')
					string = false;
			} else if (c == '"')
				string = true;
			else if (c == '{')
				depth++;
			else if (c == '}' && --depth == 0)
				end = i;
		}
		if (end < 0)
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

	/** Un texto demasiado largo conserva su principio y su final, que es donde suele estar la pregunta. */
	static String middleCut(String value, int max) {
		if (value.length() <= max)
			return value;
		String gap = " […] ";
		int head = Math.max(0, (max - gap.length()) * 2 / 3), end = Math.max(0, max - gap.length() - head);
		return value.substring(0, head) + gap + value.substring(value.length() - end);
	}
}
