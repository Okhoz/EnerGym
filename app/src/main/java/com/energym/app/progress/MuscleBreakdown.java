package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;

import java.time.*;
import java.util.*;

/**
 * MODELO del análisis por grupo muscular (como «Breakdown» de FitNotes): series y kilos movidos por zona en un
 * periodo, y qué zonas se entrenaron cada día para colorear el calendario. Un ejercicio que trabaja varias zonas
 * reparte sus series entre ellas.
 */
public final class MuscleBreakdown {
	public static final class Share {
		public final Area area;
		public final double sets, kg;

		Share(Area area, double sets, double kg) {
			this.area = area;
			this.sets = sets;
			this.kg = kg;
		}
	}

	private MuscleBreakdown() {
	}

	/** Zonas de cada sesión terminada por día (las de movilidad solo si no hubo otra cosa ese día). */
	public static Map<LocalDate, Set<Area>> byDay(List<Session> history, ZoneId zone) {
		Map<LocalDate, Set<Area>> result = new HashMap<>();
		for (Session s : history) {
			if (s.getStatus() != Session.Status.FINISHED)
				continue;
			LocalDate day = Instant.ofEpochMilli(s.getCompletedAt()).atZone(zone).toLocalDate();
			Set<Area> areas = result.computeIfAbsent(day, d -> EnumSet.noneOf(Area.class));
			for (RoutineItem item : s.getRoutine().getItems())
				if (s.getResults().containsKey(item.getExercise().getId()))
					areas.addAll(RoutineRequest.areas(item.getExercise()));
		}
		for (Set<Area> areas : result.values())
			if (areas.size() > 1)
				areas.remove(Area.MOBILITY);
		return result;
	}

	/** Reparto entre {@code from} y {@code to} (incluidos), de más a menos series. */
	public static List<Share> of(List<Session> history, ZoneId zone, LocalDate from, LocalDate to) {
		Map<Area, double[]> totals = new EnumMap<>(Area.class);
		for (Session s : history) {
			if (s.getStatus() != Session.Status.FINISHED)
				continue;
			LocalDate day = Instant.ofEpochMilli(s.getCompletedAt()).atZone(zone).toLocalDate();
			if (day.isBefore(from) || day.isAfter(to))
				continue;
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> done = s.getResults().get(item.getExercise().getId());
				if (done == null)
					continue;
				Set<Area> areas = EnumSet.noneOf(Area.class);
				areas.addAll(RoutineRequest.areas(item.getExercise()));
				if (areas.size() > 1)
					areas.remove(Area.CARDIO);
				if (areas.isEmpty())
					continue;
				double sets, kg = 0;
				switch (item.getExercise().getMetricType()) {
					case STRENGTH: {
						List<SetLog.Entry> logged = SetLog.of(done);
						sets = logged.size();
						for (SetLog.Entry e : logged)
							kg += e.kg * e.reps;
						break;
					}
					case ISOMETRIC:
						sets = number(done.get("sets"));
						break;
					default:
						sets = 1;
				}
				for (Area a : areas) {
					double[] t = totals.computeIfAbsent(a, x -> new double[2]);
					t[0] += sets / areas.size();
					t[1] += kg / areas.size();
				}
			}
		}
		List<Share> result = new ArrayList<>();
		for (Map.Entry<Area, double[]> e : totals.entrySet())
			if (e.getValue()[0] > 0)
				result.add(new Share(e.getKey(), e.getValue()[0], e.getValue()[1]));
		result.sort((a, b) -> Double.compare(b.sets, a.sets));
		return result;
	}

	private static double number(String value) {
		try {
			return value == null ? 0 : Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
