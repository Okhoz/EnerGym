package com.energym.app.nutrition;

import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

/** «Tu progreso» de Nutrición: calorías por día o por semana, días en objetivo y medias de cada macro. */
public final class NutritionProgressTest {
	/** Jueves. */
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
	private static final BodyProfile BODY = new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80,
		BodyProfile.Activity.MODERATE, BodyProfile.Goal.MAINTAIN, "");

	@Test
	public void weekShowsEachDayAgainstTheTargetAndAveragesOnlyLoggedDays() {
		double target = BODY.targets().kcal;
		Store store = new Store();
		// Hoy, justo el objetivo; anteayer, la mitad; el resto de la semana sin anotar.
		store.entries.add(food("hoy", TODAY, target, 30, 50, 10));
		store.entries.add(food("anteayer", TODAY.minusDays(2), target / 2, 10, 20, 6));
		NutritionController.Progress p = controller(store).progress(7);
		assertEquals(7, p.bars.length);
		assertEquals(target, p.bars[6], 0.01);
		assertEquals(target / 2, p.bars[4], 0.01);
		assertEquals(0, p.bars[0], 0.01);
		assertEquals("Inicial del día en el idioma de la app", "J", p.labels[6]);
		assertEquals("X", p.labels[5]);
		assertEquals(2, p.logged);
		assertEquals("Solo hoy está a menos de un 10 % del objetivo", 1, p.onTarget);
		assertEquals("La media no cuenta los días sin anotar", target * 0.75, p.kcal, 0.01);
		assertEquals(20, p.protein, 0.01);
		assertEquals(35, p.carbs, 0.01);
		assertEquals(8, p.fat, 0.01);
	}

	@Test
	public void fourWeeksShowTheDailyAverageOfEachWeek() {
		Store store = new Store();
		// Semana más reciente: 2000 y 1000 kcal (media 1500). La primera de las cuatro: 1800.
		store.entries.add(food("a", TODAY, 2000, 100, 200, 60));
		store.entries.add(food("b", TODAY.minusDays(3), 1000, 50, 100, 30));
		store.entries.add(food("c", TODAY.minusDays(27), 1800, 90, 180, 50));
		NutritionController.Progress p = controller(store).progress(NutritionController.HISTORY_DAYS);
		assertEquals(4, p.bars.length);
		assertEquals(1800, p.bars[0], 0.01);
		assertEquals(0, p.bars[1], 0.01);
		assertEquals(0, p.bars[2], 0.01);
		assertEquals(1500, p.bars[3], 0.01);
		LocalDate first = TODAY.minusDays(27);
		assertEquals("Cada barra lleva el día en que empieza su semana",
			first.getDayOfMonth() + "/" + first.getMonthValue(), p.labels[0]);
		assertEquals(3, p.logged);
		assertEquals(28, p.days);
		assertEquals((2000 + 1000 + 1800) / 3.0, p.kcal, 0.01);
	}

	@Test
	public void nothingLoggedMeansNothingToAverage() {
		NutritionController.Progress p = controller(new Store()).progress(7);
		assertEquals(0, p.logged);
		assertEquals(0, p.onTarget);
		assertEquals(0, p.kcal, 0);
		assertEquals(7, p.bars.length);
	}

	/** Un kilo de un alimento que en total aporta esos valores (por 100 g, la décima parte). */
	private static FoodEntry food(String id, LocalDate day, double kcal, double protein, double carbs, double fat) {
		return new FoodEntry(id, day, FoodEntry.Meal.LUNCH, "Comida " + id, "", FoodEntry.Unit.G, 1000, kcal / 10,
			protein / 10, carbs / 10, fat / 10, 1000);
	}

	private static NutritionController controller(Store store) {
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> TODAY,
			() -> {}, message -> { throw new AssertionError(message); });
		c.activate("owner");
		return c;
	}

	private static final class Store implements NutritionStore {
		final List<FoodEntry> entries = new ArrayList<>();

		public void load(String uid, LocalDate from, Callback<Snapshot> c) {
			c.success(new Snapshot(BODY, entries, new TreeMap<>()));
		}

		public void saveBody(String uid, BodyProfile body, Callback<Void> c) { c.success(null); }

		public void add(String uid, FoodEntry entry, Callback<Void> c) { c.success(null); }

		public void update(String uid, FoodEntry entry, Callback<Void> c) { c.success(null); }

		public void remove(String uid, String id, Callback<Void> c) { c.success(null); }

		public void weight(String uid, LocalDate day, double kg, Callback<Void> c) { c.success(null); }
	}
}
