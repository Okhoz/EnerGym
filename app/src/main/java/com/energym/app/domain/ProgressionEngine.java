package com.energym.app.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * MODEL: sobrecarga progresiva por reglas a partir de lo realmente registrado en la última sesión de cada
 * ejercicio. Respeta el objetivo y el nivel del perfil y nunca supera el tiempo disponible.
 * Ejemplo orientativo sin revisión profesional: la persona revisa y acepta cada nueva versión.
 */
public final class ProgressionEngine {
	/**
	 * Propuesta de progresión: nueva versión de la rutina y explicación de cada cambio.
	 */
	public static final class Plan {
		public final Routine routine;
		public final List<String> changes;

		Plan(Routine routine, List<String> changes) {
			this.routine = routine;
			this.changes = Collections.unmodifiableList(changes);
		}
	}

	private final MetricValidator validator = new MetricValidator();

	/**
	 * @return null si no hay sesiones finalizadas de esta rutina o ningún ejercicio cambia.
	 */
	public Plan next(Routine accepted, List<Session> history) {
		if (accepted == null || accepted.isEmpty() || !accepted.isProgressionEnabled())
			return null;
		Session last = null;
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED &&
				s.getRoutine().getProposalId().equals(accepted.getProposalId()) &&
				(last == null || s.getCompletedAt() > last.getCompletedAt()))
				last = s;
		if (last == null)
			return null;
		TrainingProfile profile = accepted.getProfile();
		boolean beginner = normalize(profile.getLevel()).contains("princip") || normalize(profile.getLevel()).contains("inici");
		List<RoutineItem> items = new ArrayList<>();
		List<String> changes = new ArrayList<>();
		int remaining = profile.getDurationMinutes() - accepted.getEstimatedMinutes();
		for (RoutineItem item : accepted.getItems()) {
			Map<String, String> done = last.getResults().get(item.getExercise().getId());
			reason = "";
			Map<String, String> next = done == null ? item.getPrescription() :
				step(item, done, previous(history, last, item.getExercise().getId()), profile.getGoal(), beginner,
					remaining);
			if (!validator.validate(item.getExercise().getMetricType(), next).isEmpty())
				next = item.getPrescription();
			int minutes = item.getEstimatedMinutes();
			if (item.getExercise().getMetricType() == MetricType.STRENGTH ||
				item.getExercise().getMetricType() == MetricType.ISOMETRIC) {
				int required = Math.max(minutes, minimumMinutes(item.getExercise().getMetricType(), next));
				if (required - minutes > remaining) {
					// Mantener el volumen anterior no puede recuperar una carga que no se hizo.
					next = new LinkedHashMap<>(item.getPrescription());
					if (item.getExercise().getMetricType() == MetricType.STRENGTH && done != null)
						next.put("loadKg", fmt(number(done.get("loadKg"))));
					required = Math.max(minutes, minimumMinutes(item.getExercise().getMetricType(), next));
				}
				if (required - minutes > remaining)
					return null;
				remaining -= required - minutes;
				minutes = required;
			}
			if (item.getExercise().getMetricType() == MetricType.ENDURANCE) {
				int extra = (int) Math.ceil(number(next.get("timeMinutes")) - number(item.getPrescription().get("timeMinutes")));
				if (extra > 0 && extra <= remaining) {
					minutes += extra;
					remaining -= extra;
				} else
					next = item.getPrescription();
			}
			String change = describe(item, next);
			if (!change.isEmpty() && !reason.isEmpty())
				change += " (" + reason + ")";
			if (!change.isEmpty())
				changes.add(change);
			items.add(new RoutineItem(item.getExercise(), next, minutes,
				change.isEmpty() ? item.getJustification() : "Progresión: " + change + "."));
		}
		if (changes.isEmpty())
			return null;
		// Un día de plan semanal conserva su marca de plan para seguir encadenando con los demás días.
		java.util.regex.Matcher plan = java.util.regex.Pattern.compile("Plan semanal \\([^)]+\\)")
			.matcher(accepted.getExplanation() == null ? "" : accepted.getExplanation());
		Routine routine = new Routine(UUID.randomUUID().toString(), 0, title(accepted.getTitle()), profile, items,
			(plan.find() ? plan.group() + " · " : "") + "Sobrecarga progresiva con tus sesiones registradas: primero repeticiones y después carga, solo con " +
				"esfuerzo asumible; un mal día mantiene y dos seguidos bajan un poco. Ejemplo sin revisión " +
				"profesional; ajusta si notas molestias.",
			accepted.getMetadata());
		return routine.isValidFor(profile) ? new Plan(routine, changes) : null;
	}

	/** Mismo mínimo explícito de descansos/duración que el contrato de prescripción; no inventa un tempo. */
	private static int minimumMinutes(MetricType type, Map<String, String> prescription) {
		double sets = number(prescription.get("sets"));
		double seconds = Math.max(0, sets - 1) * number(prescription.get("restSeconds"));
		if (type == MetricType.ISOMETRIC)
			seconds += sets * number(prescription.get("durationSeconds"));
		return Math.max(1, (int) Math.ceil(seconds / 60));
	}

	/** Motivo legible del último cambio calculado, para explicar la progresión. */
	private String reason = "";

	/**
	 * Resultado anterior del mismo ejercicio (en cualquier rutina) antes de la última sesión; null si no hay.
	 */
	private static Map<String, String> previous(List<Session> history, Session last, String exerciseId) {
		Session best = null;
		for (Session s : history)
			if (s != last && s.getStatus() == Session.Status.FINISHED && s.getCompletedAt() <= last.getCompletedAt() &&
				s.getResults().containsKey(exerciseId) && (best == null || s.getCompletedAt() > best.getCompletedAt()))
				best = s;
		return best == null ? null : best.getResults().get(exerciseId);
	}

	private static double effort(Map<String, String> done) {
		return done.containsKey("effort") && number(done.get("effort")) > 0 ? number(done.get("effort")) : 7;
	}

	/** Muy por debajo de lo previsto: series sin hacer o menos del 75 % de las repeticiones. */
	private static boolean failed(Map<String, String> done, double sets, double reps) {
		return done != null && (number(done.get("sets")) < sets || number(done.get("repetitions")) < reps * 0.75);
	}

	/** La sesión anterior ya completó el techo de repeticiones con la misma carga y sin llegar al límite. */
	private static boolean consolidated(Map<String, String> before, double sets, int top, double load) {
		return before != null && number(before.get("sets")) >= sets && number(before.get("repetitions")) >= top &&
			Math.abs(number(before.get("loadKg")) - load) < 0.01 && effort(before) < 9;
	}

	private static String comma(double value) {
		return fmt(value).replace('.', ',');
	}

	/**
	 * Reglas coherentes: un mal día se repite y dos seguidos descargan; con esfuerzo 8 solo suben repeticiones;
	 * la carga sube con esfuerzo asumible y, desde nivel intermedio, tras consolidar el techo dos sesiones.
	 */
	private Map<String, String> step(RoutineItem item, Map<String, String> done, Map<String, String> before,
		TrainingProfile.Goal goal, boolean beginner, int remaining) {
		Map<String, String> p = new LinkedHashMap<>(item.getPrescription());
		double effort = effort(done);
		boolean hard = effort >= 9, easy = effort <= 7;
		switch (item.getExercise().getMetricType()) {
			case STRENGTH: {
				double sets = number(p.get("sets")), reps = number(p.get("repetitions"));
				double doneSets = number(done.get("sets")), doneReps = number(done.get("repetitions"));
				double load = number(done.get("loadKg"));
				p.put("loadKg", fmt(load));
				boolean external = !item.getExercise().getRequiredResources().isEmpty() && load > 0;
				if (failed(done, sets, reps)) {
					if (!failed(before, sets, reps))
						break;
					reason = "dos sesiones seguidas por debajo de lo previsto: descarga";
					if (external)
						p.put("loadKg", fmt(round(load * 0.9, increment(item))));
					else if (reps > 4)
						p.put("repetitions", fmt(reps - 1));
					break;
				}
				if (hard || doneSets < sets || doneReps < reps)
					break;
				int top = goal == TrainingProfile.Goal.STRENGTH ? 8 : 12;
				String did = fmt(doneSets) + "×" + fmt(doneReps) + (external ? " con " + comma(load) + " kg" : "") +
					" y esfuerzo " + comma(effort) + "/10";
				if (external) {
					// Doble progresión: primero repeticiones hasta el techo, después carga.
					if (reps < top) {
						p.put("repetitions", fmt(reps + 1));
						reason = "completaste " + did;
					} else if (easy && (beginner || consolidated(before, sets, top, load))) {
						double inc = Math.max(increment(item), load * (beginner ? 0.05 : 0.025));
						p.put("loadKg", fmt(round(load + inc, increment(item))));
						p.put("repetitions", fmt(Math.max(goal == TrainingProfile.Goal.STRENGTH ? 5 : 8, top - 3)));
						reason = beginner ? "completaste " + did : "dos sesiones seguidas completando " + did;
					}
				} else if (reps < (goal == TrainingProfile.Goal.ENDURANCE ? 25 : 15)) {
					p.put("repetitions", fmt(reps + (beginner || !easy ? 1 : 2)));
					reason = "completaste " + did;
				} else if (sets < 5 && easy) {
					p.put("sets", fmt(sets + 1));
					reason = "dominas " + did;
				}
				break;
			}
			case ISOMETRIC: {
				double seconds = number(p.get("durationSeconds")), sets = number(p.get("sets"));
				if (hard || number(done.get("sets")) < sets || number(done.get("durationSeconds")) < seconds)
					break;
				reason = "aguantaste " + fmt(seconds) + " s en todas las series";
				if (seconds < 90)
					p.put("durationSeconds", fmt(seconds + (beginner || !easy ? 5 : 10)));
				else if (sets < 5 && easy)
					p.put("sets", fmt(sets + 1));
				break;
			}
			case ENDURANCE: {
				double minutes = number(p.get("timeMinutes"));
				if (hard || number(done.get("timeMinutes")) < minutes)
					break;
				// Regla del 10 %: el volumen aeróbico sube poco a poco y nunca supera el tiempo disponible.
				double next = Math.min(minutes + Math.max(1, Math.round(minutes * (easy ? 0.1 : 0.05))),
					minutes + remaining);
				p.put("timeMinutes", fmt(next));
				reason = "completaste " + fmt(minutes) + " min con esfuerzo " + comma(effort) + "/10";
				break;
			}
			case MOBILITY: {
				double reps = number(p.get("repetitions"));
				if (!hard && number(done.get("repetitions")) >= reps && reps < 10)
					p.put("repetitions", fmt(reps + 1));
				break;
			}
			default:
				break;
		}
		return p;
	}

	private static String describe(RoutineItem item, Map<String, String> next) {
		List<String> parts = new ArrayList<>();
		Map<String, String> before = item.getPrescription();
		for (Map.Entry<String, String> e : next.entrySet()) {
			String old = before.getOrDefault(e.getKey(), "");
			if (old.equals(e.getValue()) || number(old) == number(e.getValue()))
				continue;
			String label = label(e.getKey());
			if (label != null)
				parts.add(label + " " + old.replace('.', ',') + " → " + e.getValue().replace('.', ','));
		}
		return parts.isEmpty() ? "" : item.getExercise().getName() + ": " + String.join(", ", parts);
	}

	private static String label(String key) {
		switch (key) {
			case "sets":
				return "series";
			case "repetitions":
				return "repeticiones";
			case "loadKg":
				return "kg";
			case "durationSeconds":
				return "segundos";
			case "timeMinutes":
				return "minutos";
			default:
				return null;
		}
	}

	/**
	 * Incremento mínimo realista: 2,5 kg con barra o máquina, 1 kg con mancuernas.
	 */
	private static double increment(RoutineItem item) {
		Set<TrainingProfile.Resource> r = item.getExercise().getRequiredResources();
		return r.contains(TrainingProfile.Resource.BARBELL) || r.contains(TrainingProfile.Resource.MACHINE) ? 2.5 : 1;
	}

	private static double round(double value, double step) {
		return Math.max(step, Math.round(value / step) * step);
	}

	private static String title(String title) {
		String base = title.replaceAll(" · nivel \\d+$", "");
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(" · nivel (\\d+)$").matcher(title);
		int level = m.find() ? Integer.parseInt(m.group(1)) + 1 : 2;
		String next = base + " · nivel " + level;
		return next.length() > 120 ? next.substring(0, 120) : next;
	}

	public static String fmt(double v) {
		return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private static double number(String value) {
		if (value == null || value.trim().isEmpty())
			return 0;
		try {
			return new BigDecimal(value.trim().replace(',', '.')).doubleValue();
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String normalize(String text) {
		return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}
}
