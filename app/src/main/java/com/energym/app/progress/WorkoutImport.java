package com.energym.app.progress;

import com.energym.app.domain.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MODELO para cambiarse desde otras apps: lee el CSV que exportan Strong y Hevy, agrupa las series por entreno y
 * traduce cada ejercicio al del catálogo de EnerGym. Lo que no se reconoce se informa y no se importa. Solo
 * ejercicios de fuerza con series de peso × repeticiones. También exporta el historial con el formato de Strong.
 */
public final class WorkoutImport {
	public enum Source {STRONG, HEVY}

	public static final class Result {
		public final Source source;
		public final List<Session> sessions;
		/** Nombres de ejercicio que no tienen equivalente en el catálogo (sin repetir). */
		public final Set<String> unmatched;

		Result(Source source, List<Session> sessions, Set<String> unmatched) {
			this.source = source;
			this.sessions = Collections.unmodifiableList(sessions);
			this.unmatched = Collections.unmodifiableSet(unmatched);
		}
	}

	private static final double LB = 0.45359237;

	/** Nombre base en inglés (sin el material entre paréntesis) + material → id del catálogo. */
	private static final Map<String, String> NAMES = new HashMap<>();

	static {
		name("squat", "barbell", "barbell-back-squat");
		name("back squat", "barbell", "barbell-back-squat");
		name("front squat", "barbell", "barbell-front-squat");
		name("goblet squat", "", "dumbbell-goblet-squat");
		name("squat", "dumbbell", "dumbbell-squat");
		name("squat", "smith machine", "smith-squat");
		name("squat", "", "bodyweight-squat");
		name("air squat", "", "bodyweight-squat");
		name("hack squat", "", "hack-squat");
		name("bench press", "barbell", "barbell-bench-press");
		name("bench press", "dumbbell", "dumbbell-bench-press");
		name("bench press", "smith machine", "smith-bench-press");
		name("chest press", "machine", "machine-chest-press");
		name("incline bench press", "barbell", "barbell-incline-press");
		name("incline bench press", "dumbbell", "dumbbell-incline-press");
		name("close grip bench press", "barbell", "barbell-close-grip-press");
		name("deadlift", "barbell", "barbell-deadlift");
		name("romanian deadlift", "barbell", "barbell-romanian-deadlift");
		name("romanian deadlift", "dumbbell", "dumbbell-romanian-deadlift");
		name("overhead press", "barbell", "barbell-overhead-press");
		name("strict military press", "barbell", "barbell-overhead-press");
		name("shoulder press", "barbell", "barbell-overhead-press");
		name("shoulder press", "dumbbell", "dumbbell-shoulder-press");
		name("overhead press", "dumbbell", "dumbbell-shoulder-press");
		name("shoulder press", "machine", "machine-shoulder-press");
		name("arnold press", "dumbbell", "dumbbell-arnold-press");
		name("bent over row", "barbell", "barbell-row");
		name("barbell row", "", "barbell-row");
		name("bent over row", "dumbbell", "dumbbell-row");
		name("dumbbell row", "", "dumbbell-one-arm-row");
		name("bent over one arm row", "dumbbell", "dumbbell-one-arm-row");
		name("seated row", "cable", "seated-cable-row");
		name("seated cable row", "", "seated-cable-row");
		name("seated row", "machine", "machine-row");
		name("lat pulldown", "cable", "lat-pulldown");
		name("lat pulldown", "machine", "lat-pulldown");
		name("lat pulldown", "", "lat-pulldown");
		name("pull up", "", "pull-up");
		name("pull up", "assisted", "assisted-pull-up");
		name("chin up", "", "chin-up");
		name("push up", "", "standard-pushup");
		name("dip", "", "parallel-bar-dips");
		name("chest dip", "", "parallel-bar-dips");
		name("triceps dip", "", "parallel-bar-dips");
		name("leg press", "", "leg-press");
		name("leg press", "machine", "leg-press");
		name("leg extension", "machine", "leg-extension");
		name("lying leg curl", "machine", "leg-curl");
		name("leg curl", "machine", "leg-curl");
		name("seated leg curl", "machine", "seated-leg-curl");
		name("hip thrust", "barbell", "barbell-hip-thrust");
		name("hip thrust", "machine", "machine-hip-thrust");
		name("hip abductor", "machine", "machine-hip-abduction");
		name("hip adductor", "machine", "machine-hip-adduction");
		name("bicep curl", "barbell", "barbell-curl");
		name("bicep curl", "dumbbell", "dumbbell-biceps-curl");
		name("bicep curl", "cable", "cable-biceps-curl");
		name("hammer curl", "dumbbell", "dumbbell-hammer-curl");
		name("lateral raise", "dumbbell", "dumbbell-lateral-raise");
		name("lateral raise", "cable", "cable-lateral-raise");
		name("lateral raise", "machine", "machine-lateral-raise");
		name("reverse fly", "dumbbell", "dumbbell-reverse-fly");
		name("rear delt reverse fly", "dumbbell", "dumbbell-reverse-fly");
		name("reverse fly", "machine", "reverse-pec-deck");
		name("chest fly", "dumbbell", "dumbbell-fly");
		name("chest fly", "machine", "pec-deck");
		name("butterfly", "pec deck", "pec-deck");
		name("cable crossover", "", "cable-crossover");
		name("triceps pushdown", "cable", "cable-triceps-pushdown");
		name("triceps pushdown", "cable straight bar", "cable-triceps-pushdown");
		name("triceps rope pushdown", "", "cable-triceps-pushdown");
		name("triceps extension", "dumbbell", "dumbbell-overhead-triceps");
		name("overhead triceps extension", "cable", "cable-overhead-triceps");
		name("triceps kickback", "dumbbell", "dumbbell-triceps-kickback");
		name("face pull", "cable", "cable-face-pull");
		name("face pull", "", "cable-face-pull");
		name("shrug", "dumbbell", "dumbbell-shrug");
		name("lunge", "dumbbell", "dumbbell-walking-lunge");
		name("walking lunge", "dumbbell", "dumbbell-walking-lunge");
		name("lunge", "", "bodyweight-lunge");
		name("bulgarian split squat", "dumbbell", "dumbbell-bulgarian-split-squat");
		name("bulgarian split squat", "", "split-squat");
		name("step up", "dumbbell", "dumbbell-step-up");
		name("calf raise", "machine", "machine-calf-raise");
		name("standing calf raise", "machine", "machine-calf-raise");
		name("seated calf raise", "machine", "seated-calf-raise");
		name("calf raise", "dumbbell", "dumbbell-calf-raise");
		name("crunch", "", "crunch");
		name("cable crunch", "", "cable-crunch");
		name("crunch", "cable", "cable-crunch");
		name("leg raise", "", "lying-leg-raise");
		name("lying leg raise", "", "lying-leg-raise");
		name("glute bridge", "", "glute-bridge");
		name("burpee", "", "burpee");
		name("pullover", "dumbbell", "dumbbell-pullover");
		name("thruster", "dumbbell", "dumbbell-thruster");
	}

