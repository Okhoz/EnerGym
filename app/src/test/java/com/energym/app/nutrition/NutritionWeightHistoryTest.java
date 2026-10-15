package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** El historial visible usa sólo pesos confirmados y conserva todos los días registrados. */
public final class NutritionWeightHistoryTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	@Test public void historyIsNewestFirstAndKeepsOlderValidWeights() {
		Store store = new Store();
		store.weights.put(TODAY.minusDays(420), 90.0);
		store.weights.put(TODAY.minusDays(1), 78.5);
		store.weights.put(TODAY, 78.0);
		store.weights.put(TODAY.minusDays(2), Double.NaN);
		store.weights.put(TODAY.minusDays(3), 301.0);
		store.weights.put(TODAY.plusDays(1), 77.0);
		NutritionController c = controller(store, new ArrayList<>());
		SortedMap<LocalDate, Double> history = c.weightHistory();
		assertEquals(Arrays.asList(TODAY, TODAY.minusDays(1), TODAY.minusDays(420)),
			new ArrayList<>(history.keySet()));
		assertEquals(Arrays.asList(78.0, 78.5, 90.0), new ArrayList<>(history.values()));
		assertEquals(TODAY, c.targetWeightDay());
		assertEquals(78, c.effectiveBody().weightKg, 0);
	}

	@Test public void historyIsAReadOnlySnapshotRatherThanAnEditableAccountMap() {
		Store store = new Store();
		store.weights.put(TODAY.minusDays(1), 80.0);
		NutritionController c = controller(store, new ArrayList<>());
		SortedMap<LocalDate, Double> history = c.weightHistory();
		try {
			history.put(TODAY, 75.0);
			fail("El historial no debe permitir cambiar pesos sin guardarlos.");
		} catch (UnsupportedOperationException expected) {
			// La vista recibe una instantánea; las mutaciones pasan por el controlador.
		}
		c.logWeight("75");
		assertEquals(Collections.singletonList(TODAY.minusDays(1)), new ArrayList<>(history.keySet()));
		assertEquals(Arrays.asList(TODAY, TODAY.minusDays(1)), new ArrayList<>(c.weightHistory().keySet()));
	}

	@Test public void anotherWeightTodayUpdatesOneRowAndKeepsTheRestOfTheProfile() {
		Store store = new Store();
		store.weights.put(TODAY.minusDays(1), 80.0);
		store.weights.put(TODAY, 79.0);
		NutritionController c = controller(store, new ArrayList<>());
		c.logWeight("75");
		assertEquals(2, c.weightHistory().size());
		assertEquals(75, c.weightHistory().get(TODAY), 0);
		assertEquals(80, c.weightHistory().get(TODAY.minusDays(1)), 0);
		BodyProfile current = c.effectiveBody();
		assertEquals(BodyProfile.Goal.LOSE, current.goal);
		assertEquals(BodyProfile.Sex.MALE, current.sex);
		assertEquals(30, current.age);
		assertEquals(180, current.heightCm, 0);
		assertEquals(BodyProfile.Activity.MODERATE, current.activity);
		assertEquals("Frutos secos", current.allergies);
		assertEquals(2279, c.targets().kcal, 0);
		assertEquals(150, c.targets().protein, 0);
		assertEquals(278, c.targets().carbs, 0);
		assertEquals(63, c.targets().fat, 0);
		c.reload();
		assertEquals(75, c.weightHistory().get(TODAY), 0);
		assertEquals(2, c.weightHistory().size());
	}

	@Test public void loggingFromAnOlderDiaryDayAddsTodayWithoutReplacingThatOlderWeight() {
		Store store = new Store();
		LocalDate oldDay = TODAY.minusDays(90);
		store.weights.put(oldDay, 85.0);
		NutritionController c = controller(store, new ArrayList<>());
		c.select(oldDay);
		c.logWeight("75");
		assertEquals(Arrays.asList(TODAY, oldDay), new ArrayList<>(c.weightHistory().keySet()));
		assertEquals(85, c.weightHistory().get(oldDay), 0);
		assertEquals(75, c.weightHistory().get(TODAY), 0);
		assertEquals(TODAY, c.targetWeightDay());
	}

	@Test public void failedWeightWriteDoesNotInventAHistoryRowOrChangeTargets() {
		Store store = new Store() {
			public void weight(String uid, LocalDate day, double kg, Callback<Void> callback) {
				callback.failure(new IllegalStateException("No se pudo guardar"));
			}
		};
		store.weights.put(TODAY.minusDays(1), 80.0);
		List<String> errors = new ArrayList<>();
		NutritionController c = controller(store, errors);
		c.logWeight("75");
		assertEquals(Collections.singletonList(TODAY.minusDays(1)), new ArrayList<>(c.weightHistory().keySet()));
		assertEquals(80, c.effectiveBody().weightKg, 0);
		assertEquals(2345, c.targets().kcal, 0);
		assertFalse(c.busy);
		assertEquals(1, errors.size());
		assertTrue(c.status.isEmpty());
	}

	private static NutritionController controller(Store store, List<String> errors) {
		NutritionController c = new NutritionController(store, (code, callback) -> callback.success(null),
			() -> TODAY, () -> {}, errors::add);
		c.activate("owner");
		return c;
	}

	private static class Store implements NutritionStore {
		BodyProfile body = new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80,
			BodyProfile.Activity.MODERATE, BodyProfile.Goal.LOSE, "Frutos secos");
		final SortedMap<LocalDate, Double> weights = new TreeMap<>();
		public void load(String uid, LocalDate from, Callback<Snapshot> callback) {
			callback.success(new Snapshot(body, Collections.emptyList(), weights));
		}
		public void saveBody(String uid, BodyProfile value, Callback<Void> callback) {
			body = value;
			callback.success(null);
		}
		public void weight(String uid, LocalDate day, double kg, Callback<Void> callback) {
			weights.put(day, kg);
			callback.success(null);
		}
		public void add(String uid, FoodEntry value, Callback<Void> callback) { throw new AssertionError(); }
		public void remove(String uid, String id, Callback<Void> callback) { throw new AssertionError(); }
	}
}
