package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class OverloadCoachTest {
	private static List<SetLog.Entry> sets(double kg, int... reps) {
		List<SetLog.Entry> result = new ArrayList<>();
		for (int r : reps)
			result.add(new SetLog.Entry(kg, r));
		return result;
	}

	@SafeVarargs
	private static List<List<SetLog.Entry>> history(List<SetLog.Entry>... sessions) {
		return Arrays.asList(sessions);
	}

	@Test
	public void withoutHistoryThereIsNoSuggestion() {
		assertNull(OverloadCoach.suggest(null, 8, 12, 2.5));
		assertNull(OverloadCoach.suggest(Collections.emptyList(), 8, 12, 2.5));
		assertNull("Solo calentamiento", OverloadCoach.suggest(history(
			Collections.singletonList(new SetLog.Entry(40, 10, SetLog.Kind.WARMUP, 0))), 8, 12, 2.5));
	}

	@Test
	public void invalidArgumentsReturnNull() {
		assertNull(OverloadCoach.suggest(history(sets(60, 8)), 0, 12, 2.5));
		assertNull(OverloadCoach.suggest(history(sets(60, 8)), 12, 8, 2.5));
		assertNull(OverloadCoach.suggest(history(sets(60, 8)), 8, 12, 0));
	}

	@Test
	public void allSetsAtTopOfRangeIncreaseLoadAndResetReps() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 12, 12, 12)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.INCREASE_LOAD, s.kind);
		assertEquals(62.5, s.kg, 0.001);
		assertEquals(8, s.reps);
		assertEquals(60, s.lastKg, 0.001);
		assertEquals(12, s.lastReps);
	}

	@Test
	public void heavyLoadsIncreaseByAboutTwoAndAHalfPercent() {
		assertEquals(205, OverloadCoach.suggest(history(sets(200, 5, 5, 5)), 3, 5, 2.5).kg, 0.001);
	}

	@Test
	public void dumbbellIncrementRoundsToStep() {
		assertEquals(22, OverloadCoach.suggest(history(sets(20, 12, 12)), 8, 12, 2).kg, 0.001);
	}

	@Test
	public void oneSetShortOfTopAddsARepAtSameLoad() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 12, 11, 10)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.ADD_REPS, s.kind);
		assertEquals(60, s.kg, 0.001);
		assertEquals(11, s.reps);
	}

	@Test
	public void addRepsNeverExceedsTop() {
		assertEquals(12, OverloadCoach.suggest(history(sets(60, 12, 11)), 8, 12, 2.5).reps);
	}

	@Test
	public void failingBelowRangeHoldsLoad() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 8, 6, 5)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.HOLD, s.kind);
		assertEquals(60, s.kg, 0.001);
		assertEquals(8, s.reps);
	}

	@Test
	public void warmupAndDropSetsAreIgnored() {
		List<SetLog.Entry> day = new ArrayList<>();
		day.add(new SetLog.Entry(80, 3, SetLog.Kind.WARMUP, 0));
		day.addAll(sets(60, 12, 12));
		day.add(new SetLog.Entry(40, 6, SetLog.Kind.DROP, 0));
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(day), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.INCREASE_LOAD, s.kind);
		assertEquals(62.5, s.kg, 0.001);
	}

	@Test
	public void usesOnlySetsAtTopLoad() {
		// Series de aproximación más ligeras no frenan la progresión de la carga de trabajo.
		List<SetLog.Entry> day = new ArrayList<>(sets(50, 6));
		day.addAll(sets(60, 12, 12));
		assertEquals(OverloadCoach.Kind.INCREASE_LOAD, OverloadCoach.suggest(history(day), 8, 12, 2.5).kind);
	}

	@Test
	public void threeSessionsWithoutProgressSuggestDeload() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 9, 9), sets(60, 9, 9), sets(60, 9, 8)),
			8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.DELOAD, s.kind);
		assertEquals(55, s.kg, 0.001);
		assertEquals(8, s.reps);
		assertEquals(3, s.stalled);
	}

	@Test
	public void twoStalledSessionsStillProgressNormally() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 9, 9), sets(60, 9, 9)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.ADD_REPS, s.kind);
		assertEquals(2, s.stalled);
	}

	@Test
	public void progressBreaksTheStall() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(
			history(sets(60, 9), sets(60, 9), sets(60, 10), sets(60, 10)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.ADD_REPS, s.kind);
		assertEquals(2, s.stalled);
	}

	@Test
	public void afterDeloadTheCountRestarts() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(
			history(sets(60, 9), sets(60, 9), sets(60, 9), sets(55, 8)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.ADD_REPS, s.kind);
		assertEquals(55, s.kg, 0.001);
		assertEquals(0, s.stalled);
	}

	@Test
	public void stalledFailuresAlsoDeload() {
		assertEquals(OverloadCoach.Kind.DELOAD, OverloadCoach.suggest(
			history(sets(100, 4), sets(100, 4), sets(100, 3)), 6, 8, 2.5).kind);
	}

	@Test
	public void reachingTopWinsOverStall() {
		// Si por fin completa el tope, toca subir aunque las sesiones previas fueran iguales.
		assertEquals(OverloadCoach.Kind.INCREASE_LOAD, OverloadCoach.suggest(
			history(sets(60, 12), sets(60, 12), sets(60, 12)), 8, 12, 2.5).kind);
	}

	@Test
	public void deloadNeverGoesBelowOneStep() {
		assertEquals(2.5, OverloadCoach.suggest(history(sets(2.5, 9), sets(2.5, 9), sets(2.5, 9)), 8, 12, 2.5).kg,
			0.001);
	}

	@Test
	public void bodyweightProgressesRepetitions() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(0, 15, 14, 14)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.ADD_REPS, s.kind);
		assertEquals(0, s.kg, 0.001);
		assertEquals(15, s.reps);
		OverloadCoach.Suggestion low = OverloadCoach.suggest(history(sets(0, 5, 4)), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.HOLD, low.kind);
		assertEquals(4, low.reps);
	}

	@Test
	public void bodyweightNeverDeloads() {
		assertNotEquals(OverloadCoach.Kind.DELOAD, OverloadCoach.suggest(
			history(sets(0, 10), sets(0, 10), sets(0, 10)), 8, 12, 2.5).kind);
	}

	@Test
	public void emptySessionsInHistoryAreSkipped() {
		OverloadCoach.Suggestion s = OverloadCoach.suggest(history(sets(60, 12, 12), Collections.emptyList()), 8, 12, 2.5);
		assertEquals(OverloadCoach.Kind.INCREASE_LOAD, s.kind);
	}

	@Test
	public void rangesFollowTheTarget() {
		assertArrayEquals(new int[]{3, 5}, OverloadCoach.range(1));
		assertArrayEquals(new int[]{3, 5}, OverloadCoach.range(5));
		assertArrayEquals(new int[]{6, 8}, OverloadCoach.range(6));
		assertArrayEquals(new int[]{8, 12}, OverloadCoach.range(10));
		assertArrayEquals(new int[]{12, 15}, OverloadCoach.range(20));
	}

	@Test
	public void incrementDependsOnEquipment() {
		assertEquals(2.5, OverloadCoach.increment(EnumSet.of(TrainingProfile.Resource.BARBELL, TrainingProfile.Resource.BENCH)), 0.001);
		assertEquals(2.5, OverloadCoach.increment(EnumSet.of(TrainingProfile.Resource.MACHINE)), 0.001);
		assertEquals(2, OverloadCoach.increment(EnumSet.of(TrainingProfile.Resource.DUMBBELLS)), 0.001);
		assertEquals(1, OverloadCoach.increment(EnumSet.of(TrainingProfile.Resource.BANDS)), 0.001);
		assertEquals(1, OverloadCoach.increment(null), 0.001);
	}
}
