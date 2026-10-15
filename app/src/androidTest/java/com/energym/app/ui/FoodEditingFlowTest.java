package com.energym.app.ui;

import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.nutrition.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Sólo un controlador de memoria: prohíbe escrituras y conserva la sesión Google abierta. */
@RunWith(AndroidJUnit4.class)
public final class FoodEditingFlowTest {
	@Test public void blankNameAndRecreationNeverOfferAddShortcutsDuringEditing() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			boolean[] ready = {false};
			for (int attempt = 0; attempt < 30 && !ready[0]; attempt++) {
				scenario.onActivity(activity -> ready[0] = activity.state().app != null &&
					!activity.state().app.loading && activity.state().app.initialDataReady && activity.state().nutrition != null);
				if (!ready[0]) MainFlowTest.await();
			}
			Assume.assumeTrue("La prueba utiliza una cuenta ya abierta, sin iniciar ni cerrar sesión.", ready[0]);
			AtomicReference<NutritionController> previous = new AtomicReference<>();
			AtomicReference<NutritionController> fixture = new AtomicReference<>();
			AppController.Screen[] previousScreen = {null};
			String[] owner = {null};
			try {
				scenario.onActivity(activity -> {
					AppController app = activity.state().app;
					Assume.assumeTrue(app.owner() != null && !activity.state().nutrition.busy && !activity.state().nutrition.looking);
					owner[0] = app.owner();
					previous.set(activity.state().nutrition);
					previousScreen[0] = app.screen;
					FoodEntry food = FoodEntry.create(LocalDate.now(), FoodEntry.Meal.LUNCH, "Alimento manual", "",
						FoodEntry.Unit.G, 100, 200, 20, 10, 5);
					NutritionController n = new NutritionController(new MemoryStore(food),
						(code, callback) -> { throw new AssertionError("La edición no consulta productos"); },
						LocalDate::now, app::changed, message -> { throw new AssertionError(message); });
					fixture.set(n);
					activity.state().nutrition = n;
					n.activate(owner[0]);
					n.edit(food);
					n.foodDraft.put("name", "");
					n.foodDraft.put("unit", "ML");
					app.navigate(AppController.Screen.FOOD);
				});
				MainFlowTest.await();
				assertEditing(scenario, fixture.get());
				scenario.recreate();
				MainFlowTest.await();
				assertEditing(scenario, fixture.get());
			} finally {
				scenario.onActivity(activity -> {
					if (fixture.get() != null && activity.state().nutrition == fixture.get()) {
						activity.state().nutrition = previous.get();
						if (Objects.equals(owner[0], activity.state().app.owner()))
							activity.state().app.navigate(previousScreen[0]);
						else previous.get().activate(activity.state().app.owner());
					}
				});
			}
		}
	}

	private void assertEditing(ActivityScenario<MainActivity> scenario, NutritionController expected) {
		scenario.onActivity(activity -> {
			assertSame(expected, activity.state().nutrition);
			assertTrue(expected.editingFood());
			assertEquals("", expected.foodDraft.get("name"));
			assertEquals("ML", expected.foodDraft.get("unit"));
			View root = activity.getWindow().getDecorView();
			assertNotNull(MainFlowTest.text(root, "Guardar cambios"));
			assertNull("Un atajo de alta convertiría la edición en un registro nuevo", MainFlowTest.text(root, "Recientes"));
			assertNull(MainFlowTest.text(root, "Escanear código de barras"));
		});
	}

	private static final class MemoryStore implements NutritionStore {
		private final FoodEntry food;
		MemoryStore(FoodEntry food) { this.food = food; }
		public void load(String owner, LocalDate from, Callback<Snapshot> c) {
			c.success(new Snapshot(null, Collections.singletonList(food), new TreeMap<>()));
		}
		public void saveBody(String owner, BodyProfile body, Callback<Void> c) { throw new AssertionError("Sin escrituras"); }
		public void add(String owner, FoodEntry entry, Callback<Void> c) { throw new AssertionError("Sin escrituras"); }
		public void update(String owner, FoodEntry entry, Callback<Void> c) { throw new AssertionError("Sin escrituras"); }
		public void remove(String owner, String id, Callback<Void> c) { throw new AssertionError("Sin escrituras"); }
		public void weight(String owner, LocalDate day, double kg, Callback<Void> c) { throw new AssertionError("Sin escrituras"); }
	}
}
