package com.energym.app.ui;

import android.view.View;
import android.widget.CompoundButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.controller.AppState;
import com.energym.app.controller.AccountController;
import com.energym.app.data.EnerGymDatabase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.energym.app.domain.TrainingProfile;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Lee la cuenta actual: no inicia/cierra Google, no crea demo y no envía mensajes ni datos al servidor. */
@RunWith(AndroidJUnit4.class)
public final class PersonalTrainerChatFlowTest {
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

	@Test public void chatShowsRealModeAndAnExplicitConversationOnlyContextControl() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			boolean[] ready = {false};
			for (int i = 0; i < 30 && !ready[0]; i++) {
				scenario.onActivity(a -> ready[0] = stableAccountAndCatalog(a) && a.state().assistant != null);
				if (!ready[0])
					MainFlowTest.await();
			}
			assertTrue("La aplicación debe estar lista", ready[0]);
			scenario.onActivity(a -> Assume.assumeTrue("Se requiere una sesión ya abierta sin modificarla",
				a.state().app.owner() != null));
			scenario.onActivity(a -> a.state().app.navigate(AppController.Screen.ASSISTANT));
			boolean[] chatReady = {false};
			for (int i = 0; i < 30 && !chatReady[0]; i++) {
				scenario.onActivity(a -> chatReady[0] = stableAccountAndCatalog(a) &&
					a.state().app.screen == AppController.Screen.ASSISTANT &&
					a.getWindow().getDecorView().findViewWithTag("assistant_history_context") instanceof CompoundButton);
				if (!chatReady[0])
					MainFlowTest.await();
			}
			assertTrue("El chat debe mostrar sus controles tras estabilizarse la cuenta", chatReady[0]);
			scenario.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				Assume.assumeFalse("No interrumpir una petición de la persona", a.state().assistant.busy);
				String mode = a.state().assistant.remoteActive() ? "Servidor de IA emparejado · conversación" : "Guiado local · sin IA";
				assertNotNull("El modo actual se identifica sin fingir IA", MainFlowTest.text(root, mode));
				CompoundButton context = root.findViewWithTag("assistant_history_context");
				assertNotNull("Control visible de consentimiento para esta conversación", context);
				assertEquals(a.state().assistant.historyContextEnabled(), context.isChecked());
				TrainingProfile before = a.state().app.profile;
				String owner = a.state().app.owner();
				boolean initial = context.isChecked();
				try {
					context.performClick();
					assertEquals(!initial, a.state().assistant.historyContextEnabled());
					assertSame("El chat no reescribe el perfil", before, a.state().app.profile);
					assertEquals(owner, a.state().app.owner());
				} finally {
					a.state().assistant.historyContext(initial);
				}
				assertEquals(initial, a.state().assistant.historyContextEnabled());
			});
		}
	}
}
