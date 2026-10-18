package com.energym.app.progress;

import com.energym.app.domain.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ShareSummaryTest {
	private static final long DAY = 86_400_000L, START = 1_760_000_000_000L;

	private static Routine routine() {
		Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
		return new RoutinePlanner().create(new TrainingProfile.Builder().build(), Collections.singletonList(squat))
			.withVersionId(1);
	}

	private static Session session(int day, int minutes, String sets, String reps, String kg) {
		long end = START + day * DAY;
		Map<String, Map<String, String>> results = new HashMap<>();
		Map<String, String> r = new HashMap<>();
		r.put("sets", sets);
		r.put("repetitions", reps);
		r.put("loadKg", kg);
		results.put("squat", r);
		return new Session(UUID.randomUUID().toString(), routine(), results, end - minutes * 60000L, end,
			Session.Status.FINISHED);
	}

	@Test
	public void summarisesExercisesVolumeAndDuration() {
		Session s = session(0, 45, "3", "10", "20");
		ShareSummary summary = ShareSummary.of(s, Collections.singletonList(s));
		assertEquals(s.getRoutine().getTitle(), summary.title);
		assertEquals(45, summary.minutes);
		assertEquals(600, summary.volumeKg, 0);
		assertEquals(1, summary.lines.size());
		ShareSummary.Line line = summary.lines.get(0);
		assertEquals("Sentadilla", line.name);
		assertEquals(3, line.sets);
		assertEquals(10, line.topReps);
		assertEquals(20, line.topKg, 0);
		assertFalse("La primera vez no es récord", summary.hasRecord());
	}

	@Test
	public void detectsRecordAgainstEarlierSessionsOnly() {
		Session old = session(0, 30, "3", "10", "20");
		Session better = session(1, 30, "3", "10", "25");
		Session later = session(2, 30, "3", "10", "40");
		List<Session> history = Arrays.asList(old, better, later);
		ShareSummary summary = ShareSummary.of(better, history);
		assertTrue(summary.hasRecord());
		assertEquals(Collections.singletonList("Sentadilla"), summary.records());
		assertFalse("Igualar no es batir", ShareSummary.of(session(3, 30, "3", "10", "40"), history).hasRecord());
	}

	@Test
	public void bodyweightRecordCountsMoreReps() {
		Session before = session(0, 20, "3", "10", "0");
		Session now = session(1, 20, "3", "12", "0");
		ShareSummary summary = ShareSummary.of(now, Arrays.asList(before, now));
		assertTrue(summary.hasRecord());
		assertEquals(0, summary.volumeKg, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void draftsCannotBeShared() {
		ShareSummary.of(new Session(routine()), Collections.emptyList());
	}
}
