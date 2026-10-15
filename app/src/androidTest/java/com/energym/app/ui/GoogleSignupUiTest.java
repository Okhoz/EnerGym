package com.energym.app.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.auth.AccountUser;
import com.energym.app.controller.AccountController;
import com.energym.app.controller.AppController;
import com.energym.app.controller.AppState;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.energym.app.ui.MainFlowTest.await;
import static com.energym.app.ui.MainFlowTest.text;
import static org.junit.Assert.*;

/**
 * Presentación de acceso a Google: conserva la cuenta y nunca pulsa el proveedor ni borra la demo.
 */
@RunWith(AndroidJUnit4.class)
public class GoogleSignupUiTest {
	@Test
	public void guestGoogleEntryIsPrimaryAndBlockedWhileConnectingOrDeleting() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			scenario.onActivity(a -> {
				AppState state = a.state();
				AccountPresentation saved = new AccountPresentation(state);
				try {
					state.app.activate(null);
					state.user = null;
					state.accountStatus = AccountController.Status.SIGNED_OUT;
					state.deletingLocalData = false;
					state.app.navigate(AppController.Screen.HOME);
					View root = a.getWindow().getDecorView();
					View google = text(root, a.getString(R.string.google_continue));
					assertNotNull("Inicio debe ofrecer el acceso directo a Google", google);
					assertTrue(google.isShown());
					assertTrue(google.isEnabled());
					assertTrue(google.hasOnClickListeners());
					assertEquals(google, ((ViewGroup) google.getParent()).getChildAt(0));
					assertEquals(androidx.core.content.ContextCompat.getColor(a, R.color.gold),
						((com.google.android.material.button.MaterialButton) google).getBackgroundTintList()
							.getColorForState(new int[]{android.R.attr.state_enabled}, 0));
					View options = text(root, "Ver opciones de cuenta");
					assertNotNull(options);
					options.performClick();
					assertEquals(AppController.Screen.ACCOUNT, state.app.screen);
					state.app.navigate(AppController.Screen.HOME);
					state.accountStatus = AccountController.Status.SIGNING_IN;
					state.app.changed();
					View connecting = text(root, a.getString(R.string.google_connecting));
					assertNotNull(connecting);
					assertFalse(connecting.isEnabled());
					state.accountStatus = AccountController.Status.SIGNED_OUT;
					state.deletingLocalData = true;
					state.app.changed();
					View blocked = text(root, a.getString(R.string.google_continue));
					assertNotNull(blocked);
					assertFalse(blocked.isEnabled());
				} finally {
					saved.restore(state);
				}
			});
		}
	}

	@Test
	public void accountGoogleEntryReflectsGuestSignedInAndBusyStates() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			scenario.onActivity(a -> {
				AppState state = a.state();
				AccountPresentation saved = new AccountPresentation(state);
				try {
					state.app.activate(null);
					state.user = null;
					state.accountStatus = AccountController.Status.SIGNED_OUT;
					state.deletingLocalData = false;
					state.app.navigate(AppController.Screen.ACCOUNT);
					View root = a.getWindow().getDecorView();
					View google = text(root, a.getString(R.string.google_continue));
					assertNotNull(google);
					assertTrue(google.isEnabled());
					state.user = new AccountUser("presentation-only-uid", "Cuenta de prueba", "cuenta@example.invalid");
					state.accountStatus = AccountController.Status.AUTHENTICATED;
					state.app.changed();
					assertNotNull(text(root, a.getString(R.string.google_switch_account)));
					state.accountStatus = AccountController.Status.SIGNING_IN;
					state.app.changed();
					View connecting = text(root, a.getString(R.string.google_connecting));
					assertNotNull(connecting);
					assertFalse(connecting.isEnabled());
					state.accountStatus = AccountController.Status.AUTHENTICATED;
					state.deletingLocalData = true;
					state.app.changed();
					View blocked = text(root, a.getString(R.string.google_switch_account));
					assertNotNull(blocked);
					assertFalse(blocked.isEnabled());
				} finally {
					saved.restore(state);
				}
			});
		}
	}

	private static final class AccountPresentation {
		private final AccountUser user;
		private final AccountController.Status status;
		private final boolean deleting;
		private final String owner;
		private final AppController.Screen screen;

		AccountPresentation(AppState state) {
			user = state.user;
			status = state.accountStatus;
			deleting = state.deletingLocalData;
			owner = state.app.owner();
			screen = state.app.screen;
		}

		void restore(AppState state) {
			state.user = user;
			state.accountStatus = status;
			state.deletingLocalData = deleting;
			state.app.activate(owner);
			state.app.navigate(screen);
		}
	}

}
