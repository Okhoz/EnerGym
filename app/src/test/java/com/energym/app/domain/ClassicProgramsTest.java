package com.energym.app.domain;

import com.energym.app.visual.TestCatalog;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ClassicProgramsTest {
	@Test
	public void everyExerciseOfEveryProgramExistsInTheCatalog() throws Exception {
		Set<String> ids = new HashSet<>();
		for (Exercise e : TestCatalog.exercises())
			ids.add(e.getId());
		for (ClassicPrograms.Program p : ClassicPrograms.Program.values())
			for (ClassicPrograms.Day d : ClassicPrograms.days(p))
				for (ClassicPrograms.Slot s : d.slots)
					assertTrue(p + " · " + s.exerciseId, ids.contains(s.exerciseId));
	}

	@Test
	public void programsHaveTheirClassicNumberOfDays() {
		assertEquals(4, ClassicPrograms.days(ClassicPrograms.Program.FIVE_THREE_ONE).size());
		assertEquals(3, ClassicPrograms.days(ClassicPrograms.Program.PUSH_PULL_LEGS).size());
		assertEquals(2, ClassicPrograms.days(ClassicPrograms.Program.FULL_BODY).size());
		assertEquals(4, ClassicPrograms.days(ClassicPrograms.Program.GZCLP).size());
	}

	@Test
	public void loadUsesNinetyPercentTrainingMaxRoundedToStep() {
		// 1RM 100 → TM 90 → 85 % = 76,5 → 77,5 con saltos de 2,5.
		assertEquals(77.5, ClassicPrograms.load(100, 0.85, 2.5), 0.001);
		assertEquals(45, ClassicPrograms.load(100, 0.5, 2.5), 0.001);
		assertEquals(0, ClassicPrograms.load(0, 0.85, 2.5), 0.001);
		assertEquals(0, ClassicPrograms.load(100, 0, 2.5), 0.001);
		assertEquals(2.5, ClassicPrograms.load(1, 0.5, 2.5), 0.001);
	}

	@Test
	public void routineBuildsValidPrescriptionsWithLoadsFromBestLifts() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		Map<String, Double> best = Collections.singletonMap("barbell-back-squat", 120.0);
		Routine r = ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, 0, catalog, best,
			new TrainingProfile.Builder().build());
		assertNotNull(r);
		assertTrue(r.getTitle().startsWith("GZCLP"));
		assertTrue(r.isProgressionEnabled());
		RoutineItem squat = r.getItems().get(0);
		assertEquals("barbell-back-squat", squat.getExercise().getId());
		assertEquals("5", squat.getPrescription().get("sets"));
		assertEquals("3", squat.getPrescription().get("repetitions"));
		assertEquals("92.5", squat.getPrescription().get("loadKg"));
		assertEquals("0", r.getItems().get(1).getPrescription().get("loadKg"));
		MetricValidator validator = new MetricValidator();
		for (ClassicPrograms.Program p : ClassicPrograms.Program.values())
			for (int d = 0; d < ClassicPrograms.days(p).size(); d++)
				for (RoutineItem item : ClassicPrograms.routine(p, d, catalog, best,
					new TrainingProfile.Builder().build()).getItems())
					assertTrue(p + " " + item.getExercise().getId(),
						validator.validate(item.getExercise().getMetricType(), item.getPrescription()).isEmpty());
	}

	@Test
	public void isometricSlotsUseDuration() throws Exception {
		Routine r = ClassicPrograms.routine(ClassicPrograms.Program.FULL_BODY, 0, TestCatalog.exercises(), null,
			new TrainingProfile.Builder().build());
		RoutineItem plank = r.getItems().get(r.getItems().size() - 1);
		assertEquals("forearm-plank", plank.getExercise().getId());
		assertEquals("30", plank.getPrescription().get("durationSeconds"));
	}

	@Test
	public void invalidDayOrEmptyCatalogGiveNull() {
		TrainingProfile profile = new TrainingProfile.Builder().build();
		assertNull(ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, 9, Collections.emptyList(), null, profile));
		assertNull(ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, -1, Collections.emptyList(), null, profile));
		assertNull(ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, 0, Collections.emptyList(), null, profile));
		assertNull(ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, 0, null, null, profile));
	}

	@Test
	public void minutesCountRestsAndWork() {
		// 5 series: 5 × 40 s + 4 × 180 s = 920 s → 16 min.
		assertEquals(16, ClassicPrograms.minutes(ClassicPrograms.days(ClassicPrograms.Program.GZCLP).get(0).slots.get(0)));
	}
}
