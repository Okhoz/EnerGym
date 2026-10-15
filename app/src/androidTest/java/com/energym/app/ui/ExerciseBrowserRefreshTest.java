package com.energym.app.ui;

import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.AccountController;
import com.energym.app.controller.AppController;
import com.energym.app.controller.AppState;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Una actualización externa conserva la edición del buscador, incluso en refrescos consecutivos. */
@RunWith(AndroidJUnit4.class)
public final class ExerciseBrowserRefreshTest {
	private void ready(ActivityScenario<MainActivity> scenario) {
		boolean[] ready = {false};
		for (int attempt = 0; attempt < 30 && !ready[0]; attempt++) {
			scenario.onActivity(activity -> {
				AppState state = activity.state();
				ready[0] = state.app != null && state.accounts != null && !state.app.loading &&
					!state.deletingLocalData && state.accountStatus != AccountController.Status.SIGNING_IN &&
					(state.app.owner() == null || state.app.initialDataReady);
			});
			if (!ready[0]) MainFlowTest.await();
		}
		assertTrue("La cuenta y los datos han terminado de restaurarse", ready[0]);
		scenario.onActivity(activity -> activity.state().app.navigate(AppController.Screen.TRAINING));
		MainFlowTest.await();
		scenario.onActivity(activity -> assertNotNull(
			activity.getWindow().getDecorView().findViewWithTag("exercise_search")));
	}

	@Test public void externalRefreshPreservesSearchFocusAndLatestCursor() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			scenario.onActivity(activity -> {
				EditText input = activity.getWindow().getDecorView().findViewWithTag("exercise_search");
				input.requestFocus();
				input.setText("sentadilla");
				input.setSelection(4);
				activity.state().app.changed();
				activity.state().app.changed();
			});
			MainFlowTest.await();
			scenario.onActivity(activity -> {
				EditText input = activity.getWindow().getDecorView().findViewWithTag("exercise_search");
				assertEquals("sentadilla", input.getText().toString());
				assertTrue("El refresco no interrumpe el campo enfocado", input.hasFocus());
				assertEquals("Se conserva la posición de escritura", 4, input.getSelectionStart());
				input.setSelection(2);
				activity.state().app.changed();
			});
			MainFlowTest.await();
			scenario.onActivity(activity -> {
				EditText input = activity.getWindow().getDecorView().findViewWithTag("exercise_search");
				assertTrue(input.hasFocus());
				assertEquals("Cada refresco toma el cursor actual", 2, input.getSelectionStart());
			});
		}
	}
}
