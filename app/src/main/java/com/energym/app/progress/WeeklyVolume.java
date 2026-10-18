package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;

import java.time.*;
import java.util.*;

/**
 * MODELO del volumen semanal por grupo muscular frente a lo recomendado para ganar músculo: unas 10–20 series
 * efectivas por grupo y semana. Avisa de los grupos que se quedan cortos o se pasan.
 */
public final class WeeklyVolume {
	public static final int MIN_SETS = 10, MAX_SETS = 20;
	/** Grupos que se comparan: cardio, core y movilidad no tienen un objetivo de series comparable. */
	public static final List<Area> AREAS = Collections.unmodifiableList(Arrays.asList(Area.CHEST, Area.BACK,
		Area.SHOULDERS, Area.ARMS, Area.LEGS, Area.GLUTES));

	public enum Status {LOW, OK, HIGH}

	public static final class Line {
		public final Area area;
		public final double sets;
		public final Status status;

		Line(Area area, double sets) {
			this.area = area;
			this.sets = sets;
			status = sets < MIN_SETS - 0.001 ? Status.LOW : sets > MAX_SETS + 0.001 ? Status.HIGH : Status.OK;
		}
	}

	private WeeklyVolume() {
	}

	/** Series de esta semana (de lunes a {@code today}) para cada grupo de {@link #AREAS}, en ese orden. */
	public static List<Line> of(List<Session> history, ZoneId zone, LocalDate today) {
		Map<Area, Double> sets = new EnumMap<>(Area.class);
		for (MuscleBreakdown.Share s : MuscleBreakdown.of(history, zone, ProgressStats.weekStart(today), today))
			sets.put(s.area, s.sets);
		List<Line> result = new ArrayList<>();
		for (Area a : AREAS)
			result.add(new Line(a, sets.getOrDefault(a, 0.0)));
		return result;
	}

	/** Grupos por debajo del mínimo, del más corto al menos. */
	public static List<Line> low(List<Line> lines) {
		List<Line> result = new ArrayList<>();
		for (Line l : lines)
			if (l.status == Status.LOW)
				result.add(l);
		result.sort(Comparator.comparingDouble(l -> l.sets));
		return result;
	}
}
