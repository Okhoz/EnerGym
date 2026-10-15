package com.energym.app.progress;

import com.energym.app.domain.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

/**
 * MODEL: resumen global calculado solo con sesiones finalizadas. Sin Android; la zona horaria es explícita.
 */
public final class ProgressStats {
	public static final int WEEKS = 8;

	/**
	 * Tendencia entre las cuatro últimas semanas y las cuatro anteriores. Sin datos previos no hay porcentaje.
	 */
	public static final class Trend {
		public final double recent, previous;

		Trend(double recent, double previous) {
			this.recent = recent;
			this.previous = previous;
		}

		public boolean comparable() {
			return previous > 0;
		}

		public double percent() {
			return comparable() ? (recent - previous) * 100.0 / previous : 0;
		}
	}

	private final ZoneId zone;
	private final LocalDate today;
	private final List<Session> finished = new ArrayList<>();

	public ProgressStats(List<Session> history, ZoneId zone, LocalDate today) {
		this.zone = Objects.requireNonNull(zone, "zone");
		this.today = Objects.requireNonNull(today, "today");
		for (Session s : Objects.requireNonNull(history, "history"))
			if (s.getStatus() == Session.Status.FINISHED)
				finished.add(s);
	}

	public LocalDate day(Session s) {
		return Instant.ofEpochMilli(s.getCompletedAt()).atZone(zone).toLocalDate();
	}

	/**
	 * Número de sesiones finalizadas por día; el calendario marca exactamente estos días.
	 */
	public Map<LocalDate, Integer> sessionsByDay() {
		Map<LocalDate, Integer> days = new TreeMap<>();
		for (Session s : finished)
			days.merge(day(s), 1, Integer::sum);
		return days;
	}

	public List<Session> sessionsOn(LocalDate date) {
		List<Session> result = new ArrayList<>();
		for (Session s : finished)
			if (day(s).equals(date))
				result.add(s);
		return result;
	}

	public int total() {
		return finished.size();
	}

	/**
	 * Lunes de la semana de una fecha; las semanas empiezan en lunes como en España.
	 */
	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/**
	 * Sesiones por semana, de la más antigua a la actual.
	 */
	public double[] sessionsPerWeek() {
		double[] counts = new double[WEEKS];
		LocalDate first = weekStart(today).minusWeeks(WEEKS - 1);
		for (Session s : finished) {
			long index = ChronoUnit.WEEKS.between(first, weekStart(day(s)));
			if (index >= 0 && index < WEEKS)
				counts[(int) index]++;
		}
		return counts;
	}

	/**
	 * Minutos entre inicio y finalización registrados por semana; no se inventan duraciones.
	 */
	public double[] minutesPerWeek() {
		double[] minutes = new double[WEEKS];
		LocalDate first = weekStart(today).minusWeeks(WEEKS - 1);
		for (Session s : finished) {
			long index = ChronoUnit.WEEKS.between(first, weekStart(day(s)));
			if (index >= 0 && index < WEEKS)
				minutes[(int) index] += Math.max(0, s.getCompletedAt() - s.getStartedAt()) / 60000.0;
		}
		return minutes;
	}

	public String[] weekLabels() {
		String[] labels = new String[WEEKS];
		LocalDate first = weekStart(today).minusWeeks(WEEKS - 1);
		for (int i = 0; i < WEEKS; i++) {
			LocalDate d = first.plusWeeks(i);
			labels[i] = d.getDayOfMonth() + "/" + d.getMonthValue();
		}
		return labels;
	}

	/**
	 * Volumen de fuerza de una sesión: series × repeticiones × kg. Peso corporal (0 kg) no suma volumen.
	 */
	public static double volume(Session s) {
		double total = 0;
		for (RoutineItem item : s.getRoutine().getItems()) {
			if (item.getExercise().getMetricType() != MetricType.STRENGTH)
				continue;
			Map<String, String> r = s.getResults().get(item.getExercise().getId());
			if (r == null)
				continue;
			total += number(r.get("sets")) * number(r.get("repetitions")) * number(r.get("loadKg"));
		}
		return total;
	}

	/**
	 * Volumen de las últimas sesiones en orden cronológico, para la gráfica de línea.
	 */
	public double[] recentVolumes(int limit) {
		List<Session> ordered = new ArrayList<>(finished);
		ordered.sort(Comparator.comparingLong(Session::getCompletedAt));
		List<Session> tail = ordered.subList(Math.max(0, ordered.size() - limit), ordered.size());
		double[] values = new double[tail.size()];
		for (int i = 0; i < values.length; i++)
			values[i] = volume(tail.get(i));
		return values;
	}

	/**
	 * Volumen de fuerza (kg) por semana, de la más antigua a la actual.
	 */
	public double[] volumePerWeek() {
		return perWeek(ProgressStats::volume);
	}

	/**
	 * Kilómetros registrados (campo opcional de resistencia) por semana.
	 */
	public double[] distancePerWeek() {
		return perWeek(ProgressStats::distance);
	}

