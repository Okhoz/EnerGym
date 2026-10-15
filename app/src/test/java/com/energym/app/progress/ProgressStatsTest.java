package com.energym.app.progress;

import com.energym.app.domain.*;

import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class ProgressStatsTest {
	private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15); // jueves

	private static Routine routine() {
		Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
		return new RoutinePlanner().create(new TrainingProfile.Builder().build(), Collections.singletonList(squat))
			.withVersionId(1);
	}

	private static Session finished(LocalDate day, int minutes, String kg) {
		long end = day.atTime(19, 0).atZone(ZONE).toInstant().toEpochMilli();
		Map<String, Map<String, String>> results = new HashMap<>();
		Map<String, String> r = new HashMap<>();
		r.put("sets", "3");
		r.put("repetitions", "10");
		r.put("loadKg", kg);
		r.put("restSeconds", "60");
		results.put("squat", r);
		return new Session(UUID.randomUUID().toString(), routine(), results, end - minutes * 60000L, end,
			Session.Status.FINISHED);
	}

	@Test
	public void countsWeeksDaysAndStreak() {
		List<Session> history = Arrays.asList(finished(TODAY, 40, "20"), finished(TODAY.minusDays(1), 30, "20"),
			finished(TODAY.minusWeeks(1), 50, "0"), finished(TODAY.minusWeeks(2), 45, "10"),
			finished(TODAY.minusWeeks(6), 60, "10"));
		ProgressStats s = new ProgressStats(history, ZONE, TODAY);
		double[] weeks = s.sessionsPerWeek();
		assertEquals(2, weeks[7], 0);
		assertEquals(1, weeks[6], 0);
		assertEquals(1, weeks[5], 0);
		assertEquals(1, weeks[1], 0);
		assertEquals(3, s.weekStreak());
		assertEquals(2, s.daysThisMonth() - (TODAY.minusWeeks(2).getMonth() == TODAY.getMonth() ? 2 : 0));
		assertEquals(70, s.minutesPerWeek()[7], 0.01);
		assertEquals("12/10", s.weekLabels()[7]);
		assertEquals(5, s.total());
		assertEquals(1, s.sessionsOn(TODAY.minusDays(1)).size());
	}

	@Test
	public void volumeIgnoresBodyweightAndTrendNeedsPreviousData() {
		assertEquals(600, ProgressStats.volume(finished(TODAY, 30, "20")), 0);
		assertEquals(0, ProgressStats.volume(finished(TODAY, 30, "0")), 0);
		ProgressStats onlyRecent = new ProgressStats(Collections.singletonList(finished(TODAY, 30, "5")), ZONE, TODAY);
		assertFalse(onlyRecent.sessionsTrend().comparable());
		ProgressStats both = new ProgressStats(Arrays.asList(finished(TODAY, 30, "5"),
			finished(TODAY.minusDays(1), 30, "5"), finished(TODAY.minusWeeks(6), 30, "5")), ZONE, TODAY);
		assertEquals(100, both.sessionsTrend().percent(), 0.01);
		assertArrayEquals(new double[]{150, 150, 150}, both.recentVolumes(5), 0);
	}

	@Test
	public void streakSurvivesOpenCurrentWeek() {
		ProgressStats s = new ProgressStats(Arrays.asList(finished(TODAY.minusWeeks(1), 30, "0"),
			finished(TODAY.minusWeeks(2), 30, "0")), ZONE, TODAY);
		assertEquals(2, s.weekStreak());
		assertEquals(0, new ProgressStats(Collections.singletonList(finished(TODAY.minusWeeks(3), 30, "0")), ZONE,
			TODAY).weekStreak());
	}
}
