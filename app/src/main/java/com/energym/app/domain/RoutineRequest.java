package com.energym.app.domain;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * MODELO de lo que pide la persona para la rutina de hoy («piernas y glúteos, algo suave, 4 ejercicios»).
 * El catálogo no etiqueta músculos: las zonas de cada ejercicio se deducen de su ID y nombre. Con esto la rutina
 * se elige por lo pedido y varía entre peticiones, en vez de tomar siempre los primeros compatibles del catálogo.
 */
public final class RoutineRequest {
	public enum Area {
		LEGS("piernas", R.string.area_legs), GLUTES("glúteos", R.string.area_glutes), CHEST("pecho", R.string.area_chest),
		BACK("espalda", R.string.area_back), SHOULDERS("hombros", R.string.area_shoulders), ARMS("brazos", R.string.area_arms),
		CORE("core", R.string.area_core), CARDIO("cardio", R.string.area_cardio), MOBILITY("movilidad", R.string.area_mobility);

		/** Nombre en español: se guarda en los ejercicios propios y va a la IA; no se traduce. */
		public final String label;
		private final int text;

		Area(String label, int text) {
			this.label = label;
			this.text = text;
		}

		/** Nombre para mostrar en el idioma del móvil. */
		public String display() {
			return Txt.t(text);
		}
	}

	public enum Intensity {EASY, NORMAL, HARD}

	/** Reparto equilibrado cuando no se pide ninguna zona concreta. */
	private static final List<Area> BALANCED = Arrays.asList(Area.LEGS, Area.CHEST, Area.BACK, Area.CORE, Area.GLUTES,
		Area.SHOULDERS, Area.ARMS);
	private static final Set<Area> UPPER = EnumSet.of(Area.CHEST, Area.BACK, Area.SHOULDERS, Area.ARMS);
	private static final Set<Area> LOWER = EnumSet.of(Area.LEGS, Area.GLUTES);

	public final Set<Area> areas, excluded;
	public final Intensity intensity;
	/** Número de ejercicios pedido (0 = sin indicar). */
	public final int count;

	private RoutineRequest(Set<Area> areas, Set<Area> excluded, Intensity intensity, int count) {
		this.areas = Collections.unmodifiableSet(areas);
		this.excluded = Collections.unmodifiableSet(excluded);
		this.intensity = intensity;
		this.count = count;
	}

	/** Petición para un día concreto de un plan (p. ej. Empuje = pecho y hombros). */
	public static RoutineRequest of(Set<Area> areas, Intensity intensity, int count) {
		Set<Area> copy = EnumSet.noneOf(Area.class);
		copy.addAll(areas);
		return new RoutineRequest(copy, EnumSet.noneOf(Area.class), intensity, count);
	}

	public static RoutineRequest none() {
		return new RoutineRequest(EnumSet.noneOf(Area.class), EnumSet.noneOf(Area.class), Intensity.NORMAL, 0);
	}

	public boolean isEmpty() {
		return areas.isEmpty() && excluded.isEmpty() && intensity == Intensity.NORMAL && count == 0;
	}

