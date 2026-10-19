package com.energym.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.data.EnerGymDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.junit.Assert.*;

/** Nutrición por apartados desplegables: se pliegan con su cabecera y la elección sobrevive a recrear la pantalla. */
@RunWith(AndroidJUnit4.class)
public final class NutritionFoldsTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Test
	public void sectionsFoldFromTheirHeaderAndRememberIt() throws Exception {
		SharedPreferences prefs = context.getSharedPreferences(NutritionFragment.FOLDS, Context.MODE_PRIVATE);
		Map<String, ?> before = prefs.getAll();
		prefs.edit().clear().commit();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
		}
		String empty = context.getString(R.string.nut_nothing_today);
		String disclaimer = context.getString(R.string.nut_disclaimer);
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			for (int i = 0; i < 40; i++) {
				boolean[] ready = {false};
				s.onActivity(a -> ready[0] = a.state().app != null && a.state().accounts != null);
				if (ready[0])
					break;
				MainFlowTest.await();
			}
			s.onActivity(a -> a.state().accounts.enterDemo());
			MainFlowTest.await();
			s.onActivity(a -> a.state().app.navigate(AppController.Screen.NUTRITION));
			MainFlowTest.await();
			// Por defecto todo abierto menos «Objetivo, dieta del mes e IA».
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				for (String key : new String[]{"summary", "meals", "progress", "more"})
					assertNotNull("Cabecera " + key, root.findViewWithTag("nutrition_fold_" + key));
				assertTrue("Comidas abiertas", MainFlowTest.textContaining(root, empty));
				assertFalse("Más empieza plegado", MainFlowTest.textContaining(root, disclaimer));
			});
			header(s, "meals");
			s.onActivity(a -> assertFalse("Comidas plegadas", MainFlowTest.textContaining(a.getWindow().getDecorView(), empty)));
			assertFalse(prefs.getBoolean("meals", true));
			s.recreate();
			MainFlowTest.await();
			s.onActivity(a -> assertFalse("Sigue plegado al recrear",
				MainFlowTest.textContaining(a.getWindow().getDecorView(), empty)));
			header(s, "meals");
			header(s, "more");
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertTrue("Comidas otra vez abiertas", MainFlowTest.textContaining(root, empty));
				assertTrue("Más desplegado", MainFlowTest.textContaining(root, disclaimer));
			});
		} finally {
			SharedPreferences.Editor restore = prefs.edit().clear();
			for (Map.Entry<String, ?> e : before.entrySet())
				if (e.getValue() instanceof Boolean)
					restore.putBoolean(e.getKey(), (Boolean) e.getValue());
			restore.commit();
			try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
				db.clearLocalData();
			}
		}
	}

	private static void header(ActivityScenario<MainActivity> s, String key) {
		s.onActivity(a -> {
			View header = a.getWindow().getDecorView().findViewWithTag("nutrition_fold_" + key);
			assertNotNull(key, header);
			assertTrue(header.performClick());
		});
		MainFlowTest.await();
	}
}
