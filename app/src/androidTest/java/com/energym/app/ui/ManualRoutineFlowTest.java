package com.energym.app.ui;

import android.view.View;
import android.widget.EditText;
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
	}

	@Test public void trainingOffersBothSectionsAndEditorKeepsGlobalProfile() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			waitReady(scenario);
			TrainingProfile[] original = {null}; String[] owner = {null};
			scenario.onActivity(a -> {
				original[0] = a.state().app.profile; owner[0] = a.state().app.owner();
				assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Con IA"));
				assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Mis rutinas"));
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
				scenario.onActivity(a -> { if (owner[0].equals(a.state().app.owner())) {
					a.state().app.manual.begin(a.state().app.profile); a.state().app.navigate(AppController.Screen.TRAINING);
				} });
			}
		}
	}

	@Test public void catalogIsometricEditorUsesDurationAndRestWithoutRepetitions() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			waitReady(scenario); Exercise[] selected = {null}; String[] owner = {null};
			scenario.onActivity(a -> {
				owner[0] = a.state().app.owner();
				for (Exercise e : a.state().app.catalog.getExercises()) if (e.getMetricType() == MetricType.ISOMETRIC) { selected[0] = e; break; }
				assertNotNull("El catálogo ofrece isométricos", selected[0]);
			});
			try {
				MainFlowTest.click(scenario, "Crear rutina manual");
				scenario.onActivity(a -> {
					Exercise e = selected[0]; ManualRoutineController c = a.state().app.manual;
					c.contextInput("sportId", e.getSportIds().iterator().next());
					c.contextInput("goal", e.getGoals().iterator().next().name());
					c.contextInput("environment", e.getEnvironments().iterator().next().name());
					c.contextInput("focus", e.getCoverage() == Exercise.Coverage.COMPLEMENTARY ? "COMPLEMENTARY" : "SPECIFIC");
					for (TrainingProfile.Resource r : e.getRequiredResources()) c.contextInput("resource." + r.name(), "true");
				});
				MainFlowTest.edit(scenario, "manual_search", selected[0].getName());
				MainFlowTest.click(scenario, "Añadir " + selected[0].getName());
				scenario.onActivity(a -> {
					View root = a.getWindow().getDecorView();
					assertTrue(root.findViewWithTag("manual_durationSeconds") instanceof EditText);
					assertTrue(root.findViewWithTag("manual_restSeconds") instanceof EditText);
					assertNull(root.findViewWithTag("manual_repetitions"));
					assertSame(selected[0], a.state().app.manual.entries().get(0).exercise);
					assertEquals(owner[0], a.state().app.owner());
				});
			} finally {
				scenario.onActivity(a -> { if (owner[0].equals(a.state().app.owner())) {
					a.state().app.manual.begin(a.state().app.profile); a.state().app.navigate(AppController.Screen.TRAINING);
				} });
			}
		}
	}
}