	public static RoutineRequest parse(String message) {
		String t = " " + normalize(message) + " ";
		Set<Area> areas = EnumSet.noneOf(Area.class), excluded = EnumSet.noneOf(Area.class);
		Map<String, Set<Area>> words = new LinkedHashMap<>();
		words.put("tren superior|parte de arriba|torso", UPPER);
		words.put("tren inferior|parte de abajo", LOWER);
		words.put("piernas?|cuadriceps|femorales?|isquios?\\w*|gemelos?|sentadillas?", EnumSet.of(Area.LEGS));
		words.put("gluteos?|culo|pompis", EnumSet.of(Area.GLUTES));
		words.put("pecho|pectorales?", EnumSet.of(Area.CHEST));
		words.put("espalda|dorsales?|lumbares?", EnumSet.of(Area.BACK));
		words.put("hombros?|deltoides", EnumSet.of(Area.SHOULDERS));
		words.put("brazos?|biceps|triceps|antebrazos?", EnumSet.of(Area.ARMS));
		words.put("abdomen|abdominales?|abs|core|tripa|barriga|oblicuos?|zona media", EnumSet.of(Area.CORE));
		words.put("cardio|aerobico|correr|hiit|quemar|resistencia", EnumSet.of(Area.CARDIO));
		words.put("movilidad|estiramientos?|estirar|flexibilidad|yoga", EnumSet.of(Area.MOBILITY));
		for (Map.Entry<String, Set<Area>> w : words.entrySet()) {
			Matcher m = Pattern.compile("(?:\\b(sin|no|nada de|evita\\w*)\\s+(?:\\w+\\s+){0,2})?\\b(?:" + w.getKey() + ")\\b")
				.matcher(t);
			while (m.find())
				(m.group(1) != null ? excluded : areas).addAll(w.getValue());
		}
		if (t.matches(".*\\b(cuerpo completo|todo el cuerpo|full ?body)\\b.*"))
			areas.addAll(EnumSet.of(Area.LEGS, Area.CHEST, Area.BACK, Area.CORE));
		areas.removeAll(excluded);
		Intensity intensity = t.matches(".*\\b(suave|ligera|ligero|facil|tranquil\\w*|recuperacion|relajad\\w*|" +
			"cansad\\w*|principiante)\\b.*") ? Intensity.EASY :
			t.matches(".*\\b(intens\\w*|dura|duro|fuerte|exigente|canera|reto|a tope|hiit)\\b.*") ? Intensity.HARD :
				Intensity.NORMAL;
		int count = 0;
		String[] numbers = {"tres", "cuatro", "cinco", "seis", "siete", "ocho"};
		for (int i = 0; i < numbers.length; i++)
			t = t.replaceAll("\\b" + numbers[i] + "\\b", Integer.toString(i + 3));
		Matcher n = Pattern.compile("\\b([0-9]{1,2})\\s+ejercicios?\\b").matcher(t);
		if (n.find())
			count = Math.max(3, Math.min(8, Integer.parseInt(n.group(1))));
		return new RoutineRequest(areas, excluded, intensity, count);
	}

	/** Zonas que trabaja un ejercicio, deducidas de su tipo, ID y nombre. */
	public static Set<Area> areas(Exercise e) {
		Set<Area> a = EnumSet.noneOf(Area.class);
		// Ejercicio propio: su grupo muscular es el que eligió la persona al crearlo.
		if (e.getId().startsWith("custom-") && e.getDescription().startsWith("Grupo muscular: ")) {
			String label = e.getDescription().substring("Grupo muscular: ".length()).split("\\.")[0];
			for (Area area : Area.values())
				if (area.label.equals(label)) {
					a.add(area);
					return a;
				}
		}
		if (e.getMetricType() == MetricType.MOBILITY) {
			a.add(Area.MOBILITY);
			return a;
		}
		if (e.getMetricType() == MetricType.ENDURANCE || e.getMetricType() == MetricType.SPORT_PRACTICE) {
			a.add(Area.CARDIO);
			return a;
		}
		String s = " " + normalize(e.getId().replace('-', ' ') + " " + e.getName()) + " ";
		boolean legCurl = has(s, "leg curl|hamstring curl|femoral");
		boolean dips = has(s, "dips|fondos") && !has(s, "plank|plancha");
		if (has(s, "squat|sentadilla|lunge|zancada|leg press|prensa|leg extension|cuadriceps|calf|gemelo|talones|" +
			"step up|subidas|wall sit|hack|pistol|cossack|nordic|nordico|box jump|escalon") || legCurl)
			a.add(Area.LEGS);
		if (has(s, "glute|gluteo|hip thrust|bridge|puente|donkey|burro|fire hydrant|clamshell|almeja|abduction|" +
			"abduccion|frog pump|pull through|romanian|rumano|rdl|deadlift|peso muerto|good morning|buenos dias|sumo|" +
			"lateral walk|pasos laterales|swing|curtsy|cruzada|hip airplane") ||
			(has(s, "kickback|patada") && !has(s, "triceps")))
			a.add(Area.GLUTES);
		if (has(s, "pushup|flexion|bench press|press de banca|chest|pecho|fly|aperturas con mancuernas|aperturas en polea|" +
			"pec deck|contractor de pecho|crossover|cruce de poleas|floor press|press en el suelo|" +
			"incline press|press inclinado") && !has(s, "reverse|inverso|pull apart|flexion sentada") || dips)
			a.add(Area.CHEST);
		if ((has(s, "row|remo|pull up|dominada|pulldown|jalon|deadlift|peso muerto|superman|face pull|reverse fly|pajaros|" +
			"pull apart|aperturas con banda|pullover|back extension|hiperextensiones|dead hang|colgado|shrug|encogimientos de|" +
			"muscle up|reverse pec") && !has(s, "upright|menton|ergometro|rowing machine")))
			a.add(Area.BACK);
		if (has(s, "overhead press|press militar|shoulder press|press de hombros|arnold|lateral raise|elevaciones laterales|" +
			"elevacion lateral|front raise|elevacion frontal|upright|menton|pike|pica|handstand|pino|face pull|" +
			"reverse fly|pajaros|push press|landmine|clean press|thruster|shoulder taps|toques de hombro"))
			a.add(Area.SHOULDERS);
		if ((has(s, "curl|biceps|triceps|skull|press frances|close grip|agarre estrecho|press cerrado|diamond|diamante|" +
			"pushdown|hammer|martillo|zottman|preacher|predicador|chin up|supinas|wrist|muneca") && !legCurl) || dips)
			a.add(Area.ARMS);
		if (has(s, "plank|plancha|crunch|abdominal|encogimiento abdominal|dead bug|bird dog|hollow|russian|giro ruso|" +
			"mountain|escaladores|v up|flutter|scissor|tijeras|heel touches|toques de talon|toe touch|leg raise|" +
			"elevacion de piernas|knee raise|elevacion de rodillas|l sit|pallof|woodchop|lenador|side bend|" +
			"inclinacion lateral|stir the pot|bear crawl|gateo|suitcase|maleta|inchworm|gusano|copenhague|copenhagen"))
			a.add(Area.CORE);
		if (has(s, "burpee|jump|salto|mountain|escaladores|plank jacks|skater|patinador|tuck"))
			a.add(Area.CARDIO);
		return a;
	}

