package com.energym.app.assistant;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MODELO de las sugerencias que aparecen bajo la última respuesta del entrenador (como en Gemini): hasta 3
 * preguntas de seguimiento que siguen el hilo de la conversación. Primero profundizan en el tema que se está
 * tratando, después dan el siguiente paso natural, y nunca repiten algo que ya se haya preguntado en el chat.
 * Sin textos: la vista los toma de strings.xml ({@code followup_<tema>}).
 */
public final class FollowUps {
	public enum Topic {
		ROUTINE_TODAY, PROGRESSION, TECHNIQUE, ALTERNATIVE, WARMUP, DELOAD, NUTRITION, REST, MOTIVATION, REST_SETS,
		SETS_PER_MUSCLE, SUPERSETS, PROTEIN, DEFICIT, CARDIO, SLEEP, SPLIT_DAYS, PRE_WORKOUT, MOBILITY, HOME
	}

	public static final int MAX = 3;
	/** Sugerencias de la pantalla vacía, antes del primer mensaje. */
	public static final List<Topic> STARTERS = Collections.unmodifiableList(Arrays.asList(Topic.ROUTINE_TODAY,
		Topic.PROGRESSION, Topic.NUTRITION, Topic.MOTIVATION));
	/** Cuando la charla no toca ningún tema concreto («hola», «gracias»). */
	private static final List<Topic> DEFAULTS = Arrays.asList(Topic.ROUTINE_TODAY, Topic.PROGRESSION,
		Topic.MOTIVATION);

	/** Lo que suele preguntarse después de cada tema del entrenador, de más a menos natural. */
	private static final Map<CoachKnowledge.Topic, List<Topic>> NEXT = new EnumMap<>(CoachKnowledge.Topic.class);

	static {
		next(CoachKnowledge.Topic.PAIN, Topic.ALTERNATIVE, Topic.REST, Topic.TECHNIQUE);
		next(CoachKnowledge.Topic.LOWER_BACK, Topic.TECHNIQUE, Topic.ALTERNATIVE, Topic.MOBILITY);
		next(CoachKnowledge.Topic.PROGRESSION, Topic.DELOAD, Topic.SETS_PER_MUSCLE, Topic.TECHNIQUE);
		next(CoachKnowledge.Topic.REST_SETS, Topic.SETS_PER_MUSCLE, Topic.SUPERSETS, Topic.PROGRESSION);
		next(CoachKnowledge.Topic.HYPERTROPHY, Topic.SETS_PER_MUSCLE, Topic.PROTEIN, Topic.PROGRESSION);
		next(CoachKnowledge.Topic.STRENGTH, Topic.PROGRESSION, Topic.REST_SETS, Topic.DELOAD);
		next(CoachKnowledge.Topic.SPLIT, Topic.ROUTINE_TODAY, Topic.SETS_PER_MUSCLE, Topic.REST);
		next(CoachKnowledge.Topic.FAT_LOSS, Topic.DEFICIT, Topic.CARDIO, Topic.PROTEIN);
		next(CoachKnowledge.Topic.RECOMP, Topic.PROTEIN, Topic.DEFICIT, Topic.PROGRESSION);
		next(CoachKnowledge.Topic.SCALE, Topic.DEFICIT, Topic.SLEEP, Topic.CARDIO);
		next(CoachKnowledge.Topic.NUTRITION, Topic.PROTEIN, Topic.PRE_WORKOUT, Topic.NUTRITION);
		next(CoachKnowledge.Topic.MEAL_TIMING, Topic.PROTEIN, Topic.NUTRITION, Topic.PRE_WORKOUT);
		next(CoachKnowledge.Topic.WARMUP, Topic.MOBILITY, Topic.ROUTINE_TODAY, Topic.TECHNIQUE);
		next(CoachKnowledge.Topic.MOBILITY, Topic.WARMUP, Topic.REST, Topic.TECHNIQUE);
		next(CoachKnowledge.Topic.RECOVERY, Topic.REST, Topic.SLEEP, Topic.MOTIVATION);
		for (CoachKnowledge.Topic exercise : CoachKnowledge.TECHNIQUE)
			next(exercise, Topic.TECHNIQUE, Topic.ALTERNATIVE, Topic.PROGRESSION, Topic.HOME);
		next(CoachKnowledge.Topic.CARDIO, Topic.SPLIT_DAYS, Topic.DEFICIT, Topic.WARMUP);
		next(CoachKnowledge.Topic.SHORT_TIME, Topic.SUPERSETS, Topic.ROUTINE_TODAY, Topic.SPLIT_DAYS);
		next(CoachKnowledge.Topic.OLDER, Topic.WARMUP, Topic.MOBILITY, Topic.PROGRESSION);
		next(CoachKnowledge.Topic.MOTIVATION, Topic.ROUTINE_TODAY, Topic.SPLIT_DAYS, Topic.REST);
		next(CoachKnowledge.Topic.BEGINNER, Topic.SPLIT_DAYS, Topic.TECHNIQUE, Topic.ROUTINE_TODAY);
		next(CoachKnowledge.Topic.HOME, Topic.ROUTINE_TODAY, Topic.PROGRESSION, Topic.SETS_PER_MUSCLE);
	}

