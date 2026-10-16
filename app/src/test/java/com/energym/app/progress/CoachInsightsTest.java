package com.energym.app.progress;

import com.energym.app.domain.*;

import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class CoachInsightsTest {
	private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

	private static Exercise exercise(String id, String name) {
		return new Exercise(id, name, MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
	}

	private static Session session(String id, LocalDate day, Exercise e, List<SetLog.Entry> sets, String note) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("sets", "3");
		p.put("repetitions", "8");
		p.put("loadKg", "0");
		p.put("restSeconds", "60");
		Routine r = new Routine("r-" + id, 1, "Rutina", new TrainingProfile.Builder().build(),
			Collections.singletonList(new RoutineItem(e, p, 5, "x")), "x");
		Map<String, String> base = new HashMap<>();
		if (note != null)
			base.put(SetLog.NOTE, note);
		Map<String, Map<String, String>> results = new LinkedHashMap<>();
		results.put(e.getId(), SetLog.summary(sets, base, "60"));
		long at = day.atTime(18, 0).atZone(ZONE).toInstant().toEpochMilli();
		return new Session(id, r, results, at - 3600_000, at, Session.Status.FINISHED);
	}

	private static List<SetLog.Entry> sets(double kg, double rpe) {
		return Arrays.asList(new SetLog.Entry(kg, 8, SetLog.Kind.NORMAL, rpe), new SetLog.Entry(kg, 8, SetLog.Kind.NORMAL, rpe),
			new SetLog.Entry(kg, 8, SetLog.Kind.NORMAL, rpe));
	}

	@Test
	public void spotsStallsHighEffortNeglectedGroupsAndPainNotes() {
		Exercise squat = exercise("bodyweight-squat", "Sentadilla");
		LocalDate today = LocalDate.of(2026, 10, 16);
		List<Session> history = Arrays.asList(
			session("a", today.minusDays(6), squat, sets(80, 8), null),
			session("b", today.minusDays(4), squat, sets(80, 9.5), null),
			session("c", today.minusDays(2), squat, sets(80, 9.5), "Me molestó la rodilla al bajar"));
		List<String> insights = CoachInsights.analyze(history, ZONE, today);
		String all = String.join("\n", insights);
		assertTrue(all, all.contains("Estancado (3 sesiones sin mejorar): Sentadilla"));
		assertTrue(all, all.contains("Esfuerzo muy alto"));
		assertTrue(all, all.contains(CoachInsights.NEGLECTED) && all.contains("pecho") && !all.contains("piernas,"));
		assertTrue(all, all.contains("⚠ Anotó molestias: Sentadilla"));
	}

	@Test
	public void longBreakAndEasySessionsAreReported() {
		Exercise squat = exercise("bodyweight-squat", "Sentadilla");
		LocalDate today = LocalDate.of(2026, 10, 16);
		List<String> insights = CoachInsights.analyze(Arrays.asList(
			session("a", today.minusDays(12), squat, sets(60, 6), null),
			session("b", today.minusDays(10), squat, sets(62.5, 6), "Buen día")), ZONE, today);
		String all = String.join("\n", insights);
		assertTrue(all, all.contains("Lleva 10 días sin entrenar"));
		assertTrue(all, all.contains("Va sobrado"));
		assertTrue(all, all.contains("Sus notas: Sentadilla"));
		assertFalse(all, all.contains("Estancado"));
		assertTrue(CoachInsights.analyze(Collections.emptyList(), ZONE, today).isEmpty());
	}
}
