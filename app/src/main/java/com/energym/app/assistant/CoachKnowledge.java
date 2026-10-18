package com.energym.app.assistant;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MODELO del conocimiento del entrenador: pautas breves y contrastadas (ACSM, NSCA, ISSN y revisiones recientes) que
 * se añaden al prompt según el tema de la pregunta. Un modelo pequeño del móvil acierta mucho más cuando tiene delante
 * las cifras correctas que cuando tiene que recordarlas. Las fichas van en español porque el prompt lo está; el
 * modelo responde en el idioma del cliente.
 */
public final class CoachKnowledge {
	/** Tope de fichas por pregunta y de caracteres en total: el resto del prompt también necesita sitio. */
	static final int MAX_CARDS = 3, MAX_CHARS = 1300;

	public enum Topic {
		PROGRESSION, HYPERTROPHY, STRENGTH, FAT_LOSS, NUTRITION, WARMUP, RECOVERY, PAIN, SQUAT, BENCH, DEADLIFT,
		PULL, OVERHEAD, CARDIO, MOTIVATION, BEGINNER, HOME
	}

	private static final class Card {
		final Topic topic;
		final Pattern words;
		final String text;

		Card(Topic topic, String words, String text) {
			this.topic = topic;
			this.words = Pattern.compile("\\b(?:" + words + ")");
			this.text = text;
		}
	}