	/**
	 * Cómo se reconoce que una pregunta ya está hecha, con palabras propias o con el texto de la sugerencia (que
	 * también llega como mensaje del cliente al tocarla). Español e inglés, sin tildes.
	 */
	private static final Map<Topic, Pattern> ASKED = new EnumMap<>(Topic.class);

	static {
		asked(Topic.ROUTINE_TODAY, "rutina|que entreno|que hago hoy|entreno de hoy|routine|train today|" +
			"workout for today|what should i do");
		asked(Topic.PROGRESSION, "subo|subir (de )?peso|progres|add weight|increase");
		asked(Topic.TECHNIQUE, "tecnica|errores|technique|form\\b|mistakes");
		asked(Topic.ALTERNATIVE, "alternativa|sustitu|en vez de|alternative|instead of|replace");
		asked(Topic.WARMUP, "calent|calient|warm");
		asked(Topic.DELOAD, "descarga|deload");
		asked(Topic.NUTRITION, "como para|comer para|dieta|eat for|diet\\b");
		asked(Topic.REST, "dias? de descanso|debo descansar|rest days?\\b|how much should i rest");
		asked(Topic.MOTIVATION, "motiv");
		asked(Topic.REST_SETS, "entre series|descanso entre|between sets|rest between");
		asked(Topic.SETS_PER_MUSCLE, "cuantas series|series por|how many sets|sets per");
		asked(Topic.SUPERSETS, "superserie|superset");
		asked(Topic.PROTEIN, "proteina|protein");
		asked(Topic.DEFICIT, "deficit");
		asked(Topic.CARDIO, "cardio");
		asked(Topic.SLEEP, "dormir|sueno|sleep");
		asked(Topic.SPLIT_DAYS, "dias (a|por) la semana|dias por semana|cuantos dias|days a week|how many days");
		asked(Topic.PRE_WORKOUT, "antes de entrenar|antes del entreno|pre ?entreno|before (a )?(training|workout)|" +
			"pre-?workout");
		asked(Topic.MOBILITY, "estira|movilidad|stretch|mobility");
		asked(Topic.HOME, "en casa|at home");
	}

	private FollowUps() {
	}

	/** Atajo con solo el último mensaje del cliente (puede ser null). */
	public static List<Topic> suggest(String user, String reply, boolean routine) {
		return suggest(user == null ? Collections.emptyList() : Collections.singletonList(user), reply, routine);
	}

	/**
	 * @param users   mensajes del cliente en esta conversación, del primero al último.
	 * @param reply   última respuesta del entrenador.
	 * @param routine verdadero si esa respuesta trae una rutina propuesta.
	 */
	public static List<Topic> suggest(List<String> users, String reply, boolean routine) {
		LinkedHashSet<Topic> result = new LinkedHashSet<>();
		// Con una rutina recién propuesta, lo natural es prepararla o cambiar algo de ella.
		if (routine)
			add(result, Topic.WARMUP, Topic.ALTERNATIVE);
		for (CoachKnowledge.Topic t : subject(users, reply))
			result.addAll(NEXT.getOrDefault(t, Collections.emptyList()));
		result.addAll(DEFAULTS);
		// Nada que ya se haya preguntado en la conversación: la respuesta ya está arriba. Tras una rutina nueva,
		// calentar y pedir alternativas vuelven a tener sentido aunque se preguntaran para otra rutina.
		for (String u : users)
			for (Topic t : asked(u))
				if (!routine || (t != Topic.WARMUP && t != Topic.ALTERNATIVE))
					result.remove(t);
		if (routine)
			result.remove(Topic.ROUTINE_TODAY);
		List<Topic> list = new ArrayList<>(result);
		return list.size() > MAX ? new ArrayList<>(list.subList(0, MAX)) : list;
	}

	/**
	 * El tema que se está tratando: el del último mensaje; si es una frase corta sin tema («vale», «gracias»), el de
	 * los mensajes anteriores, para seguir el hilo; y si la charla no tiene ninguno, el de la respuesta.
	 */
	private static List<CoachKnowledge.Topic> subject(List<String> users, String reply) {
		for (int i = users.size() - 1; i >= 0; i--) {
			List<CoachKnowledge.Topic> found = CoachKnowledge.topics(users.get(i) == null ? "" : users.get(i));
			if (!found.isEmpty())
				return found;
		}
		List<CoachKnowledge.Topic> fromReply = CoachKnowledge.topics(reply == null ? "" : reply);
		return fromReply.isEmpty() ? fromReply : fromReply.subList(0, 1);
	}

	/** Preguntas de la lista que este mensaje ya hace. */
	static Set<Topic> asked(String message) {
		String text = " " + normalize(message) + " ";
		Set<Topic> found = EnumSet.noneOf(Topic.class);
		for (Map.Entry<Topic, Pattern> e : ASKED.entrySet())
			if (e.getValue().matcher(text).find())
				found.add(e.getKey());
		return found;
	}

	private static void next(CoachKnowledge.Topic topic, Topic... followUps) {
		NEXT.put(topic, Arrays.asList(followUps));
	}

	private static void asked(Topic topic, String words) {
		ASKED.put(topic, Pattern.compile("\\b(?:" + words + ")"));
	}

	private static void add(Set<Topic> set, Topic... topics) {
		set.addAll(Arrays.asList(topics));
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
	}
}
