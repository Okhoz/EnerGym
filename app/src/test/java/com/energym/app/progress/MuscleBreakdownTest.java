package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;
import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class MuscleBreakdownTest {
	private static Exercise exercise(String id, String name) {
		return new Exercise(id, name, MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
	}

	@Test
	public void setsAndKilosAreCountedPerMuscleGroupAndDay() {
		Exercise squat = exercise("bodyweight-squat", "Sentadilla sin accesorios");
		Exercise push = exercise("standard-pushup", "Flexión estándar");
		Map<String, String> p = new LinkedHashMap<>();
		p.put("sets", "2");
		p.put("repetitions", "5");
		p.put("loadKg", "0");
		p.put("restSeconds", "60");
		Routine r = new Routine("r1", 1, "Rutina", new TrainingProfile.Builder().build(),
			Arrays.asList(new RoutineItem(squat, p, 5, "x"), new RoutineItem(push, p, 5, "x")), "x");
		Map<String, Map<String, String>> results = new LinkedHashMap<>();
		results.put("bodyweight-squat", SetLog.summary(Arrays.asList(new SetLog.Entry(60, 5), new SetLog.Entry(60, 5)), null, "60"));
		results.put("standard-pushup", SetLog.summary(Collections.singletonList(new SetLog.Entry(0, 12)), null, "60"));
		ZoneId zone = ZoneId.of("Europe/Madrid");
		LocalDate day = LocalDate.of(2026, 10, 14);
		long at = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli();
		Session done = new Session("s1", r, results, at - 3600_000, at, Session.Status.FINISHED);
		List<MuscleBreakdown.Share> shares = MuscleBreakdown.of(Collections.singletonList(done), zone,
			day.withDayOfMonth(1), day);
		assertEquals(Area.LEGS, shares.get(0).area);
		assertEquals(2, shares.get(0).sets, 0.001);
		assertEquals(600, shares.get(0).kg, 0.001);
		assertTrue(shares.stream().anyMatch(s -> s.area == Area.CHEST && s.sets > 0));
		assertTrue(MuscleBreakdown.of(Collections.singletonList(done), zone, day.plusDays(1), day.plusDays(5)).isEmpty());
		Set<Area> worked = MuscleBreakdown.byDay(Collections.singletonList(done), zone).get(day);
		assertTrue(worked.contains(Area.LEGS) && worked.contains(Area.CHEST));
	}
}
