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
}