	private double[] perWeek(java.util.function.ToDoubleFunction<Session> metric) {
		double[] values = new double[WEEKS];
		LocalDate first = weekStart(today).minusWeeks(WEEKS - 1);
		for (Session s : finished) {
			long index = ChronoUnit.WEEKS.between(first, weekStart(day(s)));
			if (index >= 0 && index < WEEKS)
				values[(int) index] += metric.applyAsDouble(s);
		}
		return values;
	}

	public static double distance(Session s) {
		double total = 0;
		for (RoutineItem item : s.getRoutine().getItems()) {
			Map<String, String> r = s.getResults().get(item.getExercise().getId());
			if (r != null && item.getExercise().getMetricType() == MetricType.ENDURANCE)
				total += number(r.get("distanceKm"));
		}
		return total;
	}

	public double totalMinutes() {
		double total = 0;
		for (Session s : finished)
			total += Math.max(0, s.getCompletedAt() - s.getStartedAt()) / 60000.0;
		return total;
	}

	public double totalVolume() {
		double total = 0;
		for (Session s : finished)
			total += volume(s);
		return total;
	}

	public double totalDistance() {
		double total = 0;
		for (Session s : finished)
			total += distance(s);
		return total;
	}

	public int exercisesDone() {
		int total = 0;
		for (Session s : finished)
			total += s.getResults().size();
		return total;
	}

	/**
	 * Evolución de carga de un ejercicio de fuerza: mayor kg registrado en cada sesión, en orden cronológico.
	 */
	public double[] loadHistory(String exerciseId) {
		List<Session> ordered = new ArrayList<>(finished);
		ordered.sort(Comparator.comparingLong(Session::getCompletedAt));
		List<Double> values = new ArrayList<>();
		for (Session s : ordered) {
			Map<String, String> r = s.getResults().get(exerciseId);
			if (r != null && number(r.get("loadKg")) > 0)
				values.add(number(r.get("loadKg")));
		}
		double[] result = new double[values.size()];
		for (int i = 0; i < result.length; i++)
			result[i] = values.get(i);
		return result;
	}

	/**
	 * Mejora de cada ejercicio con carga: primera y mejor carga registradas, ordenadas por mayor mejora.
	 */
	public static final class Improvement {
		public final String exerciseId, name;
		public final double first, best;

		Improvement(String exerciseId, String name, double first, double best) {
			this.exerciseId = exerciseId;
			this.name = name;
			this.first = first;
			this.best = best;
		}

		public double gain() {
			return best - first;
		}
	}

	public List<Improvement> improvements() {
		Map<String, String> names = new LinkedHashMap<>();
		for (Session s : finished)
			for (RoutineItem item : s.getRoutine().getItems())
				if (item.getExercise().getMetricType() == MetricType.STRENGTH)
					names.putIfAbsent(item.getExercise().getId(), item.getExercise().getName());
		List<Improvement> result = new ArrayList<>();
		for (Map.Entry<String, String> e : names.entrySet()) {
			double[] loads = loadHistory(e.getKey());
			if (loads.length == 0)
				continue;
			double best = loads[0];
			for (double v : loads)
				best = Math.max(best, v);
			result.add(new Improvement(e.getKey(), e.getValue(), loads[0], best));
		}
		result.sort((a, b) -> Double.compare(b.gain(), a.gain()));
		return result;
	}

	public LocalDate today() {
		return today;
	}

	public Trend sessionsTrend() {
		double[] weeks = sessionsPerWeek();
		return new Trend(sum(weeks, 4, 8), sum(weeks, 0, 4));
	}

	public Trend minutesTrend() {
		double[] weeks = minutesPerWeek();
		return new Trend(sum(weeks, 4, 8), sum(weeks, 0, 4));
	}

	/**
	 * Semanas consecutivas con al menos una sesión; la semana actual aún abierta no rompe la racha.
	 */
	public int weekStreak() {
		Set<LocalDate> weeks = new HashSet<>();
		for (Session s : finished)
			weeks.add(weekStart(day(s)));
		LocalDate cursor = weekStart(today);
		if (!weeks.contains(cursor))
			cursor = cursor.minusWeeks(1);
		int streak = 0;
		while (weeks.contains(cursor)) {
			streak++;
			cursor = cursor.minusWeeks(1);
		}
		return streak;
	}

	public int daysThisMonth() {
		int days = 0;
		for (LocalDate d : sessionsByDay().keySet())
			if (d.getYear() == today.getYear() && d.getMonth() == today.getMonth())
				days++;
		return days;
	}

	private static double sum(double[] values, int from, int to) {
		double total = 0;
		for (int i = from; i < to; i++)
			total += values[i];
		return total;
	}

	static double number(String value) {
		if (value == null || value.trim().isEmpty())
			return 0;
		try {
			return new BigDecimal(value.trim().replace(',', '.')).doubleValue();
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
