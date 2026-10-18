package com.energym.app.assistant;

import java.text.Normalizer;
import java.util.*;

/**
 * MODELO de las sugerencias que aparecen bajo la última respuesta del entrenador (como en Gemini): hasta 3
 * preguntas de seguimiento elegidas por el tema de la conversación. Sin textos: la vista los toma de strings.xml.
 */
public final class FollowUps {
	public enum Topic {ROUTINE_TODAY, PROGRESSION, TECHNIQUE, ALTERNATIVE, WARMUP, DELOAD, NUTRITION, REST, MOTIVATION}

	public static final int MAX = 3;
	/** Sugerencias de la pantalla vacía, antes del primer mensaje. */
	public static final List<Topic> STARTERS = Collections.unmodifiableList(Arrays.asList(Topic.ROUTINE_TODAY,
		Topic.PROGRESSION, Topic.NUTRITION, Topic.MOTIVATION));

	private FollowUps() {
	}

	/**
	 * @param user    último mensaje del cliente (puede ser null).
	 * @param reply   última respuesta del entrenador.
	 * @param routine verdadero si esa respuesta trae una rutina propuesta.
	 */
	public static List<Topic> suggest(String user, String reply, boolean routine) {
		String u = normalize(user), r = normalize(reply), all = u + " " + r;
		LinkedHashSet<Topic> result = new LinkedHashSet<>();
		if (has(all, "dolor", "molest", "lesion", "duele"))
			add(result, Topic.ALTERNATIVE, Topic.REST);
		if (routine || has(all, "rutina", "entreno de hoy", "sesion"))
			add(result, Topic.WARMUP, Topic.ALTERNATIVE);
		if (has(all, "estanc", "descarga", "no avanzo", "no mejoro"))
			add(result, Topic.DELOAD, Topic.TECHNIQUE);
		if (has(all, "kg", "peso", "carga", "rpe", "subir", "progres"))
			add(result, Topic.PROGRESSION, Topic.DELOAD);
		if (has(all, "tecnica", "postura", "espalda", "rodilla", "ejecucion"))
			add(result, Topic.TECHNIQUE, Topic.ALTERNATIVE);
		if (has(all, "comida", "comer", "proteina", "dieta", "kcal", "caloria", "nutri"))
			add(result, Topic.NUTRITION, Topic.REST);
		if (has(all, "descans", "dormir", "sueno", "cansad", "agujetas"))
			add(result, Topic.REST, Topic.MOTIVATION);
		add(result, Topic.ROUTINE_TODAY, Topic.PROGRESSION, Topic.MOTIVATION);
		// No sugerir justo lo que acaba de preguntar: la respuesta ya está arriba.
		Topic asked = asked(u);
		if (asked != null && !routine)
			result.remove(asked);
		if (routine)
			result.remove(Topic.ROUTINE_TODAY);
		List<Topic> list = new ArrayList<>(result);
		return list.size() > MAX ? new ArrayList<>(list.subList(0, MAX)) : list;
	}

	private static Topic asked(String user) {
		if (has(user, "rutina", "que hago hoy", "entreno de hoy"))
			return Topic.ROUTINE_TODAY;
		if (has(user, "subo", "subir peso", "progres"))
			return Topic.PROGRESSION;
		if (has(user, "calent"))
			return Topic.WARMUP;
		if (has(user, "descarga"))
			return Topic.DELOAD;
		if (has(user, "tecnica"))
			return Topic.TECHNIQUE;
		if (has(user, "comer", "dieta", "proteina"))
			return Topic.NUTRITION;
		return null;
	}

	private static void add(Set<Topic> set, Topic... topics) {
		set.addAll(Arrays.asList(topics));
	}

	private static boolean has(String text, String... words) {
		for (String w : words)
			if (text.contains(w))
				return true;
		return false;
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
	}
}
