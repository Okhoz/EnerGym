package com.energym.app.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.*;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.domain.*;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Sin cambios de identidad ni escrituras: recorre sólo el borrador local del editor. */
@RunWith(AndroidJUnit4.class)
public final class ManualRoutineFlowTest {
	private boolean ready(MainActivity a) {
		AppState s = a.state();
		if (s.app == null || s.accounts == null || s.app.loading || !s.app.initialDataReady || s.deletingLocalData ||
			s.accountStatus == AccountController.Status.SIGNING_IN) return false;
		FirebaseUser firebase = FirebaseApp.getApps(a).isEmpty() ? null : FirebaseAuth.getInstance().getCurrentUser();
		String owner = s.app.owner();
		if (s.accountStatus == AccountController.Status.DEMO_LOCAL)
			return EnerGymDatabase.DEMO_OWNER_UID.equals(owner) && s.user == null && firebase == null;
		return (s.accountStatus == AccountController.Status.AUTHENTICATED || s.accountStatus == AccountController.Status.ERROR) && firebase != null && s.user != null &&
			firebase.getUid().equals(s.user.getUid()) && firebase.getUid().equals(owner);
	}
	private void waitReady(ActivityScenario<MainActivity> scenario) {
		boolean[] stable = {false};
		for (int i = 0; i < 30 && !stable[0]; i++) {
			scenario.onActivity(a -> stable[0] = ready(a));
			if (!stable[0]) MainFlowTest.await();
		}
		Assume.assumeTrue("Requiere una cuenta ya abierta; el test no inicia ni cierra Google.", stable[0]);
		scenario.onActivity(a -> {
			Assume.assumeTrue(!a.state().app.manual.busy && a.state().app.manual.entries().isEmpty());
			a.state().app.navigate(AppController.Screen.TRAINING);
		});
		MainFlowTest.await();
		MainFlowTest.trainingPane(scenario, "mine");
	}