	/**
	 * Candidatos ordenados por afinidad con lo pedido; a igual afinidad el orden cambia con la semilla para que dos
	 * peticiones iguales no den siempre la misma rutina.
	 */
	public List<Exercise> rank(List<Exercise> compatible, long seed) {
		List<Exercise> result = new ArrayList<>();
		for (Exercise e : compatible)
			if (Collections.disjoint(areas(e), excluded) || areas.containsAll(areas(e)))
				result.add(e);
		result.sort(Comparator.comparingInt((Exercise e) -> -score(e)).thenComparingLong(e -> mix(seed, e.getId())));
		return result;
	}

	private int score(Exercise e) {
		Set<Area> mine = areas(e);
		int score = 0;
		for (Area a : mine)
			if (areas.contains(a))
				score += 10;
		if (areas.isEmpty() && !mine.contains(Area.MOBILITY) && !mine.contains(Area.CARDIO))
			score += 5;
		// Con intensidad suave pesa menos lo explosivo; con intensa, más.
		if (mine.contains(Area.CARDIO) && !areas.contains(Area.CARDIO))
			score += intensity == Intensity.HARD ? 2 : intensity == Intensity.EASY ? -4 : -1;
		return score;
	}

	/**
	 * Selección equilibrada: un calentamiento de movilidad si cabe, luego un ejercicio por zona pedida (o de cuerpo
	 * completo) por turnos, y la parte de cardio al final.
	 */
	public List<Exercise> compose(List<Exercise> compatible, long seed, int max) {
		List<Exercise> ranked = rank(compatible, seed);
		List<Exercise> chosen = new ArrayList<>();
		boolean onlyMobility = areas.equals(EnumSet.of(Area.MOBILITY));
		boolean onlyCardio = areas.equals(EnumSet.of(Area.CARDIO));
		if (onlyMobility || onlyCardio) {
			for (Exercise e : ranked)
				if (areas(e).contains(onlyMobility ? Area.MOBILITY : Area.CARDIO) && chosen.size() < max)
					chosen.add(e);
			return order(chosen);
		}
		if (max >= 4 && !areas.contains(Area.MOBILITY))
			for (Exercise e : ranked)
				if (areas(e).contains(Area.MOBILITY)) {
					chosen.add(e);
					break;
				}
		List<Area> targets = new ArrayList<>();
		for (Area a : areas.isEmpty() ? BALANCED : new ArrayList<>(areas))
			if (a != Area.CARDIO && !excluded.contains(a))
				targets.add(a);
		boolean progress = true;
		while (chosen.size() < max && progress) {
			progress = false;
			for (Area target : targets) {
				if (chosen.size() >= max)
					break;
				for (Exercise e : ranked)
					if (!chosen.contains(e) && areas(e).contains(target) && (target == Area.MOBILITY ||
						!areas(e).contains(Area.MOBILITY)) && e.getMetricType() != MetricType.ENDURANCE &&
						e.getMetricType() != MetricType.SPORT_PRACTICE) {
						chosen.add(e);
						progress = true;
						break;
					}
			}
		}
		if (areas.contains(Area.CARDIO))
			for (Exercise e : ranked)
				if (!chosen.contains(e) && areas(e).contains(Area.CARDIO)) {
					if (chosen.size() >= max)
						chosen.remove(chosen.size() - 1);
					chosen.add(e);
					break;
				}
		for (Exercise e : ranked)
			if (chosen.size() < Math.min(max, 3) && !chosen.contains(e))
				chosen.add(e);
		return order(chosen);
	}

