package com.energym.app.assistant;

import com.energym.app.R;
import com.energym.app.i18n.Txt;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MODELO del conocimiento del entrenador: pautas breves y contrastadas (ACSM, NSCA, ISSN y revisiones recientes) que
 * se añaden al prompt según el tema de la pregunta. Un modelo pequeño del móvil acierta mucho más cuando tiene delante
 * las cifras correctas que cuando tiene que recordarlas. Las fichas están en strings.xml (español e inglés), así que
 * también sirven al guiado local cuando el móvil no tiene IA.
 */
public final class CoachKnowledge {
	/** Tope de fichas por pregunta y de caracteres en total: el resto del prompt también necesita sitio. */
	static final int MAX_CARDS = 3, MAX_CHARS = 1400;

	public enum Topic {
		PAIN, LOWER_BACK, PROGRESSION, REST_SETS, HYPERTROPHY, STRENGTH, SPLIT, FAT_LOSS, RECOMP, SCALE, NUTRITION,
		MEAL_TIMING, WARMUP, MOBILITY, RECOVERY, SQUAT, BENCH, DEADLIFT, PULL, OVERHEAD, ARMS, GLUTES, CORE, PUSHUP,
		CARDIO, SHORT_TIME, OLDER, MOTIVATION, BEGINNER, HOME
	}

	/** Temas de técnica de un ejercicio: con ellos el guiado local prefiere la ficha del catálogo. */
	public static final Set<Topic> TECHNIQUE = Collections.unmodifiableSet(EnumSet.of(Topic.SQUAT, Topic.BENCH,
		Topic.DEADLIFT, Topic.PULL, Topic.OVERHEAD, Topic.ARMS, Topic.GLUTES, Topic.CORE, Topic.PUSHUP));

	private static final class Card {
		final Topic topic;
		final Pattern words;
		final int text;

		Card(Topic topic, int text, String words) {
			this.topic = topic;
			this.text = text;
			this.words = Pattern.compile("\\b(?:" + words + ")");
		}
	}

