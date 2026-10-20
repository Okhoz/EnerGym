package com.energym.app.progress;

import com.energym.app.domain.*;

import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class GoalsTest {
	private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

	private static Routine routine() {
		Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
		return new RoutinePlanner().create(new TrainingProfile.Builder().build(), Collections.singletonList(squat))
			.withVersionId(1);
	}

	private static Session session(LocalDate day, int minutes, String kg) {
		long end = day.atTime(19, 0).atZone(ZONE).toInstant().toEpochMilli();
		Map<String, String> r = new HashMap<>();
		r.put("sets", "3");
		r.put("repetitions", "10");
		r.put("loadKg", kg);
		r.put("restSeconds", "60");
		return new Session(UUID.randomUUID().toString(), routine(), Collections.singletonMap("squat", r),
			end - minutes * 60000L, end, Session.Status.FINISHED);
	}

	private final ProgressStats stats = new ProgressStats(Arrays.asList(session(TODAY.minusWeeks(3), 30, "40"),
		session(TODAY.minusDays(1), 45, "50"), session(TODAY, 30, "55")), ZONE, TODAY);

	@Test
	public void totalsAndImprovementsCountEverythingDone() {
		assertEquals(105, stats.totalMinutes(), 0.01);
		assertEquals(3 * 10 * (40 + 50 + 55), stats.totalVolume(), 0.01);
		assertEquals(3, stats.exercisesDone());
		assertArrayEquals(new double[]{40, 50, 55}, stats.loadHistory("squat"), 0);
		ProgressStats.Improvement best = stats.improvements().get(0);
		assertEquals(15, best.gain(), 0);
		assertEquals(3150, stats.volumePerWeek()[7], 0.01);
	}

	@Test
	public void weeklyLoadAndWeightGoalsMeasureRealProgress() {
		GoalProgress sessions = GoalProgress.of(Goal.create(Goal.Type.SESSIONS_PER_WEEK, 4, 0, null, null), stats,
			new TreeMap<>());
		assertEquals(2, sessions.current, 0);
		assertEquals(0.5, sessions.ratio, 0.001);
		assertEquals(8, sessions.series.length);
		GoalProgress load = GoalProgress.of(Goal.create(Goal.Type.EXERCISE_LOAD, 50, 0, "squat", "Sentadilla"), stats,
			new TreeMap<>());
		assertTrue(load.reached);
		assertEquals(1, load.ratio, 0);
		SortedMap<LocalDate, Double> weights = new TreeMap<>();
		weights.put(TODAY.minusDays(10), 80.0);
		weights.put(TODAY, 77.0);
		GoalProgress lose = GoalProgress.of(Goal.create(Goal.Type.TARGET_WEIGHT, 75, 80, null, null), stats, weights);
		assertEquals(0.6, lose.ratio, 0.001);
		GoalProgress gain = GoalProgress.of(Goal.create(Goal.Type.TARGET_WEIGHT, 82, 80, null, null), stats, weights);
		assertEquals(0, gain.ratio, 0);
		assertThrows(IllegalArgumentException.class, () -> Goal.create(Goal.Type.SESSIONS_PER_WEEK, 0, 0, null, null));
		assertThrows(IllegalArgumentException.class, () -> Goal.create(Goal.Type.EXERCISE_LOAD, 50, 0, "", ""));
	}

	@Test
	public void controllerValidatesSavesAndRemoves() {
		List<Goal> saved = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		GoalsController c = new GoalsController(new GoalsController.Store() {
			public void load(String owner, Callback<List<Goal>> callback) {
				callback.success(new ArrayList<>(saved));
			}

			public void save(String owner, Goal goal, Callback<Void> callback) {
				saved.add(goal);
				callback.success(null);
			}

			public void remove(String owner, String goalId, Callback<Void> callback) {
				saved.removeIf(g -> g.id.equals(goalId));
				callback.success(null);
			}
		}, () -> {
		}, errors::add);
		c.activate("uid");
		c.startNew();
		c.draft.put("target", "tres");
		c.save(0, null);
		assertEquals(1, errors.size());
		c.draft.put("target", "3");
		c.save(0, null);
		assertFalse(c.editing);
		assertEquals(1, c.goals.size());
		c.startNew();
		c.draft.put("type", "TARGET_WEIGHT");
		c.draft.put("target", "74,5");
		c.save(78, null);
		assertEquals(78, saved.get(1).start, 0);
		assertEquals(74.5, saved.get(1).target, 0);
		c.remove(c.goals.get(0));
		assertEquals(1, saved.size());
		assertEquals(1, c.goals.size());
	}

	@Test
	public void customGoalCountsTheLatestLoggedValueInEitherDirection() {
		Goal books = Goal.custom("Leer libros", "libros", 12, 0);
		GoalProgress empty = GoalProgress.of(books, stats, new TreeMap<>());
		assertEquals(0, empty.ratio, 0);
		assertEquals(0, empty.series.length);
		Goal logged = books.withEntry(TODAY.minusDays(3), 2).withEntry(TODAY, 3).withEntry(TODAY, 6);
		assertEquals(2, logged.entries.size());
		GoalProgress p = GoalProgress.of(logged, stats, new TreeMap<>());
		assertEquals(6, p.current, 0);
		assertEquals(0.5, p.ratio, 0.001);
		assertArrayEquals(new double[]{2, 6}, p.series, 0);
		assertEquals("Leer libros: 12 libros", logged.title());
		// Bajar un tiempo: de 30 a 25 min, ahora 27.
		Goal run = Goal.custom("5 km", "min", 25, 30).withEntry(TODAY, 27);
		assertEquals(0.6, GoalProgress.of(run, stats, new TreeMap<>()).ratio, 0.001);
		assertTrue(GoalProgress.of(run.withEntry(TODAY, 24), stats, new TreeMap<>()).reached);
		assertEquals(1, logged.withoutEntry(TODAY).entries.size());
		assertThrows(IllegalArgumentException.class, () -> Goal.custom(" ", "", 10, 0));
		assertThrows(IllegalArgumentException.class, () -> Goal.custom("Igual", "", 5, 5));
		assertThrows(IllegalStateException.class,
			() -> Goal.create(Goal.Type.SESSIONS_PER_WEEK, 3, 0, null, null).withEntry(TODAY, 1));
	}

	@Test
	public void completingKeepsTheGoalAndReopeningBringsItBack() {
		List<Goal> saved = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		GoalsController c = new GoalsController(new GoalsController.Store() {
			public void load(String owner, Callback<List<Goal>> callback) {
				callback.success(new ArrayList<>(saved));
			}

			public void save(String owner, Goal goal, Callback<Void> callback) {
				saved.removeIf(g -> g.id.equals(goal.id));
				saved.add(goal);
				callback.success(null);
			}

			public void remove(String owner, String goalId, Callback<Void> callback) {
				saved.removeIf(g -> g.id.equals(goalId));
				callback.success(null);
			}
		}, () -> {
		}, errors::add);
		c.today = () -> TODAY;
		c.clock = () -> 1_000L;
		c.activate("uid");
		c.startNew();
		c.draft.put("type", "CUSTOM");
		c.draft.put("name", "Dominadas seguidas");
		c.draft.put("target", "15");
		c.draft.put("start", "4");
		c.save(0, null);
		assertTrue(errors.toString(), errors.isEmpty());
		Goal goal = c.goals.get(0);
		assertEquals(4, goal.start, 0);
		c.startLogging(goal);
		c.progressDraft = "muchas";
		c.logProgress(goal);
		assertEquals(1, errors.size());
		assertEquals(goal.id, c.logging);
		c.progressDraft = "8,5";
		c.logProgress(goal);
		assertNull(c.logging);
		assertEquals(8.5, c.goals.get(0).entries.get(TODAY), 0);
		assertEquals(1, saved.size());
		c.complete(c.goals.get(0));
		assertTrue(c.active().isEmpty());
		assertEquals(1, c.completed().size());
		assertEquals(1_000L, saved.get(0).completedAt);
		assertEquals(8.5, saved.get(0).entries.get(TODAY), 0);
		c.reopen(c.completed().get(0));
		assertEquals(1, c.active().size());
		assertFalse(saved.get(0).completed());
		c.removeEntry(c.goals.get(0), TODAY);
		assertTrue(c.goals.get(0).entries.isEmpty());
	}
}
