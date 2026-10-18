package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;
import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class WeeklyVolumeTest {
	private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

	private static Exercise exercise(String id, String name) {
		return new Exercise(id, name, MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.GYM), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
	}

	private static Session session(String id, Exercise e, int sets, LocalDate day) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("sets", Integer.toString(sets));
		p.put("repetitions", "8");
		p.put("loadKg", "50");
		p.put("restSeconds", "90");
		Routine r = new Routine("r-" + id, 1, "Rutina", new TrainingProfile.Builder().build(),
			Collections.singletonList(new RoutineItem(e, p, 10, "x")), "x");
		List<SetLog.Entry> logged = new ArrayList<>();
		for (int i = 0; i < sets; i++)
			logged.add(new SetLog.Entry(50, 8));
		long at = day.atTime(18, 0).atZone(ZONE).toInstant().toEpochMilli();
		return new Session(id, r, Collections.singletonMap(e.getId(), SetLog.summary(logged, null, "90")),
			at - 3600_000, at, Session.Status.FINISHED);
	}

	@Test
	public void comparesThisWeeksSetsWithTheRecommendedRange() {
		Exercise bench = exercise("barbell-bench-press", "Press de banca con barra");
		LocalDate wednesday = LocalDate.of(2026, 10, 14);
		List<Session> history = Arrays.asList(session("a", bench, 6, wednesday.minusDays(2)),
			session("b", bench, 6, wednesday), session("old", bench, 20, wednesday.minusDays(7)));
		List<WeeklyVolume.Line> lines = WeeklyVolume.of(history, ZONE, wednesday);
		assertEquals(WeeklyVolume.AREAS.size(), lines.size());
		WeeklyVolume.Line chest = lines.get(WeeklyVolume.AREAS.indexOf(Area.CHEST));
		assertEquals("La semana pasada no cuenta", 12, chest.sets, 0.5);
		assertEquals(WeeklyVolume.Status.OK, chest.status);
		WeeklyVolume.Line legs = lines.get(WeeklyVolume.AREAS.indexOf(Area.LEGS));
		assertEquals(0, legs.sets, 0.001);
		assertEquals(WeeklyVolume.Status.LOW, legs.status);
		assertFalse(WeeklyVolume.low(lines).stream().anyMatch(l -> l.area == Area.CHEST));
		assertTrue(WeeklyVolume.low(lines).stream().anyMatch(l -> l.area == Area.LEGS));
	}

	@Test
	public void tooManySetsAreFlagged() {
		Exercise bench = exercise("barbell-bench-press", "Press de banca con barra");
		LocalDate day = LocalDate.of(2026, 10, 14);
		List<Session> history = new ArrayList<>();
		for (int i = 0; i < 3; i++)
			history.add(session("s" + i, bench, 9, day));
		WeeklyVolume.Line chest = WeeklyVolume.of(history, ZONE, day).get(WeeklyVolume.AREAS.indexOf(Area.CHEST));
		assertTrue(chest.sets > WeeklyVolume.MAX_SETS);
		assertEquals(WeeklyVolume.Status.HIGH, chest.status);
	}

	@Test
	public void lowIsSortedFromShortest() {
		List<WeeklyVolume.Line> lines = WeeklyVolume.of(Collections.emptyList(), ZONE, LocalDate.of(2026, 10, 14));
		assertEquals(WeeklyVolume.AREAS.size(), WeeklyVolume.low(lines).size());
		Exercise bench = exercise("barbell-bench-press", "Press de banca con barra");
		lines = WeeklyVolume.of(Collections.singletonList(session("a", bench, 4, LocalDate.of(2026, 10, 13))), ZONE,
			LocalDate.of(2026, 10, 14));
		List<WeeklyVolume.Line> low = WeeklyVolume.low(lines);
		assertEquals(Area.CHEST, low.get(low.size() - 1).area);
	}
}
