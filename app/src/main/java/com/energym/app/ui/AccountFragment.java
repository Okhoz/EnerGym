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
		boolean signing = state.accountStatus == AccountController.Status.SIGNING_IN || state.deletingLocalData;
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
			"Enviar local usa reglas en este dispositivo. Enviar externo envía tu mensaje y el perfil de la conversación al servidor y su proveedor de IA, solo cuando lo pulsas. Los mensajes anteriores se envían únicamente con tu consentimiento en Tu perfil. Ese permiso es independiente del respaldo Firebase.");
		LinearLayout local = card("Datos de este dispositivo",
			"Borrar elimina el perfil, las rutinas, las sesiones, la conversación y las propuestas locales de la cuenta o demo actual, y cierra su sesión. No borra otras cuentas ni datos de Firebase. Si vuelves a conectar Google, se restaurarán los datos ya confirmados en Firebase; los pendientes solo locales se perderán.");
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