	/** En orden de prioridad: la seguridad primero, después lo que responde a la pregunta y al final la técnica. */
	private static final List<Card> CARDS = Arrays.asList(
		new Card(Topic.PAIN, R.string.kc_pain, "dolor|duele|duelen|molest|lesion|lesiona|tiron|pinchazo|diagnost|" +
			"tendinitis|tendon|pain|hurt|injur|ache|tendinitis"),
		new Card(Topic.LOWER_BACK, R.string.kc_lower_back, "lumbar|lumbares|espalda baja|rinones|lower back|low back"),
		new Card(Topic.PROGRESSION, R.string.kc_progression, "subo|subir|sube|progres|carga|estanc|no avanzo|no mejoro|sin mejorar|no progreso|" +
			"mas peso|increase|progress|stuck|plateau|add weight|heavier|overload"),
		new Card(Topic.REST_SETS, R.string.kc_rest_sets, "descanso entre|descansar entre|descanso\\w* entre series|" +
			"cuanto descanso|cuanto descanso|rest between|how long.{0,20}rest|rest time"),
		new Card(Topic.HYPERTROPHY, R.string.kc_hypertrophy, "musculo|hipertrofia|volumen|ganar masa|crecer|" +
			"cuantas series|series por|muscle|hypertrophy|volume|bulk|grow|how many sets"),
		new Card(Topic.STRENGTH, R.string.kc_strength, "fuerza|1rm|powerlifting|maxima|maximo|strength|powerlift|max"),
		new Card(Topic.SPLIT, R.string.kc_split, "dividir|division|rutina semanal|dias por semana|cuantos dias|frecuencia|" +
			"torso pierna|weider|split|how many days|frequency|weekly plan"),
		new Card(Topic.FAT_LOSS, R.string.kc_fat_loss, "perder grasa|adelgazar|bajar peso|perder peso|deficit|quemar|" +
			"tripa|barriga|definir|lose fat|fat loss|lose weight|cut|cutting|belly"),
		new Card(Topic.RECOMP, R.string.kc_recomp, "recomposic|recomp|ganar musculo y perder|perder grasa y ganar|" +
			"lose fat and (build|gain)|build muscle and lose"),
		new Card(Topic.SCALE, R.string.kc_scale, "bascula|no bajo|no pierdo peso|estancad\\w* (de|en el) peso|" +
			"peso estancado|scale|not losing weight|weight plateau"),
		new Card(Topic.NUTRITION, R.string.kc_nutrition, "proteina|comer|comida|dieta|caloria|kcal|carbohidrato|hidrato|" +
			"creatina|suplement|batido|protein|eat|food|diet|calorie|carb|creatine|supplement|shake|macro"),
		new Card(Topic.MEAL_TIMING, R.string.kc_meal_timing, "antes de entrenar|despues de entrenar|antes del entreno|" +
			"despues del entreno|pre ?entreno|post ?entreno|ayunas|before (training|workout)|after (training|workout)|" +
			"pre-?workout|post-?workout|fasted"),
		new Card(Topic.WARMUP, R.string.kc_warmup, "calent|calient|warm"),
		new Card(Topic.MOBILITY, R.string.kc_mobility, "movilidad|estirar|estiramiento|flexibilidad|mobility|stretch|flexib"),
		new Card(Topic.RECOVERY, R.string.kc_recovery, "descans|dormir|sueno|agujetas|cansad|recuper|fatiga|sobreentren|" +
			"sleep|rest day|recover|tired|fatigue|overtrain|doms|sore"),
		new Card(Topic.SQUAT, R.string.kc_squat, "sentadilla|squat"),
		new Card(Topic.BENCH, R.string.kc_bench, "press de banca|banca|bench|chest press|pecho|chest"),
		new Card(Topic.DEADLIFT, R.string.kc_deadlift, "peso muerto|deadlift|rumano|romanian|rdl"),
		new Card(Topic.PULL, R.string.kc_pull, "dominada|dominadas|jalon|remo|pull ?up|chin ?up|row|lat pulldown"),
		new Card(Topic.OVERHEAD, R.string.kc_overhead, "press militar|press de hombro|hombro|hombros|overhead|shoulder|" +
			"military press"),
		new Card(Topic.ARMS, R.string.kc_arms, "biceps|triceps|brazo|brazos|arm|arms|curl"),
		new Card(Topic.GLUTES, R.string.kc_glutes, "gluteo|gluteos|culo|hip thrust|glute|butt"),
		new Card(Topic.CORE, R.string.kc_core, "abdominal|abdominales|abs|core|plancha|tableta|six pack|plank"),
		new Card(Topic.PUSHUP, R.string.kc_pushup, "flexion|flexiones|push ?ups?|pushups?"),
		new Card(Topic.CARDIO, R.string.kc_cardio, "cardio|correr|carrera|running|resistencia|aerobic|zona 2|run|" +
			"endurance|jog|bike|bici"),
		new Card(Topic.SHORT_TIME, R.string.kc_short_time, "poco tiempo|sin tiempo|tengo \\d+ min|rapido|rapida|" +
			"superserie|short on time|no time|quick|superset"),
		new Card(Topic.OLDER, R.string.kc_older, "tengo [5-9]\\d|[5-9]\\d anos|edad avanzada|jubilad|older|over 50|" +
			"over 60|senior|elderly"),
		new Card(Topic.MOTIVATION, R.string.kc_motivation, "motiv|constan|pereza|habito|ganas|disciplina|no puedo|" +
			"motivation|consisten|lazy|habit|don.?t feel like"),
		new Card(Topic.BEGINNER, R.string.kc_beginner, "empez|principiante|novato|primera vez|nunca he|beginner|start|" +
			"new to|first time"),
		new Card(Topic.HOME, R.string.kc_home, "casa|sin material|peso corporal|calistenia|home|bodyweight|no equipment|" +
			"calisthenics"));

	private CoachKnowledge() {
	}

	private static List<Card> matching(String message) {
		String t = " " + normalize(message) + " ";
		List<Card> found = new ArrayList<>();
		for (Card c : CARDS)
			if (c.words.matcher(t).find())
				found.add(c);
		return found;
	}

	/** Temas detectados en el mensaje, de más a menos prioritarios. */
	static List<Topic> topics(String message) {
		List<Topic> topics = new ArrayList<>();
		for (Card c : matching(message))
			topics.add(c.topic);
		return topics;
	}

	/** Fichas para el prompt (vacío si la pregunta no toca ningún tema). */
	public static String forMessage(String message) {
		StringBuilder out = new StringBuilder();
		int cards = 0;
		for (Card c : matching(message)) {
			if (cards >= MAX_CARDS)
				break;
			String text = Txt.t(c.text);
			if (out.length() + text.length() + 3 > MAX_CHARS)
				continue;
			out.append("• ").append(text).append('\n');
			cards++;
		}
		return out.toString();
	}

	/**
	 * Respuesta del guiado local (móviles sin IA): las fichas de los dos temas más prioritarios, o vacío si la pregunta
	 * no toca ninguno.
	 */
	public static String answer(String message) {
		List<Card> found = matching(message);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < Math.min(2, found.size()); i++)
			out.append(out.length() == 0 ? "" : "\n\n").append(Txt.t(found.get(i).text));
		return out.toString();
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
	}
}
