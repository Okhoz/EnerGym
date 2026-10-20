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
import com.energym.app.domain.Routine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.junit.Assert.*;

/** Entreno por apartados desplegables, como Nutrición: se pliegan con su cabecera y se recuerda la elección. */
@RunWith(AndroidJUnit4.class)
public final class TrainingFoldsTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Test
	public void sectionsFoldFromTheirHeaderAndRememberIt() {
		SharedPreferences prefs = context.getSharedPreferences(TrainingFragment.FOLDS, Context.MODE_PRIVATE);
		Map<String, ?> before = prefs.getAll();
		prefs.edit().clear().commit();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
		}
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			MainFlowTest.click(s, "Cuenta");
			MainFlowTest.click(s, "Entrar en demo local");
			MainFlowTest.click(s, "Configurar mi perfil");
			MainFlowTest.click(s, "Guardar perfil");
			// Una rutina aceptada: ya no es la primera vez y el menú de crear pasa a plegarse.
			s.onActivity(a -> {
				Routine suggestion = a.state().app.suggest();
				assertNotNull("Sugerencia para crear la rutina", suggestion);
				a.state().app.acceptAndStart(suggestion, false);
			});
			for (int i = 0; i < 40; i++) {
				boolean[] accepted = {false};
				s.onActivity(a -> accepted[0] = a.state().app.routines.accepted != null);
				if (accepted[0])
					break;
				MainFlowTest.await();
			}
			s.onActivity(a -> a.state().app.navigate(AppController.Screen.TRAINING));
			MainFlowTest.await();
			MainFlowTest.trainingPane(s, "mine");
			String[] start = new String[1];
			s.onActivity(a -> {
				start[0] = a.getString(R.string.tr_start_workout);
				View root = a.getWindow().getDecorView();
				assertNotNull("Cabecera Hoy toca", root.findViewWithTag("training_today"));
				assertNotNull("Cabecera Crear", root.findViewWithTag("training_new"));
				assertNotNull("Hoy toca abierto", MainFlowTest.text(root, start[0]));
				assertNull("Crear empieza plegado", root.findViewWithTag("training_free"));
			});
			header(s, "training_new");
			s.onActivity(a -> assertNotNull("Crear desplegado",
				a.getWindow().getDecorView().findViewWithTag("training_free")));
			header(s, "training_today");
			s.onActivity(a -> assertNull("Hoy toca plegado",
				MainFlowTest.text(a.getWindow().getDecorView(), start[0])));
			assertFalse(prefs.getBoolean("today", true));
			assertTrue(prefs.getBoolean("new", false));
			s.recreate();
			MainFlowTest.await();
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertNull("Sigue plegado al recrear", MainFlowTest.text(root, start[0]));
				assertNotNull("Sigue desplegado al recrear", root.findViewWithTag("training_free"));
			});
			header(s, "training_today");
			s.onActivity(a -> assertNotNull("Hoy toca otra vez abierto",
				MainFlowTest.text(a.getWindow().getDecorView(), start[0])));
			// Ejercicios: la biblioteca también se pliega.
			MainFlowTest.trainingPane(s, "exercises");
			s.onActivity(a -> assertNotNull("Buscador visible",
				a.getWindow().getDecorView().findViewWithTag("exercise_search")));
			header(s, "training_library");
			s.onActivity(a -> assertNull("Biblioteca plegada",
				a.getWindow().getDecorView().findViewWithTag("exercise_search")));
			header(s, "training_library");
			s.onActivity(a -> assertNotNull("Biblioteca abierta",
				a.getWindow().getDecorView().findViewWithTag("exercise_search")));
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

	private static void header(ActivityScenario<MainActivity> s, String tag) {
		s.onActivity(a -> {
			View header = a.getWindow().getDecorView().findViewWithTag(tag);
			assertNotNull(tag, header);
			assertTrue(header.performClick());
		});
		MainFlowTest.await();
	}
}
