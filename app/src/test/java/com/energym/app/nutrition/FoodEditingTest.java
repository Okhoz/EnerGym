package com.energym.app.nutrition;

import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

/** Edición real del registro, sin duplicados ni pérdida de identidad al sincronizar. */
public final class FoodEditingTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	@Test public void editingPreservesIdentityDateAndRecalculatesTotalsWithoutAddingAnotherEntry() {
		Store store = new Store();
		FoodEntry original = entry("one", 150);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		assertTrue(c.editingFood());
		c.foodDraft.put("amount", "250");
		c.foodDraft.put("meal", "DINNER");
		c.foodDraft.put("name", "Arroz corregido");
		c.addFood(() -> {});
		assertEquals(0, store.adds);
		assertEquals(1, store.updates);
		assertEquals(1, c.entries.size());
		FoodEntry changed = c.entries.get(0);
		assertEquals(original.id, changed.id);
		assertEquals(original.createdAt, changed.createdAt);
		assertEquals(original.day, changed.day);
		assertEquals(FoodEntry.Meal.DINNER, changed.meal);
		assertEquals(900, c.totals(DAY)[0], 0.001);
		assertEquals(900, c.historyCalories(DAY), 0.001);
		assertFalse(c.editingFood());
	}

	@Test public void cancelOrSwitchingAccountCannotOverwriteThePreviousFood() {
		Store store = new Store();
		FoodEntry original = entry("one", 150);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		long scan = c.scanToken();
		c.cancelFood();
		assertFalse(c.editingFood());
		assertFalse(c.scanned("3017620422003", scan));
		assertEquals(0, store.updates);
		c.edit(original);
		c.activate("other");
		assertFalse(c.editingFood());
		assertTrue(c.foodDraft.isEmpty());
	}

	@Test public void aDifferentMealOrNewDayCannotMoveAnEditedHistoricalEntry() {
		Store store = new Store();
		FoodEntry original = entry("one", 100);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		c.selected = DAY.minusDays(1);
		c.foodDraft.put("amount", "200");
		c.addFood(() -> {});
		assertEquals(DAY, c.entries.get(0).day);
		assertEquals(720, c.historyCalories(DAY), 0.001);
	}

	@Test public void aReloadStartedBeforeTheEditCannotUndoTheSavedTotals() {
		Store store = new Store();
		FoodEntry original = entry("one", 100);
		store.entries.add(original);
		NutritionController c = controller(store);
		store.holdLoad = true;
		c.reload();
		NutritionStore.Snapshot stale = new NutritionStore.Snapshot(null, Collections.singletonList(original), new TreeMap<>());
		c.edit(original);
		c.foodDraft.put("amount", "200");
		c.addFood(() -> {});
		store.delayedLoad.success(stale);
		assertEquals(720, c.totals(DAY)[0], 0.001);
		assertEquals(720, c.historyCalories(DAY), 0.001);
	}

	private static FoodEntry entry(String id, double amount) {
		return new FoodEntry(id, DAY, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G,
			amount, 360, 7, 79, 1, 1000);
	}

	private static NutritionController controller(Store store) {
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY,
			() -> {}, message -> { throw new AssertionError(message); });
		c.activate("owner");
		return c;
	}

	private static final class Store implements NutritionStore {
		final List<FoodEntry> entries = new ArrayList<>();
		int adds, updates;
		boolean holdLoad;
		Callback<Snapshot> delayedLoad;
		public void load(String uid, LocalDate from, Callback<Snapshot> c) {
			if (holdLoad) { delayedLoad = c; return; }
			c.success(new Snapshot(null, entries, new TreeMap<>()));
		}
		public void saveBody(String uid, BodyProfile body, Callback<Void> c) { c.success(null); }
		public void add(String uid, FoodEntry entry, Callback<Void> c) { adds++; c.success(null); }
		public void update(String uid, FoodEntry entry, Callback<Void> c) {
			updates++;
			entries.removeIf(old -> old.id.equals(entry.id));
			entries.add(entry);
			c.success(null);
		}
		public void remove(String uid, String id, Callback<Void> c) { c.success(null); }
		public void weight(String uid, LocalDate day, double kg, Callback<Void> c) { c.success(null); }
	}
}
