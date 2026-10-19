package com.energym.app.ui;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.NutritionRepository;
import com.energym.app.nutrition.FoodEntry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

import static org.junit.Assert.*;

/** Cada alimento del diario se edita o se borra con su propio botón, sin pasar por el menú de la fila. */
@RunWith(AndroidJUnit4.class)
public final class NutritionFoodActionsTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Test
	public void editAndDeleteButtonsActOnTheirOwnFood() throws Exception {
		LocalDate today = LocalDate.now();
		FoodEntry keep = FoodEntry.create(today, FoodEntry.Meal.LUNCH, "Arroz de prueba", "", FoodEntry.Unit.G, 150,
			130, 2.7, 28, 0.3);
		FoodEntry drop = FoodEntry.create(today, FoodEntry.Meal.DINNER, "Pollo de prueba", "", FoodEntry.Unit.G, 120,
			165, 31, 0, 3.6);
		context.getSharedPreferences(NutritionFragment.FOLDS, Context.MODE_PRIVATE).edit().clear().commit();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
			NutritionRepository repo = new NutritionRepository(db);
			repo.add(keep);
			repo.add(drop);
		}
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			for (int i = 0; i < 40; i++) {
				boolean[] ready = {false};
				s.onActivity(a -> ready[0] = a.state().app != null && a.state().accounts != null);
				if (ready[0])
					break;
				MainFlowTest.await();
			}
			s.onActivity(a -> a.state().accounts.enterDemo());
			boolean[] loaded = {false};
			for (int i = 0; i < 40 && !loaded[0]; i++) {
				MainFlowTest.await();
				s.onActivity(a -> loaded[0] = a.state().nutrition.entriesOn(today).size() == 2);
			}
			assertTrue("Los dos alimentos de prueba se cargan", loaded[0]);
			s.onActivity(a -> a.state().app.navigate(AppController.Screen.NUTRITION));
			MainFlowTest.await();

			// Editar abre ese mismo alimento para cambiarlo.
			s.onActivity(a -> {
				View edit = a.getWindow().getDecorView().findViewWithTag("food_edit_" + keep.id);
				assertNotNull("Botón de editar visible", edit);
				assertTrue(edit.isShown());
				assertEquals("Editar Arroz de prueba", edit.getContentDescription().toString());
				assertTrue(edit.performClick());
			});
			MainFlowTest.await();
			s.onActivity(a -> {
				assertEquals(AppController.Screen.FOOD, a.state().app.screen);
				assertTrue(a.state().nutrition.editingFood());
				assertEquals("Arroz de prueba", a.state().nutrition.foodDraft.get("name"));
				a.state().nutrition.cancelFood();
				a.state().app.navigate(AppController.Screen.NUTRITION);
			});
			MainFlowTest.await();

			// Borrar pide confirmación y solo quita ese alimento.
			s.onActivity(a -> {
				View delete = a.getWindow().getDecorView().findViewWithTag("food_delete_" + drop.id);
				assertNotNull("Botón de borrar visible", delete);
				assertTrue(delete.performClick());
			});
			MainFlowTest.await();
			s.onActivity(a -> assertEquals("Aún no se ha borrado nada", 2, a.state().nutrition.entriesOn(today).size()));
			confirm();
			boolean[] removed = {false};
			for (int i = 0; i < 40 && !removed[0]; i++) {
				MainFlowTest.await();
				s.onActivity(a -> removed[0] = a.state().nutrition.entriesOn(today).size() == 1);
			}
			assertTrue("Se borra el alimento confirmado", removed[0]);
			s.onActivity(a -> {
				assertEquals(keep.id, a.state().nutrition.entriesOn(today).get(0).id);
				assertNull(a.getWindow().getDecorView().findViewWithTag("food_delete_" + drop.id));
			});
		} finally {
			try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
				db.clearLocalData();
			}
		}
	}

	/** Pulsa «Quitar» en el diálogo de confirmación que esté abierto. */
	private static void confirm() throws Exception {
		android.app.UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
		android.view.accessibility.AccessibilityNodeInfo root = null;
		for (int i = 0; i < 30 && root == null; i++) {
			root = ui.getRootInActiveWindow();
			if (root == null)
				Thread.sleep(100);
		}
		assertNotNull("Diálogo de confirmación", root);
		java.util.List<android.view.accessibility.AccessibilityNodeInfo> buttons =
			root.findAccessibilityNodeInfosByViewId("android:id/button1");
		assertFalse("Botón de confirmar", buttons.isEmpty());
		assertTrue(buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
		MainFlowTest.await();
	}
}
