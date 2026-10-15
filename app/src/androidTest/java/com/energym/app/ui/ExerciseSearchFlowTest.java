package com.energym.app.ui;

import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.controller.AccountController;
import com.energym.app.controller.AppState;
import com.energym.app.data.EnerGymDatabase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** El buscador explora el catálogo sin abrir Google, escribir datos ni cambiar la elegibilidad del perfil. */
@RunWith(AndroidJUnit4.class)
public final class ExerciseSearchFlowTest {
	private void ready(ActivityScenario<MainActivity> scenario) throws Exception {
		boolean[] ready = {false};
		for (int i = 0; i < 30 && !ready[0]; i++) {
			scenario.onActivity(a -> ready[0] = stableAccountAndCatalog(a));
			if (!ready[0])
				MainFlowTest.await();
		}
		assertTrue("El catálogo y la cuenta deben terminar de restaurarse", ready[0]);
		scenario.onActivity(a -> a.state().app.navigate(AppController.Screen.TRAINING));
		boolean[] searchReady = {false};
		for (int i = 0; i < 30 && !searchReady[0]; i++) {
			scenario.onActivity(a -> searchReady[0] = stableAccountAndCatalog(a) &&
				a.state().app.screen == AppController.Screen.TRAINING &&
				a.getWindow().getDecorView().findViewWithTag("exercise_search") instanceof EditText);
			if (!searchReady[0])
				MainFlowTest.await();
		}
		assertTrue("La biblioteca debe mostrar el campo exercise_search tras estabilizar la cuenta", searchReady[0]);
	}

	/** Sólo lectura: no cambia la identidad ni oculta callbacks de autenticación aún pendientes. */
	private boolean stableAccountAndCatalog(MainActivity activity) {
		AppState state = activity.state();
		if (state.app == null || state.accounts == null || state.app.loading || state.deletingLocalData ||
			state.accountStatus == AccountController.Status.SIGNING_IN)
			return false;
		FirebaseUser firebase = FirebaseApp.getApps(activity).isEmpty() ? null : FirebaseAuth.getInstance().getCurrentUser();
		String owner = state.app.owner();
		if (state.accountStatus == AccountController.Status.DEMO_LOCAL)
			return EnerGymDatabase.DEMO_OWNER_UID.equals(owner) && state.user == null && firebase == null;
		if (state.accountStatus == AccountController.Status.CONFIGURATION_NEEDED ||
			state.accountStatus == AccountController.Status.SIGNED_OUT)
			return owner == null && state.user == null && firebase == null;
		if (state.accountStatus == AccountController.Status.AUTHENTICATED ||
			state.accountStatus == AccountController.Status.ERROR) {
			if (firebase == null)
				return state.accountStatus == AccountController.Status.ERROR && state.user == null && owner == null;
			return state.user != null && firebase.getUid().equals(state.user.getUid()) && firebase.getUid().equals(owner);
		}
		return false;
	}

	private void select(ActivityScenario<MainActivity> scenario, String tag, String label) throws Exception {
		scenario.onActivity(a -> {
			Spinner spinner = a.getWindow().getDecorView().findViewWithTag(tag);
			assertNotNull("Filtro accesible: " + tag, spinner);
			int index = -1;
			for (int i = 0; i < spinner.getAdapter().getCount(); i++)
				if (label.equals(spinner.getAdapter().getItem(i).toString()))
					index = i;
			assertTrue("Opción de filtro: " + label, index >= 0);
			spinner.setSelection(index);
		});
		MainFlowTest.await();
	}

	@Test public void libraryCombinesFiltersCountsResultsAndClearsThem() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			scenario.onActivity(a -> assertNotNull("La biblioteca ofrece filtros explícitos",
				MainFlowTest.text(a.getWindow().getDecorView(), "Filtrar ejercicios")));
			MainFlowTest.click(scenario, "Filtrar ejercicios");
			select(scenario, "exercise_filter_sport", "Calistenia");
			select(scenario, "exercise_filter_environment", "Casa");
			select(scenario, "exercise_filter_material", "Sin material");
			select(scenario, "exercise_filter_goal", "Fuerza");
			MainFlowTest.edit(scenario, "exercise_search", "sentad");
			scenario.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertNotNull(MainFlowTest.text(root, "Sentadilla sin accesorios"));
				assertNull(MainFlowTest.text(root, "Sentadilla con mancuernas"));
				assertTrue(MainFlowTest.textContaining(root, "resultado"));
			});
			MainFlowTest.click(scenario, "Limpiar búsqueda y filtros");
			scenario.onActivity(a -> {
				EditText query = a.getWindow().getDecorView().findViewWithTag("exercise_search");
				assertEquals("", query.getText().toString());
				assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(),
					a.state().app.catalog.getExercises().size() + " resultados"));
			});
		}
	}

	@Test public void queryNormalizesTextAndExplainsNoResults() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			MainFlowTest.edit(scenario, "exercise_search", " FLEXIÓN   estÁN ");
			scenario.onActivity(a -> assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Flexión estándar")));
			MainFlowTest.edit(scenario, "exercise_search", "zapatillainexistente");
			scenario.onActivity(a -> assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(),
				"No hay ejercicios con esta búsqueda y estos filtros")));
		}
	}
}
