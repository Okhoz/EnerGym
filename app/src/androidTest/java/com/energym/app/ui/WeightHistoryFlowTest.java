package com.energym.app.ui;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.nutrition.BodyProfile;
import com.energym.app.nutrition.FoodEntry;
import com.energym.app.nutrition.NutritionController;
import com.energym.app.nutrition.NutritionStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.*;

/** Historial de vista con almacén en memoria: nunca usa la cuenta ni la base de datos del dispositivo. */
@RunWith(AndroidJUnit4.class)
public final class WeightHistoryFlowTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	@Test
	public void paginationShowsEveryRecordedDayWithoutResettingAnUnsavedField() throws Exception {
		Store store = new Store();
		for (int day = 0; day < 11; day++) store.weights.put(TODAY.minusDays(day), 75.0 + day);
		NutritionController nutrition = controller(store);
		nutrition.fillBody();
		nutrition.bodyDraft.put("age", "31");
		Map<String, String> draft = new TreeMap<>(nutrition.bodyDraft);
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			scenario.onActivity(activity -> {
				ScreenFragment host = host(activity);
				LinearLayout field = host.card("Prueba de edición", "");
				host.textField(field, "unsaved_field", "Edad (años)", "31", true, true,
					value -> fail("Paginar no debe editar el formulario"));
				WeightHistorySection.render(host, nutrition);
				assertEquals(android.view.View.GONE,
					host.rows.findViewWithTag("health_weight_records").getVisibility());
				host.rows.findViewWithTag("health_weight_toggle").performClick();
				assertEquals(android.view.View.VISIBLE,
					host.rows.findViewWithTag("health_weight_records").getVisibility());
				assertNotNull(host.rows.findViewWithTag("health_weight_" + TODAY));
				assertNull(host.rows.findViewWithTag("health_weight_" + TODAY.minusDays(10)));
				TextView targets = host.rows.findViewWithTag("health_weight_targets");
				assertTrue(targets.getText().toString().contains(Math.round(nutrition.targets().kcal) + " kcal/día"));
				assertFalse(host.rows.findViewWithTag("health_weight_newer").isEnabled());
				host.rows.findViewWithTag("health_weight_older").performClick();
				assertNotNull(host.rows.findViewWithTag("health_weight_" + TODAY.minusDays(10)));
				assertNull(host.rows.findViewWithTag("health_weight_" + TODAY));
				assertFalse(host.rows.findViewWithTag("health_weight_older").isEnabled());
				host.rows.findViewWithTag("health_weight_newer").performClick();
				assertNotNull(host.rows.findViewWithTag("health_weight_" + TODAY));
				EditText unsaved = host.rows.findViewWithTag("unsaved_field");
				assertEquals("31", unsaved.getText().toString());
				assertEquals(draft, nutrition.bodyDraft);
				assertEquals(0, store.writes);
				host.rows.findViewWithTag("health_weight_toggle").performClick();
				assertEquals(android.view.View.GONE,
					host.rows.findViewWithTag("health_weight_records").getVisibility());
			});
		}
	}

	@Test
	public void emptyHistoryExplainsItsStateWithoutInventingMeasurements() throws Exception {
		Store store = new Store();
		NutritionController nutrition = controller(store);
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			scenario.onActivity(activity -> {
				ScreenFragment host = host(activity);
				WeightHistorySection.render(host, nutrition);
				assertNotNull(host.rows.findViewWithTag("health_weight_empty"));
				assertNull(host.rows.findViewWithTag("health_weight_" + TODAY));
				assertNull(host.rows.findViewWithTag("health_weight_older"));
				assertTrue(nutrition.weights.isEmpty());
				assertEquals(0, store.writes);
			});
		}
	}

	private static ScreenFragment host(MainActivity activity) {
		return (ScreenFragment) activity.getSupportFragmentManager().findFragmentById(R.id.content);
	}

	private static void ready(ActivityScenario<MainActivity> scenario) throws Exception {
		boolean[] loaded = {false};
		for (int attempt = 0; attempt < 100 && !loaded[0]; attempt++) {
			InstrumentationRegistry.getInstrumentation().waitForIdleSync();
			scenario.onActivity(activity -> loaded[0] = activity.state().app != null &&
				activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ScreenFragment);
			if (!loaded[0]) Thread.sleep(50);
		}
		assertTrue("La vista debe estar preparada", loaded[0]);
	}

	private static NutritionController controller(Store store) {
		NutritionController nutrition = new NutritionController(store, (code, callback) -> callback.success(null),
			() -> TODAY, () -> {}, issue -> fail(issue));
		nutrition.activate("memory-only-test-owner");
		return nutrition;
	}

	private static final class Store implements NutritionStore {
		final SortedMap<LocalDate, Double> weights = new TreeMap<>();
		int writes;
		public void load(String owner, LocalDate from, Callback<Snapshot> callback) {
			BodyProfile body = new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80,
				BodyProfile.Activity.MODERATE, BodyProfile.Goal.MAINTAIN, "");
			callback.success(new Snapshot(body, Collections.emptyList(), weights));
		}
		public void saveBody(String owner, BodyProfile body, Callback<Void> callback) { writes++; fail("Vista de solo lectura"); }
		public void weight(String owner, LocalDate day, double kg, Callback<Void> callback) { writes++; fail("Vista de solo lectura"); }
		public void add(String owner, FoodEntry food, Callback<Void> callback) { writes++; fail("Vista de solo lectura"); }
		public void remove(String owner, String id, Callback<Void> callback) { writes++; fail("Vista de solo lectura"); }
	}
}
