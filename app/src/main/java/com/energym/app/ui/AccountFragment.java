package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.auth.AccountUser;
import com.energym.app.controller.AccountController;

import android.widget.LinearLayout;

/**
 * VISTA de identidad y respaldo: entrada demo y Google siempre se muestran con alcance explícito.
 */
public final class AccountFragment extends ScreenFragment {
	public AccountFragment() {
		super(R.layout.fragment_account);
	}

	protected void content() {
		heading("CUENTA Y AJUSTES", "Tus datos, contigo",
			"Conecta Google para activar el respaldo Firebase. La demo permanece solo en este dispositivo.");
		AccountUser user = state.user;
		LinearLayout account = card(state.accountText(), user == null ? "Elige cómo quieres entrar." : user.getEmail());
		boolean signing = state.accountStatus == AccountController.Status.SIGNING_IN;
		if (state.accountStatus == AccountController.Status.CONFIGURATION_NEEDED)
			card("Google: configuración pendiente",
				"Falta la configuración Firebase de esta instalación. No se puede iniciar sesión ni respaldar datos en la nube todavía.");
		action(account, "Conectar con Google", !signing,
			() -> state.accounts.signInWithGoogle(requireActivity(), false));
		if (app.owner() == null)
			action(account, "Entrar en demo local", !signing, () -> {
				state.accounts.enterDemo();
				app.back();
			});
		else
			action(account, "Salir de la cuenta / demo", !signing, state.accounts::signOut);
		LinearLayout backup =
			card("Respaldo", state.syncText() + (state.syncError.isEmpty() ? "" : "\n" + state.syncError));
		action(backup, "Reintentar sincronización", user != null && !signing, state.sync::requestSync);
		card("Separación de cuentas",
			"Cada cuenta conserva su propio perfil, rutinas y sesiones. La demo no se copia automáticamente a Google. Cerrar sesión oculta los datos locales de esa cuenta.");
		card("Historial y personalización",
			"El consentimiento para usar historial con IA es independiente del respaldo Google. Puedes cambiarlo en Tu perfil. Esta versión no envía datos a IA.");
	}
}