	private static void name(String base, String equipment, String id) {
		NAMES.put(key(base, equipment), id);
	}

	private WorkoutImport() {
	}

	/** Id del catálogo para un nombre de Strong/Hevy («Bench Press (Barbell)») o un nombre del propio catálogo. */
	public static String match(String name, List<Exercise> catalog) {
		if (name == null || name.trim().isEmpty())
			return null;
		String n = name.trim();
		for (Exercise e : catalog)
			if (normalize(e.getName()).equals(normalize(n)) || normalize(e.getSourceName()).equals(normalize(n)) ||
				e.getId().equals(n))
				return e.getId();
		String base = n, equipment = "";
		int open = n.lastIndexOf('(');
		if (open > 0 && n.endsWith(")")) {
			base = n.substring(0, open);
			equipment = n.substring(open + 1, n.length() - 1);
		}
		String id = NAMES.get(key(base, equipment));
		if (id == null)
			id = NAMES.get(key(base, ""));
		return id;
	}

	private static String key(String base, String equipment) {
		return normalize(base).replaceAll("s$", "") + "|" + normalize(equipment);
	}

	private static String normalize(String text) {
		return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/**
	 * @return null si el texto no es un CSV de Strong ni de Hevy.
	 */
	public static Result parse(String csv, List<Exercise> catalog, TrainingProfile profile, ZoneId zone) {
		if (csv == null || catalog == null || profile == null)
			return null;
		String text = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
		int firstLine = text.indexOf('\n');
		String header = firstLine < 0 ? text : text.substring(0, firstLine);
		char delimiter = count(header, ';') > count(header, ',') ? ';' : ',';
		List<List<String>> rows = rows(text, delimiter);
		if (rows.isEmpty())
			return null;
		Map<String, Integer> col = new HashMap<>();
		for (int i = 0; i < rows.get(0).size(); i++)
			col.put(rows.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
		Source source;
		if (col.containsKey("exercise_title") && col.containsKey("start_time"))
			source = Source.HEVY;
		else if (col.containsKey("exercise name") && col.containsKey("date") && col.containsKey("set order"))
			source = Source.STRONG;
		else
			return null;
		Map<String, Exercise> byId = new HashMap<>();
		for (Exercise e : catalog)
			byId.put(e.getId(), e);
		// Entreno (inicio + título) → ejercicio → series, en el orden del archivo.
		Map<String, Workout> workouts = new LinkedHashMap<>();
		Set<String> unmatched = new TreeSet<>();
		boolean lbs = col.containsKey("weight_lbs");
		for (List<String> row : rows.subList(1, rows.size())) {
			String exercise = cell(row, col, source == Source.HEVY ? "exercise_title" : "exercise name");
			if (exercise.isEmpty())
				continue;
			String id = match(exercise, catalog);
			Exercise e = id == null ? null : byId.get(id);
			if (e == null || e.getMetricType() != MetricType.STRENGTH) {
				unmatched.add(exercise);
				continue;
			}
			String title, startText, endText;
			double kg;
			int reps;
			SetLog.Kind kind;
			if (source == Source.HEVY) {
				title = cell(row, col, "title");
				startText = cell(row, col, "start_time");
				endText = cell(row, col, "end_time");
				kg = number(cell(row, col, lbs ? "weight_lbs" : "weight_kg")) * (lbs ? LB : 1);
				reps = (int) number(cell(row, col, "reps"));
				String type = cell(row, col, "set_type").toLowerCase(Locale.ROOT);
				kind = type.startsWith("warm") ? SetLog.Kind.WARMUP : type.startsWith("drop") ? SetLog.Kind.DROP :
					type.startsWith("fail") ? SetLog.Kind.FAILURE : SetLog.Kind.NORMAL;
			} else {
				title = cell(row, col, "workout name");
				startText = cell(row, col, "date");
				endText = "";
				boolean rowLbs = cell(row, col, "weight unit").toLowerCase(Locale.ROOT).startsWith("lb");
				kg = number(cell(row, col, "weight")) * (rowLbs ? LB : 1);
				reps = (int) number(cell(row, col, "reps"));
				String order = cell(row, col, "set order").toUpperCase(Locale.ROOT);
				kind = order.equals("W") ? SetLog.Kind.WARMUP : order.equals("D") ? SetLog.Kind.DROP :
					order.equals("F") ? SetLog.Kind.FAILURE : SetLog.Kind.NORMAL;
			}
			LocalDateTime start = date(startText);
			if (start == null || reps <= 0)
				continue;
			double rpe = number(cell(row, col, "rpe"));
			String key = startText + "|" + title;
			Workout w = workouts.get(key);
			if (w == null) {
				LocalDateTime end = date(endText);
				if (end == null || end.isBefore(start))
					end = start.plusMinutes(source == Source.STRONG ? minutes(cell(row, col, "duration")) : 60);
				w = new Workout(title.isEmpty() ? "Entreno importado" : title, start, end);
				workouts.put(key, w);
			}
			String note = cell(row, col, source == Source.HEVY ? "exercise_notes" : "notes");
			if (!note.isEmpty())
				w.notes.put(e, note);
			w.sets.computeIfAbsent(e, x -> new ArrayList<>())
				.add(new SetLog.Entry(Math.round(Math.max(0, kg) * 100) / 100.0, Math.min(reps, 999), kind, rpe));
		}
		List<Session> sessions = new ArrayList<>();
		for (Workout w : workouts.values()) {
			Session s = w.session(source, profile, zone);
			if (s != null)
				sessions.add(s);
		}
		return new Result(source, sessions, unmatched);
	}

	private static final class Workout {
		final String title;
		final LocalDateTime start, end;
		final Map<Exercise, List<SetLog.Entry>> sets = new LinkedHashMap<>();
		final Map<Exercise, String> notes = new HashMap<>();

		Workout(String title, LocalDateTime start, LocalDateTime end) {
			this.title = title;
			this.start = start;
			this.end = end;
		}

		Session session(Source source, TrainingProfile profile, ZoneId zone) {
			List<RoutineItem> items = new ArrayList<>();
			Map<String, Map<String, String>> results = new LinkedHashMap<>();
			for (Map.Entry<Exercise, List<SetLog.Entry>> e : sets.entrySet()) {
				List<SetLog.Entry> list = e.getValue().size() > SetLog.MAX_SETS ?
					e.getValue().subList(0, SetLog.MAX_SETS) : e.getValue();
				Map<String, String> base = new HashMap<>();
				if (notes.containsKey(e.getKey()))
					base.put(SetLog.NOTE, notes.get(e.getKey()).replaceAll("\\s+", " "));
				Map<String, String> done = SetLog.summary(list, base, "90");
				if (done == null)
					continue;
				Map<String, String> prescription = new LinkedHashMap<>();
				prescription.put("sets", done.get("sets"));
				prescription.put("repetitions", done.get("repetitions"));
				prescription.put("loadKg", done.get("loadKg"));
				prescription.put("restSeconds", "90");
				items.add(new RoutineItem(e.getKey(), prescription, Math.max(1, list.size() * 2),
					"Importado desde " + (source == Source.HEVY ? "Hevy" : "Strong") + "."));
				results.put(e.getKey().getId(), done);
			}
			if (items.isEmpty())
				return null;
			String id = UUID.nameUUIDFromBytes((source + "|" + start + "|" + title).getBytes(StandardCharsets.UTF_8))
				.toString();
			long started = start.atZone(zone).toInstant().toEpochMilli();
			long finished = Math.max(started, end.atZone(zone).toInstant().toEpochMilli());
			Routine routine = new Routine("import-" + id, 0, title.length() > 120 ? title.substring(0, 120) : title,
				profile, items, "Entreno importado desde " + (source == Source.HEVY ? "Hevy" : "Strong") + ".",
				new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
			return new Session("import-" + id, routine, results, started, finished, Session.Status.FINISHED);
		}
	}

	private static final DateTimeFormatter[] DATES = {
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
		DateTimeFormatter.ISO_LOCAL_DATE_TIME,
		DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH),
		DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH),
	};

	static LocalDateTime date(String text) {
		if (text == null || text.trim().isEmpty())
			return null;
		String t = text.trim();
		for (DateTimeFormatter f : DATES)
			try {
				return LocalDateTime.parse(t, f);
			} catch (RuntimeException ignored) {
				// Probar el siguiente formato.
			}
		try {
			return OffsetDateTime.parse(t).toLocalDateTime();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Duración de Strong («1h 5m», «45m» o segundos); 60 si no se entiende. */
	static long minutes(String text) {
		String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		if (t.matches("\\d+"))
			return Math.max(1, Long.parseLong(t) / 60);
		java.util.regex.Matcher h = java.util.regex.Pattern.compile("(\\d+)\\s*h").matcher(t);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*m").matcher(t);
		long total = (h.find() ? Long.parseLong(h.group(1)) * 60 : 0) + (m.find() ? Long.parseLong(m.group(1)) : 0);
		return total > 0 ? total : 60;
	}

	/** Historial en el formato CSV de Strong, que entienden Strong, Hevy y otras apps. */
	public static String exportStrong(List<Session> history, ZoneId zone) {
		StringBuilder out = new StringBuilder(
			"Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE\n");
		DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		List<Session> sorted = new ArrayList<>();
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED)
				sorted.add(s);
		sorted.sort(Comparator.comparingLong(Session::getStartedAt));
		for (Session s : sorted) {
			String date = Instant.ofEpochMilli(s.getStartedAt()).atZone(zone).toLocalDateTime().format(f);
			long minutes = Math.max(1, (s.getCompletedAt() - s.getStartedAt()) / 60000);
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> done = s.getResults().get(item.getExercise().getId());
				if (done == null || item.getExercise().getMetricType() != MetricType.STRENGTH)
					continue;
				int order = 0;
				for (SetLog.Entry e : SetLog.of(done)) {
					String set = e.kind == SetLog.Kind.WARMUP ? "W" : e.kind == SetLog.Kind.DROP ? "D" :
						e.kind == SetLog.Kind.FAILURE ? "F" : Integer.toString(++order);
					out.append(csv(date)).append(',').append(csv(s.getRoutine().getTitle())).append(',')
						.append(minutes / 60 > 0 ? (minutes / 60) + "h " : "").append(minutes % 60).append("m,")
						.append(csv(item.getExercise().getName())).append(',').append(set).append(',')
						.append(SetLog.format(e.kg)).append(',').append(e.reps).append(",0,0,")
						.append(csv(done.getOrDefault(SetLog.NOTE, ""))).append(",,")
						.append(e.rpe > 0 ? SetLog.format(e.rpe) : "").append('\n');
				}
			}
		}
		return out.toString();
	}

	private static String csv(String value) {
		String c = value == null ? "" : value;
		if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0)
			c = "'" + c;
		return c.contains(",") || c.contains("\"") || c.contains("\n") ? '"' + c.replace("\"", "\"\"") + '"' : c;
	}

	private static String cell(List<String> row, Map<String, Integer> col, String name) {
		Integer i = col.get(name);
		return i == null || i >= row.size() ? "" : row.get(i).trim();
	}

	private static double number(String text) {
		try {
			return text == null || text.isEmpty() ? 0 : Double.parseDouble(text.replace(',', '.'));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int count(String text, char c) {
		int n = 0;
		for (int i = 0; i < text.length(); i++)
			if (text.charAt(i) == c)
				n++;
		return n;
	}

	static List<List<String>> rows(String csv, char delimiter) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < csv.length(); i++) {
			char ch = csv.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (ch == '"')
					quoted = false;
				else
					cell.append(ch);
			} else if (ch == '"')
				quoted = true;
			else if (ch == delimiter) {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (ch == '\n') {
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			} else if (ch != '\r')
				cell.append(ch);
		}
		if (cell.length() > 0 || !row.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}
}
