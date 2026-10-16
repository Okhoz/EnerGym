package com.energym.app.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.AccountController;
import com.energym.app.controller.AppController;
import com.energym.app.domain.Routine;
import com.energym.app.domain.RoutinePlanner;
import com.energym.app.domain.Session;
import com.energym.app.domain.TrainingProfile;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Sólo navegación y fixtures en memoria: no cambia identidad ni escribe sesiones o rutinas. */
@RunWith(AndroidJUnit4.class)
public final class CleanTrainingFlowTest {
	private void ready(ActivityScenario<MainActivity> scenario) {
		boolean[] stable = {false};
		for (int attempt = 0; attempt < 30 && !stable[0]; attempt++) {
			scenario.onActivity(activity -> stable[0] = activity.state().app != null && activity.state().accounts != null &&
				!activity.state().app.loading && !activity.state().deletingLocalData &&
				activity.state().accountStatus != AccountController.Status.SIGNING_IN &&
				(activity.state().app.owner() == null || activity.state().app.initialDataReady));
			if (!stable[0]) MainFlowTest.await();
		}
		assertTrue("La cuenta y sus datos deben estar listos", stable[0]);
		scenario.onActivity(activity -> activity.state().app.navigate(AppController.Screen.TRAINING));
		MainFlowTest.await();
	}

	private void pane(ActivityScenario<MainActivity> scenario, String tag) {
		scenario.onActivity(activity -> {
			View tab = activity.getWindow().getDecorView().findViewWithTag(tag);
			assertNotNull("Acceso a la vista " + tag, tab);
			assertTrue(tab.isEnabled());
			tab.performClick();
		});
		MainFlowTest.await();
	}

	@Test public void panesExposeOnlyTheirOwnCreationAndSearchControls() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			pane(scenario, "training_tab_mine");
			// Con rutinas, las formas de crear están detrás de un único «+ Nuevo»; sin rutinas ya se ven.
			scenario.onActivity(activity -> {
				View toggle = activity.getWindow().getDecorView().findViewWithTag("training_new");
				if (toggle != null && MainFlowTest.text(activity.getWindow().getDecorView(), "Crear rutina manual") == null)
					toggle.performClick();
			});
			MainFlowTest.await();
			boolean[] signedIn = {false};
			scenario.onActivity(activity -> signedIn[0] = activity.state().app.owner() != null);
			if (!signedIn[0]) {
				// Sin cuenta no hay nada que crear: solo el acceso a elegirla.
				scenario.onActivity(activity -> {
					View root = activity.getWindow().getDecorView();
					assertNotNull(MainFlowTest.text(root, "Elegir cuenta"));
					assertNull(MainFlowTest.text(root, "Crear rutina manual"));
				});
				return;
			}
			scenario.onActivity(activity -> {
				View root = activity.getWindow().getDecorView();
				// Rutinas reúne todas las formas de crear.
				assertNotNull(MainFlowTest.text(root, "Crear rutina manual"));
				assertNotNull(MainFlowTest.text(root, "Crear rutina con IA"));
				assertNotNull(MainFlowTest.text(root, "Una automática ya"));
				assertNull(root.findViewWithTag("exercise_search"));
				assertNull("Ya no hay pestaña separada para la IA", root.findViewWithTag("training_tab_ai"));
			});
			pane(scenario, "training_tab_exercises");
			scenario.onActivity(activity -> {
				View root = activity.getWindow().getDecorView();
				assertNotNull(root.findViewWithTag("exercise_search"));
				assertNull(MainFlowTest.text(root, "Crear rutina manual"));
				assertNull(MainFlowTest.text(root, "Crear rutina con IA"));
			});
		}
	}

	@Test public void techniqueBackKeepsTheExercisePaneAndItsQuery() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			pane(scenario, "training_tab_exercises");
			MainFlowTest.edit(scenario, "exercise_search", "Flexión estándar");
			MainFlowTest.click(scenario, "Ver técnica de Flexión estándar");
			scenario.onActivity(activity -> {
				assertEquals(AppController.Screen.EXERCISE, activity.state().app.screen);
				activity.state().app.back();
			});
			MainFlowTest.await();
			scenario.onActivity(activity -> {
				View root = activity.getWindow().getDecorView();
				EditText search = root.findViewWithTag("exercise_search");
				assertNotNull("Volver de técnica debe mostrar Ejercicios", search);
				assertEquals("Flexión estándar", search.getText().toString());
				assertNull(MainFlowTest.text(root, "Crear rutina manual"));
			});
		}
	}

	@Test public void extraDraftsStayCollapsedAndDuplicateSnapshotsAppearOnce() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			AppController[] app = {null};
			String[] owner = {null};
			AtomicReference<List<Session>> previousPending = new AtomicReference<>();
			Session[] previousSession = {null};
			boolean[] previousExpanded = {false};
			TrainingViewState.Pane[] previousPane = {null};
			scenario.onActivity(activity -> {
				app[0] = activity.state().app;
				Assume.assumeTrue(app[0].owner() != null && !app[0].sessions.busy && !app[0].routines.busy);
				owner[0] = app[0].owner();
				previousPending.set(app[0].pending);
				previousSession[0] = app[0].sessions.session;
				previousExpanded[0] = activity.state().training.draftsExpanded;
				previousPane[0] = activity.state().training.pane;
			});
			try {
				scenario.onActivity(activity -> {
					Assume.assumeTrue(Objects.equals(owner[0], app[0].owner()));
					Routine routine = new RoutinePlanner().create(new TrainingProfile.Builder().build(),
						app[0].catalog.getExercises()).withVersionId(1);
					Session first = new Session(routine), second = new Session(routine);
					app[0].sessions.session = null;
					app[0].pending = Arrays.asList(first, second, second);
					activity.state().training.draftsExpanded = false;
				});
				pane(scenario, "training_tab_mine");
				scenario.onActivity(activity -> {
					View root = activity.getWindow().getDecorView();
					assertEquals("El primer entreno a medias es la acción principal", 1, count(root, "Seguir entrenando"));
					assertNotNull(MainFlowTest.text(root, "Otros entrenos a medias (1)"));
				});
				MainFlowTest.click(scenario, "Otros entrenos a medias (1)");
				scenario.onActivity(activity -> assertEquals("Cada UUID debe aparecer una vez", 2,
					count(activity.getWindow().getDecorView(), "Seguir entrenando") +
					count(activity.getWindow().getDecorView(), "Seguir este")));
			} finally {
				scenario.onActivity(activity -> {
					if (activity.state().app == app[0] && Objects.equals(owner[0], app[0].owner())) {
						app[0].pending = previousPending.get();
						app[0].sessions.session = previousSession[0];
						activity.state().training.draftsExpanded = previousExpanded[0];
						activity.state().training.select(previousPane[0]);
						app[0].changed();
					}
				});
			}
		}
	}

	private int count(View view, String text) {
		int count = view instanceof TextView && text.contentEquals(((TextView) view).getText()) ? 1 : 0;
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int index = 0; index < group.getChildCount(); index++) count += count(group.getChildAt(index), text);
		}
		return count;
	}
}
