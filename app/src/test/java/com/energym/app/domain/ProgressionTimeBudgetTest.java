package com.energym.app.domain;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProgressionTimeBudgetTest {
	private Routine routine(MetricType type, int available) {
		TrainingProfile profile = new TrainingProfile.Builder().durationMinutes(available).level("Intermedio").build();
		Exercise exercise = new Exercise("fixture", "Fixture", type, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Fixture", Collections.emptyList());
		Map<String, String> prescription = new LinkedHashMap<>();
		prescription.put("sets", type == MetricType.STRENGTH ? "4" : "3");
		prescription.put("restSeconds", type == MetricType.STRENGTH ? "90" : "60");
		if (type == MetricType.STRENGTH) {
			prescription.put("repetitions", "15");
			prescription.put("loadKg", "0");
		} else
			prescription.put("durationSeconds", "60");
		return new Routine("fixture-base", 1, "Fixture", profile,
			Collections.singletonList(new RoutineItem(exercise, prescription, 5, "Fixture")), "Fixture");
	}

	private ProgressionEngine.Plan next(MetricType type, int minutes) {
		Routine routine = routine(type, minutes);
		Session finished = new Session("fixture-session", routine,
			Collections.singletonMap("fixture", routine.getItems().get(0).getPrescription()), 100, 200, Session.Status.FINISHED);
		return new ProgressionEngine().next(routine, Collections.singletonList(finished));
	}

	@Test public void strengthDoesNotAddSetsWhoseRestCannotFit() {
		assertNull(next(MetricType.STRENGTH, 5));
	}

	@Test public void strengthReservesTheAdditionalRestWhenSetsIncrease() {
		ProgressionEngine.Plan next = next(MetricType.STRENGTH, 6);
		assertNotNull(next);
		assertEquals("5", next.routine.getItems().get(0).getPrescription().get("sets"));
		assertEquals(6, next.routine.getEstimatedMinutes());
	}

	@Test public void isometricDoesNotIncreaseSecondsBeyondTheAvailableBudget() {
		assertNull(next(MetricType.ISOMETRIC, 5));
	}

	@Test public void isometricReservesTheNewDurationAndRests() {
		ProgressionEngine.Plan next = next(MetricType.ISOMETRIC, 6);
		assertNotNull(next);
		assertEquals("70", next.routine.getItems().get(0).getPrescription().get("durationSeconds"));
		assertEquals(6, next.routine.getEstimatedMinutes());
	}

	@Test public void holdingVolumeForTimeKeepsTheZeroLoadActuallyPerformed() {
		Routine bodyweight = routine(MetricType.STRENGTH, 5);
		Exercise loaded = new Exercise("fixture", "Fixture", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			EnumSet.of(TrainingProfile.Resource.BARBELL), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Fixture", Collections.emptyList());
		Map<String, String> target = new LinkedHashMap<>(bodyweight.getItems().get(0).getPrescription());
		target.put("loadKg", "60");
		Routine accepted = new Routine("fixture-loaded", 1, "Fixture",
			bodyweight.getProfile().withResources(EnumSet.of(TrainingProfile.Resource.BARBELL)),
			Collections.singletonList(new RoutineItem(loaded, target, 5, "Fixture")), "Fixture");
		Map<String, String> completed = new LinkedHashMap<>(target);
		completed.put("loadKg", "0");
		Session finished = new Session("fixture-session", accepted, Collections.singletonMap("fixture", completed),
			100, 200, Session.Status.FINISHED);
		ProgressionEngine.Plan next = new ProgressionEngine().next(accepted, Collections.singletonList(finished));
		assertNotNull(next);
		assertEquals("0", next.routine.getItems().get(0).getPrescription().get("loadKg"));
		assertEquals("4", next.routine.getItems().get(0).getPrescription().get("sets"));
		assertEquals(5, next.routine.getEstimatedMinutes());
	}
}