	private static final List<Card> CARDS = Arrays.asList(
		new Card(Topic.PAIN, "dolor|duele|duelen|molest|lesion|lesiona|tiron|pinchazo|pain|hurt|injur|sore knee|ache",
			"Dolor: si es agudo, punzante o con hinchazón, para y no entrenes esa zona. Una molestia leve (≤3/10) " +
				"permite entrenar bajando carga o rango y cambiando a una variante que no moleste. Si dura más de 1–2 " +
				"semanas, empeora o hay hormigueo o pérdida de fuerza, que lo valore un profesional sanitario. No diagnostiques."),
		new Card(Topic.PROGRESSION, "subo|subir|sube|progres|peso|carga|estanc|no avanzo|no mejoro|increase|progress|stuck|" +
			"plateau|add weight|heavier",
			"Progresión (doble progresión): trabaja en un rango (p. ej. 8–12). Cuando hagas el tope en todas las series " +
				"con buena técnica y RPE ≤8, sube la carga mínima (2,5 kg barra/máquina, 1–2 kg mancuernas) y vuelve al " +
				"mínimo del rango. Si no, suma repeticiones. Tras 3 sesiones sin mejorar: descarga (−10 % una semana) o " +
				"cambia de variante o rango. Esfuerzo ideal RPE 7–8 (te quedan 2–3 repeticiones)."),
		new Card(Topic.HYPERTROPHY, "musculo|hipertrofia|volumen|ganar masa|crecer|definicion muscular|series por|" +
			"muscle|hypertrophy|volume|bulk|grow|size",
			"Ganar músculo: 10–20 series efectivas por grupo muscular y semana, repartidas en 2 sesiones o más. " +
				"Rango 6–12 repeticiones (de 5 a 30 sirve si te acercas al fallo), a 1–3 repeticiones del fallo. " +
				"Descansos de 1,5–3 min en básicos. Ligero superávit (+200–300 kcal) y proteína 1,6–2,2 g/kg."),
		new Card(Topic.STRENGTH, "fuerza|1rm|rm|powerlifting|maxima|maximo|strength|max|powerlift",
			"Fuerza máxima: básicos a 1–6 repeticiones con 80–90 % del 1RM, 3–5 series y 3–5 min de descanso. " +
				"La técnica manda: deja 1–2 repeticiones en reserva casi siempre. 1RM estimado ≈ peso × (1 + reps/30). " +
				"Incluye trabajo accesorio de 8–12 repeticiones para los puntos débiles."),
		new Card(Topic.FAT_LOSS, "perder grasa|adelgazar|bajar peso|perder peso|deficit|quemar|tripa|barriga|lose fat|" +
			"fat loss|lose weight|cut|cutting|belly",
			"Perder grasa: déficit de 300–500 kcal/día para bajar un 0,5–1 % del peso por semana. Proteína alta " +
				"(1,8–2,2 g/kg) y seguir entrenando fuerza para no perder músculo. 7.000–10.000 pasos al día ayudan más " +
				"que el cardio extenuante. No se quema grasa localizada haciendo abdominales."),
		new Card(Topic.NUTRITION, "proteina|comer|comida|dieta|caloria|kcal|carbohidrato|hidrato|grasa|creatina|suplement|" +
			"batido|protein|eat|food|diet|calorie|carb|creatine|supplement|shake|macro",
			"Nutrición: proteína 1,6–2,2 g/kg al día en 3–5 tomas de 20–40 g. Carbohidratos según el volumen de " +
				"entreno (3–5 g/kg si entrenas fuerte). Creatina monohidrato 3–5 g/día es el suplemento con más " +
				"evidencia; el resto rara vez hace falta. Agua: ~35 ml/kg, más si sudas mucho."),
		new Card(Topic.WARMUP, "calent|calient|warm",
			"Calentamiento: 5 min de actividad suave y movilidad de las articulaciones que vas a usar. Luego series de " +
				"aproximación del primer ejercicio: ~40 % × 8, 60 % × 5, 80 % × 3 del peso de trabajo. Los estiramientos " +
				"estáticos largos mejor después o en otro momento."),
		new Card(Topic.RECOVERY, "descans|dormir|sueno|agujetas|cansad|recuper|fatiga|sobreentren|sleep|rest|recover|" +
			"tired|fatigue|overtrain|doms",
			"Recuperación: duerme 7–9 h. Deja 48–72 h antes de volver a cargar fuerte el mismo grupo. Las agujetas no " +
				"indican un buen entreno. Haz una semana de descarga (menos series o −10 % de carga) cada 4–8 semanas o " +
				"si notas cansancio persistente, peor rendimiento o mal sueño."),
		new Card(Topic.SQUAT, "sentadilla|squat",
			"Sentadilla: pies a la anchura de los hombros y algo abiertos, rodillas siguiendo la línea de los pies, " +
				"tronco firme (coge aire y aprieta el abdomen), baja hasta donde mantengas la espalda neutra y sube " +
				"empujando el suelo. Errores típicos: rodillas hacia dentro, talones que se levantan, redondear la espalda."),
		new Card(Topic.BENCH, "press de banca|banca|bench|chest press|pecho",
			"Press de banca: escápulas juntas y abajo, ligero arco, pies firmes, agarre algo más ancho que los hombros, " +
				"baja controlado a la parte baja del pecho con los codos a ~45–70° y empuja en línea recta. Usa " +
				"seguros o compañero si vas pesado."),
		new Card(Topic.DEADLIFT, "peso muerto|deadlift|rumano|romanian|rdl",
			"Peso muerto: barra sobre el medio del pie, cadera atrás, espalda neutra y dorsales activos, la barra pegada " +
				"a las piernas todo el recorrido. Empuja el suelo con las piernas y termina con la cadera, sin hiperextender. " +
				"Si la espalda se redondea, baja la carga."),
		new Card(Topic.PULL, "dominada|dominadas|jalon|remo|espalda|pull up|pullup|chin up|row|lat pulldown|back",
			"Tracciones (dominadas, jalón, remo): empieza bajando y juntando las escápulas, tira con los codos hacia la " +
				"cadera y controla la bajada. Para la primera dominada: negativas lentas (3–5 s), dominadas con banda y " +
				"jalón pesado 3 veces por semana."),
		new Card(Topic.OVERHEAD, "press militar|press de hombro|hombro|hombros|overhead|shoulder|military press",
			"Press de hombros: glúteos y abdomen apretados para no arquear la zona lumbar, la barra o mancuernas suben " +
				"en línea con la cabeza (la cabeza se aparta y vuelve). Elevaciones laterales ligeras, 12–20 repeticiones, " +
				"sin balancear."),
		new Card(Topic.CARDIO, "cardio|correr|carrera|running|resistencia|aerobic|zona 2|run|endurance|jog|bike|bici",
			"Cardio: unas 80 % de las sesiones suaves (zona 2: puedes hablar) y 20 % intensas (intervalos). Sube el " +
				"volumen semanal ≤10 %. Si lo combinas con fuerza, separa los entrenos intensos de pierna y carrera al " +
				"menos 6 h. Mínimo recomendado: 150 min/semana moderados."),
		new Card(Topic.MOTIVATION, "motiv|constan|pereza|habito|ganas|disciplina|no puedo|motivation|consisten|lazy|habit",
			"Constancia: mejor un plan mínimo que cumplas (2–3 sesiones de 30–45 min) que uno ideal que abandones. " +
				"Entrena siempre a la misma hora, deja la ropa preparada y apunta cada sesión. Si fallas un día, vuelve " +
				"en el siguiente sin compensar. Celebra progresos pequeños."),
		new Card(Topic.BEGINNER, "empez|principiante|novato|primera vez|nunca he|beginner|start|new to|first time",
			"Principiantes: 2–3 sesiones de cuerpo completo por semana con ejercicios básicos (sentadilla, empuje, " +
				"tracción, bisagra de cadera, core). 2–3 series de 8–12 a RPE 6–7 aprendiendo la técnica. Sube carga " +
				"cada semana mientras puedas: al principio se progresa muy rápido."),
		new Card(Topic.HOME, "casa|sin material|peso corporal|calistenia|home|bodyweight|no equipment|calisthenics",
			"En casa sin material: progresa haciendo el ejercicio más difícil (flexiones inclinadas → normales → " +
				"pies elevados), a una pierna, más lento (3 s bajando) o con pausas, y acércate al fallo (1–2 repeticiones " +
				"en reserva). Una mochila con libros sirve de lastre."));

	private CoachKnowledge() {
	}

	/** Temas detectados en el mensaje, de más a menos relevantes. */
	static List<Topic> topics(String message) {
		String t = " " + normalize(message) + " ";
		List<Topic> found = new ArrayList<>();
		for (Card c : CARDS)
			if (c.words.matcher(t).find())
				found.add(c.topic);
		return found;
	}

	/** Fichas para el prompt (vacío si la pregunta no toca ningún tema). */
	public static String forMessage(String message) {
		StringBuilder out = new StringBuilder();
		int cards = 0;
		String t = " " + normalize(message) + " ";
		for (Card c : CARDS) {
			if (cards >= MAX_CARDS)
				break;
			if (!c.words.matcher(t).find() || out.length() + c.text.length() + 3 > MAX_CHARS)
				continue;
			out.append("• ").append(c.text).append('\n');
			cards++;
		}
		return out.toString();
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
	}
}