	private View described(View view, String description) {
		if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
			return view;
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int index = 0; index < group.getChildCount(); index++) {
				View found = described(group.getChildAt(index), description);
				if (found != null) return found;
			}
		}
		return null;
	}

	private void clickAdd(ActivityScenario<MainActivity> scenario, Exercise exercise) {
		scenario.onActivity(a -> {
			View button = described(a.getWindow().getDecorView(), "Añadir " + exercise.getName());
			assertNotNull("Cada acción identifica su ejercicio para accesibilidad", button);
			assertTrue(button.isEnabled());
			button.performClick();
		});
		MainFlowTest.await();
	}

	private void select(ActivityScenario<MainActivity> scenario, String tag, String label) {
		scenario.onActivity(a -> {
			Spinner spinner = a.getWindow().getDecorView().findViewWithTag(tag);
			assertNotNull(tag, spinner);
			int selected = -1;
			for (int index = 0; index < spinner.getAdapter().getCount(); index++) {
				if (label.equals(spinner.getAdapter().getItem(index).toString())) selected = index;
			}
			assertTrue("Opción disponible: " + label, selected >= 0);
			spinner.setSelection(selected);
		});
		MainFlowTest.await();
	}

	private void configureContext(ManualRoutineController controller, Exercise exercise) {
		controller.contextInput("sportId", exercise.getSportIds().iterator().next());
		controller.contextInput("goal", exercise.getGoals().iterator().next().name());
		controller.contextInput("environment", exercise.getEnvironments().iterator().next().name());
		controller.contextInput("focus", exercise.getCoverage() == Exercise.Coverage.COMPLEMENTARY ? "COMPLEMENTARY" : "SPECIFIC");
		for (TrainingProfile.Resource resource : exercise.getRequiredResources())
			controller.contextInput("resource." + resource.name(), "true");
	}

	@Test public void trainingOffersSeparatePanesAndEditorKeepsGlobalProfile() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			waitReady(scenario);
			TrainingProfile[] original = {null};
			String[] owner = {null};
			scenario.onActivity(a -> {
				original[0] = a.state().app.profile;
				owner[0] = a.state().app.owner();
				assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Con IA"));
				assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Mis rutinas"));
				assertNotNull(a.getWindow().getDecorView().findViewWithTag("training_tab_exercises"));
				assertNull("La creación con IA pertenece a su propia vista",
					MainFlowTest.text(a.getWindow().getDecorView(), "Crear rutina con IA"));
			});
			try {
				MainFlowTest.click(scenario, "Crear rutina manual");
				MainFlowTest.edit(scenario, "manual_title", "Mi rutina de prueba local");
				MainFlowTest.click(scenario, "Progresión automática");
				MainFlowTest.click(scenario, "Ajustar contexto de esta rutina");
				MainFlowTest.edit(scenario, "manual_duration", "60");
				scenario.onActivity(a -> {
					assertEquals(AppController.Screen.MANUAL_ROUTINE, a.state().app.screen);
					assertEquals(60, a.state().app.manual.context().getDurationMinutes());
					assertFalse(a.state().app.manual.progression());
					TrainingProfile current = a.state().app.profile;
					if (original[0] == null) assertNull(current);
					else {
						assertNotNull(current);
						assertEquals(original[0].getDurationMinutes(), current.getDurationMinutes());
						assertEquals(original[0].getSportId(), current.getSportId());
						assertEquals(original[0].getGoal(), current.getGoal());
						assertEquals(original[0].getResources(), current.getResources());
					}
					assertEquals(owner[0], a.state().app.owner());
					assertFalse(((MaterialCheckBox) a.getWindow().getDecorView().findViewWithTag("manual_progression")).isChecked());
				});
			} finally {
				scenario.onActivity(a -> {
					if (owner[0].equals(a.state().app.owner())) {
						a.state().app.manual.begin(a.state().app.profile);
						a.state().app.navigate(AppController.Screen.TRAINING);
					}
				});
			}
		}
	}

	@Test public void catalogIsometricEditorUsesDurationAndRestWithoutRepetitions() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			waitReady(scenario);
			Exercise[] selected = {null};
			String[] owner = {null};
			scenario.onActivity(a -> {
				owner[0] = a.state().app.owner();
				for (Exercise exercise : a.state().app.catalog.getExercises()) {
					if (exercise.getMetricType() == MetricType.ISOMETRIC) {
						selected[0] = exercise;
						break;
					}
				}
				assertNotNull("El catálogo ofrece isométricos", selected[0]);
			});
			try {
				MainFlowTest.click(scenario, "Crear rutina manual");
				scenario.onActivity(a -> {
					configureContext(a.state().app.manual, selected[0]);
				});
				MainFlowTest.edit(scenario, "manual_search", selected[0].getName());
				clickAdd(scenario, selected[0]);
				scenario.onActivity(a -> {
					View root = a.getWindow().getDecorView();
					assertTrue(root.findViewWithTag("manual_durationSeconds") instanceof EditText);
					assertTrue(root.findViewWithTag("manual_restSeconds") instanceof EditText);
					assertNull(root.findViewWithTag("manual_repetitions"));
					assertSame(selected[0], a.state().app.manual.entries().get(0).exercise);
					assertEquals(owner[0], a.state().app.owner());
				});
			} finally {
				scenario.onActivity(a -> {
					if (owner[0].equals(a.state().app.owner())) {
						a.state().app.manual.begin(a.state().app.profile);
						a.state().app.navigate(AppController.Screen.TRAINING);
					}
				});
			}
		}
	}

	@Test public void manualCatalogFiltersPreserveContextAndAllModeKeepsIncompatibleAddDisabled() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			waitReady(scenario);
			Exercise[] selected = {null}, incompatible = {null};
			TrainingProfile[] context = {null}, global = {null};
			String[] owner = {null}, discipline = {null};
			scenario.onActivity(a -> {
				owner[0] = a.state().app.owner();
				global[0] = a.state().app.profile;
				selected[0] = a.state().app.catalog.getExercises().get(0);
			});
			try {
				MainFlowTest.click(scenario, "Crear rutina manual");
				scenario.onActivity(a -> {
					ManualRoutineController controller = a.state().app.manual;
					configureContext(controller, selected[0]);
					context[0] = controller.context();
					for (Sport sport : a.state().app.catalog.getSports())
						if (sport.getId().equals(context[0].getSportId())) discipline[0] = sport.getName();
					for (Exercise exercise : a.state().app.catalog.getExercises()) {
						if (!controller.contextCompatibility(exercise).isEmpty()) {
							incompatible[0] = exercise;
							break;
						}
					}
					assertNotNull(incompatible[0]);
					MaterialCheckBox compatible = a.getWindow().getDecorView().findViewWithTag("manual_only_compatible");
					assertTrue("El catálogo comienza con compatibles", compatible.isChecked());
				});
				MainFlowTest.click(scenario, "Filtrar ejercicios");
				select(scenario, "manual_filter_sport", discipline[0]);
				select(scenario, "manual_filter_environment", AppController.label(context[0].getEnvironment().name()));
				select(scenario, "manual_filter_goal", AppController.label(context[0].getGoal().name()));
				String material = selected[0].getRequiredResources().isEmpty() ? "Sin material" :
					AppController.label(selected[0].getRequiredResources().iterator().next().name());
				select(scenario, "manual_filter_material", material);
				MainFlowTest.edit(scenario, "manual_search", selected[0].getName());
				scenario.onActivity(a -> {
					View root = a.getWindow().getDecorView();
					View add = described(root, "Añadir " + selected[0].getName());
					assertNotNull("La combinación de filtros mantiene el ejercicio elegido", add);
					assertTrue(add.isEnabled());
					MaterialCheckBox compatible = root.findViewWithTag("manual_only_compatible");
					compatible.setChecked(false);
				});
				MainFlowTest.click(scenario, "Limpiar búsqueda y filtros");
				MainFlowTest.edit(scenario, "manual_search", incompatible[0].getName());
				scenario.onActivity(a -> {
					View root = a.getWindow().getDecorView();
					View add = described(root, "Añadir " + incompatible[0].getName());
					assertNotNull("Explorar todos muestra también los incompatibles", add);
					assertFalse("Explorar no permite añadir un incompatible", add.isEnabled());
					assertTrue(a.state().app.manual.entries().isEmpty());
					TrainingProfile current = a.state().app.manual.context();
					assertEquals(context[0].getSportId(), current.getSportId());
					assertEquals(context[0].getEnvironment(), current.getEnvironment());
					assertEquals(context[0].getGoal(), current.getGoal());
					assertEquals(context[0].getResources(), current.getResources());
					TrainingProfile profile = a.state().app.profile;
					if (global[0] == null) assertNull(profile);
					else {
						assertNotNull(profile);
						assertEquals(global[0].getSportId(), profile.getSportId());
						assertEquals(global[0].getEnvironment(), profile.getEnvironment());
						assertEquals(global[0].getGoal(), profile.getGoal());
						assertEquals(global[0].getResources(), profile.getResources());
					}
				});
			} finally {
				scenario.onActivity(a -> {
					if (owner[0].equals(a.state().app.owner())) {
						a.state().app.manual.begin(a.state().app.profile);
						a.state().app.navigate(AppController.Screen.TRAINING);
					}
				});
			}
		}
	}
}
