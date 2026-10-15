package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.auth.AccountUser;
import com.energym.app.controller.AccountController;

import android.widget.LinearLayout;

/**
 * VISTA de identidad y respaldo: la política de build limita la demo al desarrollo.
 */
public final class AccountFragment extends ScreenFragment {
	public AccountFragment() {
		super(R.layout.fragment_account);
	}

	protected void content() {
		heading("CUENTA Y AJUSTES", "Tus datos, contigo",
			getString(R.string.account_intro));
		AccountUser user = state.user;
		LinearLayout account = card(state.accountText(), user == null ? getString(R.string.account_signed_out) : user.getEmail());
		boolean signing = state.accountStatus == AccountController.Status.SIGNING_IN || state.deletingLocalData;
		if (state.accountStatus == AccountController.Status.CONFIGURATION_NEEDED)
			card("Google: configuración pendiente",
				"Falta la configuración Firebase de esta instalación. No se puede iniciar sesión ni respaldar datos en la nube todavía.");
		action(account, "Conectar con Google", !signing,
			() -> state.accounts.signInWithGoogle(requireActivity(), false));
		if (app.owner() == null && state.accounts.isDemoEnabled())
			action(account, "Entrar en demo local", !signing, () -> {
				state.accounts.enterDemo();
				app.back();
			});
		else if (app.owner() != null)
			action(account, getString(R.string.account_sign_out), !signing, state.accounts::signOut);
		LinearLayout backup =
			card("Respaldo", state.syncText() + (state.syncError.isEmpty() ? "" : "\n" + state.syncError));
		action(backup, "Reintentar sincronización", user != null && !signing, state.sync::requestSync);
		card("Separación de cuentas",
			getString(R.string.account_separation));
		assistantServer();
		LinearLayout local = card("Datos de este dispositivo",
			getString(R.string.account_local_data));
		action(local, "Borrar datos locales", app.owner() != null && !signing, () -> {
			if (getParentFragmentManager().findFragmentByTag("delete-local-confirmation") != null)
				return;
			DeleteLocalConfirmation dialog = new DeleteLocalConfirmation();
			android.os.Bundle args = new android.os.Bundle();
			args.putString("owner", app.owner());
			dialog.setArguments(args);
			dialog.show(getParentFragmentManager(), "delete-local-confirmation");
		});
	}

	/**
	 * Ajustes del asistente fuera del chat: servidor de IA emparejado o guiado local gratuito por reglas.
	 */
	private void assistantServer() {
		com.energym.app.assistant.ProposalController c = state.assistant;
		if (c == null || app.owner() == null)
			return;
		LinearLayout server = card("Asistente con IA", c.connected() ?
			"Conectado a tu servidor. Los mensajes del chat se envían a la IA junto con el perfil de la conversación; los anteriores, solo con tu permiso en Tu perfil." :
			"Sin servidor, el chat usa el guiado local gratuito por reglas, sin enviar datos. Empareja tu servidor para que responda la IA.");
		if (c.connected()) {
			action(server, "Desconectar y revocar", !c.busy, c::disconnect);
			return;
		}
		action(server, c.showConnection ? "Ocultar conexión" : "Conectar servidor de IA", c::toggleConnection);
		if (!c.showConnection)
			return;
		textField(server, "assistant_endpoint", "URL del servidor (HTTPS)", c.endpoint, false, false, c::endpointInput);
		textField(server, "assistant_pairing", "Código privado de emparejamiento", c.pairingCode, false, false,
			c::pairingInput);
		android.widget.EditText pairing = server.findViewWithTag("assistant_pairing");
		pairing.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
		pairing.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
		action(server, "Conectar", !c.busy, () -> c.connect(c.endpoint, c.pairingCode));
		if (!c.status.isEmpty())
			text(server, c.status, R.style.EnerGym_Body, 12);
	}

	/**
	 * El dueño confirmado sobrevive a rotación; cambiar de cuenta invalida esta confirmación.
	 */
	public static final class DeleteLocalConfirmation extends androidx.fragment.app.DialogFragment {
		@Override
		public android.app.Dialog onCreateDialog(android.os.Bundle saved) {
			return new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("¿Borrar los datos locales?")
				.setMessage(
					"Se borrarán los datos y la conversación de esta cuenta en este dispositivo y se cerrará la sesión. Los datos de otras cuentas y de Firebase se conservan. Al volver a conectar Google se restaurará el respaldo confirmado; los cambios solo locales se perderán.")
				.setNegativeButton("Cancelar", null)
				.setPositiveButton("Borrar y salir", (dialog, which) ->
					((com.energym.app.MainActivity) requireActivity()).state()
						.clearLocalData(requireArguments().getString("owner")))
				.create();
		}
	}
}

