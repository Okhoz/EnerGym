package com.energym.app.ui;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.progress.GoalsController;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Progreso › Metas: crear una meta personal, apuntar el avance, marcarla como cumplida y reabrirla. */
@RunWith(AndroidJUnit4.class)
public final class GoalsFlowTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Test
	public void personalGoalIsLoggedCompletedAndReopened() {
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
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
			MainFlowTest.await();
			s.onActivity(a -> a.state().app.navigate(AppController.Screen.HISTORY));
			MainFlowTest.await();
			s.onActivity(a -> {
				View tab = a.getWindow().getDecorView().findViewWithTag("history_tab_goals");
				assertNotNull("Pestaña Metas", tab);
				tab.performClick();
			});
			MainFlowTest.await();
			String[] t = new String[8];
			s.onActivity(a -> {
				t[0] = a.getString(R.string.hist_new_goal);
				t[1] = a.getString(R.string.common_save);
				t[2] = a.getString(R.string.hist_goal_log);
				t[3] = a.getString(R.string.hist_goal_complete);
				t[4] = a.getString(R.string.hist_goal_reopen);
				t[5] = a.getString(R.string.hist_goal_completed_on, "");
				t[6] = a.getString(R.string.goal_status_custom, "3 libros", "12 libros");
				t[7] = a.getString(R.string.hist_goals_summary, 0, 1);
			});
			MainFlowTest.click(s, t[0]);
			s.onActivity(a -> {
				GoalsController g = a.state().goals;
				assertTrue("Formulario abierto", g.editing);
				g.draft.put("type", "CUSTOM");
				a.state().app.changed();
			});
			MainFlowTest.await();
			MainFlowTest.edit(s, "goal_name", "Leer libros");
			MainFlowTest.edit(s, "goal_unit", "libros");
			MainFlowTest.edit(s, "goal_custom_target", "12");
			MainFlowTest.click(s, t[1]);
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertNotNull("Tarjeta de la meta", MainFlowTest.text(root, "Leer libros: 12 libros"));
				assertNotNull("Botón para apuntar", MainFlowTest.text(root, t[2]));
			});
			MainFlowTest.click(s, t[2]);
			MainFlowTest.edit(s, "goal_progress", "3");
			MainFlowTest.click(s, t[1]);
			s.onActivity(a -> assertTrue("Avance apuntado",
				MainFlowTest.textContaining(a.getWindow().getDecorView(), t[6])));
			MainFlowTest.click(s, t[3]);
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertTrue("En cumplidas", MainFlowTest.textContaining(root, t[5].trim()));
				assertTrue("Resumen", MainFlowTest.textContaining(root, t[7]));
				assertNull("Ya no se apunta", MainFlowTest.text(root, t[2]));
				assertEquals(1, a.state().goals.completed().size());
			});
			MainFlowTest.click(s, t[4]);
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertFalse("Fuera de cumplidas", MainFlowTest.textContaining(root, t[5].trim()));
				assertNotNull("Vuelve a estar activa", MainFlowTest.text(root, t[2]));
				assertEquals(3, a.state().goals.active().get(0).entries.values().iterator().next(), 0);
			});
		} finally {
			try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
				db.clearLocalData();
			}
		}
	}
}
