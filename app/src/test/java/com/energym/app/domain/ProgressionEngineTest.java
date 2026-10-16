package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ProgressionEngineTest {
	private static Exercise exercise(String id, MetricType type, TrainingProfile.Resource... resources) {
		Set<TrainingProfile.Resource> r = resources.length == 0 ? Collections.emptySet() :
			EnumSet.copyOf(Arrays.asList(resources));
		return new Exercise(id, id, type, EnumSet.of(TrainingProfile.Goal.STRENGTH, TrainingProfile.Goal.GENERAL_FITNESS,
			TrainingProfile.Goal.ENDURANCE), EnumSet.of(TrainingProfile.Environment.GYM), r,
			Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
	}

	private static TrainingProfile profile(TrainingProfile.Goal goal, int minutes) {
		return new TrainingProfile.Builder().goal(goal).environment(TrainingProfile.Environment.GYM)
			.category(TrainingProfile.Category.GYM)
			.resources(EnumSet.of(TrainingProfile.Resource.BARBELL)).durationMinutes(minutes).level("Intermedio").build();
	}

	private static Map<String, String> map(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2)
			m.put(kv[i], kv[i + 1]);
		return m;
	}

	private static Routine routine(TrainingProfile p, RoutineItem... items) {
		return new Routine("base", 1, "Fuerza", p, Arrays.asList(items), "Base");
	}

	private static Session done(Routine r, String id, Map<String, String> result) {
		long now = System.currentTimeMillis();
		return new Session(UUID.randomUUID().toString(), r, Collections.singletonMap(id, result), now - 3600000, now,
			Session.Status.FINISHED);
	}

	private final Exercise squat = exercise("squat", MetricType.STRENGTH, TrainingProfile.Resource.BARBELL);

	@Test
	public void completedLoadedSetsAddRepsUntilCeilingThenLoad() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Routine r = routine(p, new RoutineItem(squat, map("sets", "3", "repetitions", "6", "loadKg", "60", "restSeconds", "120"),
			10, "Base"));
		ProgressionEngine.Plan plan = new ProgressionEngine().next(r,
			Collections.singletonList(done(r, "squat", map("sets", "3", "repetitions", "6", "loadKg", "60", "effort", "7"))));
		assertEquals("7", plan.routine.getItems().get(0).getPrescription().get("repetitions"));
		assertEquals("60", plan.routine.getItems().get(0).getPrescription().get("loadKg"));
		assertTrue(plan.routine.getTitle().endsWith("nivel 2"));
		Routine top = routine(p, new RoutineItem(squat, map("sets", "3", "repetitions", "8", "loadKg", "60", "restSeconds", "120"),
			10, "Base"));
		// Intermedio: una sola sesión en el techo consolida; la carga sube tras dos seguidas con esfuerzo asumible.
		assertNull(new ProgressionEngine().next(top, Collections.singletonList(
			done(top, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60")))));
		List<Session> twice = Arrays.asList(daysAgo(top, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60",
			"effort", "7"), 3), done(top, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60", "effort", "6")));
		ProgressionEngine.Plan loaded = new ProgressionEngine().next(top, twice);
		Map<String, String> next = loaded.routine.getItems().get(0).getPrescription();
		assertEquals("62.5", next.get("loadKg"));
		assertEquals("5", next.get("repetitions"));
		assertTrue(loaded.changes.get(0).contains("kg 60 → 62,5"));
		assertTrue("Explica el motivo", loaded.changes.get(0).contains("dos sesiones seguidas"));
	}

	@Test
	public void effortEightAddsRepsButNeverLoad() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Routine top = routine(p, new RoutineItem(squat,
			map("sets", "3", "repetitions", "8", "loadKg", "60", "restSeconds", "120"), 10, "Base"));
		assertNull(new ProgressionEngine().next(top, Arrays.asList(
			daysAgo(top, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60", "effort", "7"), 3),
			done(top, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60", "effort", "8")))));
		Routine low = routine(p, new RoutineItem(squat,
			map("sets", "3", "repetitions", "6", "loadKg", "60", "restSeconds", "120"), 10, "Base"));
		assertEquals("7", new ProgressionEngine().next(low, Collections.singletonList(
			done(low, "squat", map("sets", "3", "repetitions", "6", "loadKg", "60", "effort", "8"))))
			.routine.getItems().get(0).getPrescription().get("repetitions"));
	}

	private static Session daysAgo(Routine r, String id, Map<String, String> result, int days) {
		long at = System.currentTimeMillis() - days * 86400000L;
		return new Session(UUID.randomUUID().toString(), r, Collections.singletonMap(id, result), at - 3600000, at,
			Session.Status.FINISHED);
	}

	@Test
	public void hardOrMissedSessionsHoldOrDeload() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Routine r = routine(p, new RoutineItem(squat, map("sets", "3", "repetitions", "8", "loadKg", "60", "restSeconds", "120"),
			10, "Base"));
		assertNull(new ProgressionEngine().next(r, Collections.singletonList(
			done(r, "squat", map("sets", "3", "repetitions", "8", "loadKg", "60", "effort", "9.5")))));
		// Un mal día se repite; dos seguidos por debajo de lo previsto bajan un 10 %.
		assertNull(new ProgressionEngine().next(r, Collections.singletonList(
			done(r, "squat", map("sets", "2", "repetitions", "5", "loadKg", "60")))));
		ProgressionEngine.Plan deloaded = new ProgressionEngine().next(r, Arrays.asList(
			daysAgo(r, "squat", map("sets", "3", "repetitions", "5", "loadKg", "60"), 4),
			done(r, "squat", map("sets", "2", "repetitions", "5", "loadKg", "60"))));
		assertEquals("55", deloaded.routine.getItems().get(0).getPrescription().get("loadKg"));
		assertTrue(deloaded.changes.get(0).contains("descarga"));
		assertNull(new ProgressionEngine().next(r, Collections.emptyList()));
	}

	@Test
	public void repetitionProgressionUsesTheLoadActuallyCompleted() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Routine r = routine(p, new RoutineItem(squat,
			map("sets", "3", "repetitions", "6", "loadKg", "60", "restSeconds", "120"), 10, "Base"));
		for (String completedLoad : new String[]{"30", "80", "0"}) {
			ProgressionEngine.Plan plan = new ProgressionEngine().next(r, Collections.singletonList(
				done(r, "squat", map("sets", "3", "repetitions", "6", "loadKg", completedLoad, "effort", "7"))));
			assertNotNull(plan);
			Map<String, String> next = plan.routine.getItems().get(0).getPrescription();
			assertEquals("Carga realizada " + completedLoad, completedLoad, next.get("loadKg"));
			assertEquals("0".equals(completedLoad) ? "8" : "7", next.get("repetitions"));
		}
	}

	@Test
	public void zeroLoadRemainsExplicitWhenTheTargetWasNotCompleted() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Routine r = routine(p, new RoutineItem(squat,
			map("sets", "3", "repetitions", "6", "loadKg", "60", "restSeconds", "120"), 10, "Base"));
		for (Map<String, String> result : Arrays.asList(
			map("sets", "3", "repetitions", "6", "loadKg", "0", "effort", "9"),
			map("sets", "2", "repetitions", "6", "loadKg", "0", "effort", "7"),
			map("sets", "3", "repetitions", "5", "loadKg", "0", "effort", "7"))) {
			ProgressionEngine.Plan plan = new ProgressionEngine().next(r, Collections.singletonList(done(r, "squat", result)));
			assertNotNull(plan);
			Map<String, String> next = plan.routine.getItems().get(0).getPrescription();
			assertEquals("0", next.get("loadKg"));
			assertTrue(Integer.parseInt(next.get("repetitions")) <= 6);
		}
	}

	@Test
	public void isometricProgressionRequiresAllPrescribedSetsAndDuration() {
		TrainingProfile p = profile(TrainingProfile.Goal.STRENGTH, 30);
		Exercise plank = exercise("plank", MetricType.ISOMETRIC);
		Routine r = routine(p, new RoutineItem(plank,
			map("sets", "3", "durationSeconds", "30", "restSeconds", "60"), 5, "Base"));
		assertNull(new ProgressionEngine().next(r, Collections.singletonList(
			done(r, "plank", map("sets", "1", "durationSeconds", "30", "effort", "7")))));
		assertNull(new ProgressionEngine().next(r, Collections.singletonList(
			done(r, "plank", map("sets", "3", "durationSeconds", "20", "effort", "7")))));
		ProgressionEngine.Plan plan = new ProgressionEngine().next(r, Collections.singletonList(
			done(r, "plank", map("sets", "3", "durationSeconds", "30", "effort", "7"))));
		assertNotNull(plan);
		assertEquals("40", plan.routine.getItems().get(0).getPrescription().get("durationSeconds"));
	}

	@Test
	public void bodyweightAndEnduranceProgressWithinAvailableTime() {
		TrainingProfile p = profile(TrainingProfile.Goal.GENERAL_FITNESS, 30);
		Exercise push = exercise("push", MetricType.STRENGTH);
		Exercise run = exercise("run", MetricType.ENDURANCE);
		Routine r = routine(p, new RoutineItem(push, map("sets", "2", "repetitions", "10", "loadKg", "0", "restSeconds", "60"),
			5, "Base"), new RoutineItem(run, map("timeMinutes", "20", "intervals", "1"), 23, "Base"));
		long now = System.currentTimeMillis();
		Map<String, Map<String, String>> results = new LinkedHashMap<>();
		results.put("push", map("sets", "2", "repetitions", "10", "loadKg", "0"));
		results.put("run", map("timeMinutes", "20", "intervals", "1"));
		Session s = new Session("s", r, results, now - 3600000, now, Session.Status.FINISHED);
		ProgressionEngine.Plan plan = new ProgressionEngine().next(r, Collections.singletonList(s));
		assertEquals("12", plan.routine.getItems().get(0).getPrescription().get("repetitions"));
		assertEquals("22", plan.routine.getItems().get(1).getPrescription().get("timeMinutes"));
		assertTrue(plan.routine.getEstimatedMinutes() <= 30);
		assertTrue(plan.routine.isValidFor(p));
	}
}