	/** Movilidad primero, cardio continuo al final. */
	private static List<Exercise> order(List<Exercise> chosen) {
		List<Exercise> result = new ArrayList<>();
		for (Exercise e : chosen)
			if (e.getMetricType() == MetricType.MOBILITY)
				result.add(e);
		for (Exercise e : chosen)
			if (!result.contains(e) && e.getMetricType() != MetricType.ENDURANCE &&
				e.getMetricType() != MetricType.SPORT_PRACTICE)
				result.add(e);
		for (Exercise e : chosen)
			if (!result.contains(e))
				result.add(e);
		return result;
	}

	/** Título corto según lo pedido («Piernas y glúteos · suave»), o vacío sin zonas. */
	public String title() {
		String base;
		if (areas.containsAll(UPPER) && Collections.disjoint(areas, LOWER))
			base = "Tren superior";
		else if (areas.size() >= 4)
			base = "Cuerpo completo";
		else {
			List<String> names = new ArrayList<>();
			for (Area a : areas)
				names.add(a.label);
			base = names.isEmpty() ? "" : join(names);
		}
		if (base.isEmpty())
			return "";
		base = Character.toUpperCase(base.charAt(0)) + base.substring(1);
		return base + (intensity == Intensity.EASY ? " · suave" : intensity == Intensity.HARD ? " · intensa" : "");
	}

	/** Resumen para el prompt del entrenador; vacío si la petición no concreta nada. */
	public String describe() {
		List<String> parts = new ArrayList<>();
		if (!areas.isEmpty()) {
			List<String> names = new ArrayList<>();
			for (Area a : areas)
				names.add(a.label);
			parts.add("zonas: " + join(names));
		}
		if (!excluded.isEmpty()) {
			List<String> names = new ArrayList<>();
			for (Area a : excluded)
				names.add(a.label);
			parts.add("evitar: " + join(names));
		}
		if (intensity != Intensity.NORMAL)
			parts.add("intensidad: " + (intensity == Intensity.EASY ? "suave" : "alta"));
		if (count > 0)
			parts.add(count + " ejercicios");
		return String.join("; ", parts);
	}

	private static String join(List<String> names) {
		return names.size() == 1 ? names.get(0) :
			String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
	}

	private static boolean has(String text, String alternatives) {
		for (String w : alternatives.split("\\|"))
			if (text.contains(" " + w + " ") || text.contains(" " + w + "s ") || text.contains(" " + w + "es "))
				return true;
		return false;
	}

	/** Mezcla completa (finalizador de MurmurHash3) para que semillas cercanas den órdenes muy distintos. */
	private static long mix(long seed, String id) {
		long h = seed * 0x9E3779B97F4A7C15L + id.hashCode();
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		return h ^ (h >>> 33);
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
