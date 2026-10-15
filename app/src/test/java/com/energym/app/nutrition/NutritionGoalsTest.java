package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** Objetivos calculados sobre el perfil confirmado, con borrador independiente del diario. */
public final class NutritionGoalsTest {
	private static BodyProfile profile(BodyProfile.Goal goal) {
		return new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80, BodyProfile.Activity.MODERATE, goal, "");
	}

	@Test public void threeGoalsReuseMifflinAndTheExistingEnergyAdjustments() {
		assertEquals(1780, profile(BodyProfile.Goal.MAINTAIN).basal(), 0.001);
		assertEquals(2759, profile(BodyProfile.Goal.MAINTAIN).targets().kcal, 0);
		assertEquals(2345, profile(BodyProfile.Goal.LOSE).targets().kcal, 0);
		assertEquals(3035, profile(BodyProfile.Goal.GAIN).targets().kcal, 0);
		for (BodyProfile.Goal goal : BodyProfile.Goal.values()) {
			BodyProfile.Targets targets = profile(goal).targets();
			assertTrue(targets.protein > 0 && targets.carbs >= 0 && targets.fat > 0);
			assertEquals(targets.kcal, targets.protein * 4 + targets.carbs * 4 + targets.fat * 9, 7);
		}
	}

	@Test public void legacyMinorProfileRemainsReadableButDoesNotProduceAutomaticTargets() {
		BodyProfile minor = new BodyProfile(BodyProfile.Sex.FEMALE, 17, 165, 60,
			BodyProfile.Activity.MODERATE, BodyProfile.Goal.LOSE, "");
		assertEquals(17, minor.age);
		assertNull(minor.targets());
		assertFalse(minor.estimationIssue().isEmpty());
		assertNotNull(new BodyProfile(BodyProfile.Sex.FEMALE, 18, 165, 60,
			BodyProfile.Activity.MODERATE, BodyProfile.Goal.MAINTAIN, "").targets());
	}

	@Test public void lowEnergyOrInconsistentMacrosAreNotPresentedAsAValidPlan() {
		BodyProfile tooLow = new BodyProfile(BodyProfile.Sex.FEMALE, 100, 120, 30,
			BodyProfile.Activity.SEDENTARY, BodyProfile.Goal.LOSE, "");
		BodyProfile incoherent = new BodyProfile(BodyProfile.Sex.MALE, 100, 120, 300,
			BodyProfile.Activity.SEDENTARY, BodyProfile.Goal.LOSE, "");
		for (BodyProfile body : Arrays.asList(tooLow, incoherent)) {
			assertNull(body.targets());
			assertFalse(body.estimationIssue().isEmpty());
		}
	}

	@Test public void choosingGoalPreviewsMacrosWithoutChangingSavedProfileAndCancelWritesNothing() {
		Store store = new Store();
		NutritionController c = controller(store);
		c.fillBody();
		c.selectNutritionGoal(BodyProfile.Goal.LOSE);
		assertEquals(2345, c.previewTargets().kcal, 0);
		assertEquals(2759, c.targets().kcal, 0);
		assertEquals(BodyProfile.Goal.MAINTAIN, c.body.goal);
		assertEquals(0, store.writes);
		c.cancelBody();
		assertTrue(c.bodyDraft.isEmpty());
		assertEquals(BodyProfile.Goal.MAINTAIN, c.body.goal);
		assertEquals(0, store.writes);
	}

	@Test public void confirmingGoalPersistsProfileAndRecalculatesTargetsOnlyAfterSuccess() {
		Store store = new Store();
		NutritionController c = controller(store);
		c.fillBody(); c.selectNutritionGoal(BodyProfile.Goal.GAIN);
		c.saveBody(() -> {});
		assertEquals(2, store.writes);
		assertEquals(BodyProfile.Goal.GAIN, store.body.goal);
		assertEquals(3035, c.targets().kcal, 0);
	}

	@Test public void incompleteDraftHasNoTargetsAndAccountSwitchDropsThePendingGoal() {
		Store store = new Store();
		NutritionController c = controller(store);
		c.fillBody(); c.bodyDraft.put("weightKg", "");
		assertNull(c.previewTargets());
		assertFalse(c.bodyPreviewIssue().isEmpty());
		c.activate("other");
		assertTrue(c.bodyDraft.isEmpty());
		assertEquals(0, store.writes);
	}

	@Test public void newWeightsRecalculateCaloriesAndAllMacrosWithoutChangingTheGoal() {
		for (BodyProfile.Goal goal : Arrays.asList(BodyProfile.Goal.LOSE, BodyProfile.Goal.GAIN)) {
			Store store = new Store();
			store.body = profile(goal);
			NutritionController c = controller(store);
			BodyProfile.Targets before = c.targets();
			c.logWeight(goal == BodyProfile.Goal.LOSE ? "75" : "85");
			BodyProfile.Targets after = c.targets();
			assertEquals(goal, c.effectiveBody().goal);
			assertEquals(goal, store.body.goal);
			assertEquals(LocalDate.of(2026, 10, 15), c.targetWeightDay());
			int direction = goal == BodyProfile.Goal.LOSE ? -1 : 1;
			assertTrue(direction * (after.kcal - before.kcal) > 0);
			assertTrue(direction * (after.protein - before.protein) > 0);
			assertTrue(direction * (after.carbs - before.carbs) > 0);
			assertTrue(direction * (after.fat - before.fat) > 0);
			assertEquals(2, store.writes); // Un peso y la actualización contractual del perfil, sin duplicados.
			c.reload(); c.targets(); c.targets(); c.fillBody(); c.previewTargets();
			assertEquals(2, store.writes);
		}
	}

	@Test public void restoreUsesLatestValidNonFutureWeightAndIgnoresTheSelectedDiaryDay() {
		Store store = new Store();
		LocalDate today = LocalDate.of(2026, 10, 15);
		store.weights.put(today.minusDays(10), 60.0);
		store.weights.put(today.minusDays(1), 77.0);
		store.weights.put(today, Double.NaN);
		store.weights.put(today.plusDays(1), 200.0);
		NutritionController c = controller(store);
		assertEquals(80, c.body.weightKg, 0); // Copia antigua del perfil: no se reescribe al leer.
		assertEquals(77, c.effectiveBody().weightKg, 0);
		assertEquals(today.minusDays(1), c.targetWeightDay());
		double kcal = c.targets().kcal;
		c.select(today.minusDays(60));
		assertEquals(kcal, c.targets().kcal, 0);
		assertEquals(0, store.writes);
		NutritionController afterRestart = controller(store);
		assertEquals(kcal, afterRestart.targets().kcal, 0);
		assertEquals(77, afterRestart.effectiveBody().weightKg, 0);
	}

	@Test public void changingAccountClearsTheWeightReferenceAndDropsALateWeightCallback() {
		AtomicReference<NutritionStore.Callback<Void>> delayed = new AtomicReference<>();
		Store store = new Store() {
			public void weight(String uid, LocalDate day, double kg, Callback<Void> callback) { writes++; delayed.set(callback); }
			public void load(String uid, LocalDate from, Callback<Snapshot> callback) {
				if (uid.equals("other")) callback.success(new Snapshot(null, Collections.emptyList(), new TreeMap<>()));
				else super.load(uid, from, callback);
			}
		};
		NutritionController c = controller(store);
		c.logWeight("75");
		c.activate("other");
		delayed.get().success(null);
		assertNull(c.targets());
		assertNull(c.targetWeightDay());
		assertTrue(c.weights.isEmpty());
		assertEquals(1, store.writes);
	}

	@Test public void unavailableAutomaticTargetDoesNotBlockTheFoodDiary() {
		Store store = new Store() {
			public void add(String uid, FoodEntry entry, Callback<Void> callback) { writes++; callback.success(null); }
		};
		store.body = new BodyProfile(BodyProfile.Sex.FEMALE, 17, 165, 60, BodyProfile.Activity.MODERATE,
			BodyProfile.Goal.LOSE, "");
		List<String> errors = new ArrayList<>();
		NutritionController c = new NutritionController(store, (code, callback) -> callback.success(null),
			() -> LocalDate.of(2026, 10, 15), () -> {}, errors::add);
		c.activate("owner"); c.fillBody(); c.saveBody(() -> {});
		assertEquals(0, store.writes);
		assertEquals(1, errors.size());
		c.startFrom(FoodEntry.create(c.today(), FoodEntry.Meal.LUNCH, "Alimento", "", FoodEntry.Unit.G,
			100, 200, 10, 20, 2));
		c.addFood(() -> {});
		assertEquals(1, store.writes);
		assertEquals(200, c.totals(c.today())[0], 0);
		assertNull(c.targets());
	}

	@Test public void aReloadStartedBeforeLoggingWeightCannotUndoTheNewReference() {
		AtomicReference<NutritionStore.Callback<NutritionStore.Snapshot>> delayed = new AtomicReference<>();
		boolean[] hold = {false};
		Store store = new Store() {
			public void load(String uid, LocalDate from, Callback<Snapshot> callback) {
				if (hold[0]) delayed.set(callback);
				else super.load(uid, from, callback);
			}
		};
		LocalDate today = LocalDate.of(2026, 10, 15);
		store.weights.put(today.minusDays(1), 80.0);
		NutritionController c = controller(store);
		NutritionStore.Snapshot stale = new NutritionStore.Snapshot(store.body, Collections.emptyList(), store.weights);
		hold[0] = true; c.reload(); c.logWeight("75");
		delayed.get().success(stale);
		assertEquals(75, c.effectiveBody().weightKg, 0);
		assertEquals(today, c.targetWeightDay());
		assertEquals(2, store.writes);
	}

	@Test public void nutritionCoachBriefUsesConfirmedTargetsAndFoodTotalsWithoutWriting() {
		Store store = new Store();
		NutritionController c = controller(store);
		c.fillBody(); c.selectNutritionGoal(BodyProfile.Goal.LOSE); // Borrador sin confirmar.
		c.entries = Collections.singletonList(FoodEntry.create(c.today(), FoodEntry.Meal.LUNCH, "Comida", "",
			FoodEntry.Unit.G, 150, 200, 20, 10, 5));
		String brief = c.coachBrief();
		assertTrue(brief.contains("Mantenimiento"));
		assertFalse(brief.contains("Déficit calórico"));
		assertTrue(brief.contains("2759 kcal/día"));
		assertTrue(brief.contains("300 kcal"));
		assertTrue(brief.contains("proteínas 30 g"));
		assertTrue(brief.length() <= 2000);
		assertEquals(0, store.writes);
	}

	@Test public void coachBriefWithoutProfileDoesNotInventCalorieTargets() {
		Store store = new Store();
		store.body = null;
		String brief = controller(store).coachBrief();
		assertTrue(brief.contains("completar mis datos físicos"));
		assertFalse(brief.contains("kcal/día"));
		assertEquals(0, store.writes);
	}

	private static NutritionController controller(Store store) {
		NutritionController c = new NutritionController(store, (code, callback) -> callback.success(null),
			() -> LocalDate.of(2026, 10, 15), () -> {}, message -> { throw new AssertionError(message); });
		c.activate("owner");
		return c;
	}

	private static class Store implements NutritionStore {
		BodyProfile body = profile(BodyProfile.Goal.MAINTAIN);
		final SortedMap<LocalDate, Double> weights = new TreeMap<>();
		int writes;
		public void load(String uid, LocalDate from, Callback<Snapshot> c) {
			c.success(new Snapshot(body, Collections.emptyList(), weights));
		}
		public void saveBody(String uid, BodyProfile value, Callback<Void> c) { writes++; body = value; c.success(null); }
		public void weight(String uid, LocalDate day, double kg, Callback<Void> c) { writes++; weights.put(day, kg); c.success(null); }
		public void add(String uid, FoodEntry value, Callback<Void> c) { throw new AssertionError(); }
		public void remove(String uid, String id, Callback<Void> c) { throw new AssertionError(); }
	}
}
