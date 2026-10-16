package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.auth.AccountUser;
import com.energym.app.controller.AccountController;

import android.widget.LinearLayout;

/**
 * VISTA de identidad y respaldo: la política de build limita la demo al desarrollo.
 */
public final class AccountFragment extends ScreenFragment {
	/** Recordatorio que espera la respuesta al permiso de notificaciones: «training» o «water». */
	private String pendingReminder;
	private boolean notificationsDenied;
	private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
		registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
			granted -> {
				String kind = pendingReminder;
				pendingReminder = null;
				notificationsDenied = !granted;
				if (granted && kind != null)
					enableReminder(kind);
				else
					render();
			});

	public AccountFragment() {
		super(R.layout.fragment_account);
	}

	@Override
	public void onCreate(android.os.Bundle saved) {
		super.onCreate(saved);
		if (saved != null)
			pendingReminder = saved.getString("pendingReminder");
	}

	@Override
	public void onSaveInstanceState(android.os.Bundle out) {
		super.onSaveInstanceState(out);
		out.putString("pendingReminder", pendingReminder);
	}

	protected void content() {
		heading("CUENTA Y AJUSTES", "Cuenta", getString(R.string.account_intro));
		AccountUser user = state.user;
		LinearLayout account = card(state.accountText(), user == null ? getString(R.string.account_signed_out) : user.getEmail());
		boolean signing = state.accountStatus == AccountController.Status.SIGNING_IN || state.deletingLocalData;
		if (state.accountStatus == AccountController.Status.CONFIGURATION_NEEDED)
			card(getString(R.string.google_configuration_title), getString(R.string.google_configuration_missing));
		int googleLabel = state.accountStatus == AccountController.Status.SIGNING_IN ? R.string.google_connecting :
			user == null ? R.string.google_continue : R.string.google_switch_account;
		primary(action(account, getString(googleLabel), !signing,
			() -> state.accounts.signInWithGoogle(requireActivity(), false)));
		if (app.owner() == null && state.accounts.isDemoEnabled())
			action(account, "Entrar en demo local", !signing, () -> {
				state.accounts.enterDemo();
				app.back();
			});
		else if (app.owner() != null)
			action(account, getString(R.string.account_sign_out), !signing, state.accounts::signOut);
		LinearLayout backup =
			card("Respaldo", state.syncText() + (state.syncError.isEmpty() ? "" : "\n" + state.syncError));
		if (user != null)
			action(backup, "Reintentar sincronización", !signing, state.sync::requestSync);
		card("Separación de cuentas",
			getString(R.string.account_separation));
		assistantServer();
		reminders();
		preferences();
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
		if (c.onDevice()) {
			LinearLayout local = card("Entrenador con IA", c.connectionNotice());
			if (!c.downloadOffer().isEmpty())
				primary(action(local, c.downloadOffer(), !c.busy, c::downloadModel));
			text(local, "Usa Gemini Nano si tu móvil lo trae o, si no, Gemma (Google, licencia Apache 2.0), que " +
				"se descarga sola la primera vez que abres la app (2,6 GB). Todo se ejecuta en el móvil: no usa servidores ni envía tus mensajes fuera del dispositivo. " +
				"Si tu móvil no puede, el chat usa el guiado local.", R.style.EnerGym_FieldCaption, 8);
			if (!c.status.isEmpty()) text(local, c.status, R.style.EnerGym_Body, 12);
			return;
		}
		if (c.automaticConnection()) {
			LinearLayout online = card("Asistente con IA", c.connectionNotice());
			text(online, "Los mensajes se envían al servicio online sólo al usar el chat en modo IA. " +
				"Puedes elegir el guiado local dentro del chat.", R.style.EnerGym_FieldCaption, 8);
			if (!c.status.isEmpty()) text(online, c.status, R.style.EnerGym_Body, 12);
			return;
		}
		LinearLayout server = card("Asistente con IA", c.remoteActive() ?
			"Conectado a tu servidor. Puedes conversar con la IA y activar «Recordar esta conversación» dentro del chat para mantener el hilo." :
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
	 * Recordatorios locales opcionales: el permiso de notificaciones se pide solo al activar uno.
	 */
	private void reminders() {
		com.energym.app.reminders.ReminderController r = state.reminders;
		if (r == null)
			return;
		com.energym.app.reminders.ReminderPlan plan = r.plan();
		androidx.fragment.app.Fragment open = getParentFragmentManager().findFragmentByTag("reminder-time");
		if (open instanceof com.google.android.material.timepicker.MaterialTimePicker)
			listen((com.google.android.material.timepicker.MaterialTimePicker) open);
		LinearLayout card = card("Recordatorios",
			"Avisos en este móvil, sin servidor. Puedes apagarlos cuando quieras.");
		String days = plan.daysText();
		toggle(card, "Aviso los días de entreno", plan.training, on -> {
			if (on) requestReminder("training");
			else r.setTraining(false);
		});
		caption(card, days.isEmpty() ? "Crea tu plan de entreno para saber qué días avisarte." :
			"Días: " + days + " (según los " + plan.daysPerWeek + " días por semana de tu plan). Abre Entreno.");
		if (plan.training)
			action(card, "Hora del aviso: " + plan.trainingTimeText(), this::pickTrainingTime);
		toggle(card, "Recordatorios para beber agua", plan.water, on -> {
			if (on) requestReminder("water");
			else r.setWater(false);
		});
		caption(card, "Entre las " + com.energym.app.reminders.ReminderPlan.WATER_START_HOUR + ":00 y las " +
			com.energym.app.reminders.ReminderPlan.WATER_END_HOUR + ":00. Abre Nutrición para apuntar el vaso.");
		if (plan.water)
			choice(card, "reminder_water_hours", "Frecuencia", java.util.Arrays.asList("1", "2", "3", "4"),
				java.util.Arrays.asList("Cada hora", "Cada 2 horas", "Cada 3 horas", "Cada 4 horas"),
				Integer.toString(plan.waterHours), value -> r.setWaterHours(Integer.parseInt(value)));
		if ((plan.training || plan.water || notificationsDenied) && !r.canNotify()) {
			text(card, "Las notificaciones de EnerGym están desactivadas, así que no verás los avisos. " +
				"Actívalas en los ajustes de Android.", R.style.EnerGym_Body, 12);
			action(card, "Abrir ajustes de notificaciones", () -> startActivity(
				new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
					.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())));
		}
	}

	/** Preferencias de este móvil: unidad de peso del registro de entreno. */
	private void preferences() {
		LinearLayout card = card("Preferencias", "Se guardan en este móvil.");
		choice(card, "weight_unit", "Unidad de peso en el entreno", java.util.Arrays.asList("kg", "lb"),
			java.util.Arrays.asList("Kilos (kg)", "Libras (lb)"), Units.lb(requireContext()) ? "lb" : "kg",
			"Tus registros se guardan igual; solo cambia cómo se muestran y escriben.",
			v -> Units.setLb(requireContext(), "lb".equals(v)));
	}

	/** Explicación bajo un interruptor, separada del siguiente control. */
	private void caption(LinearLayout target, String value) {
		android.widget.TextView help = text(target, value, R.style.EnerGym_FieldCaption, 0);
		LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) help.getLayoutParams();
		params.bottomMargin = dp(16);
		help.setLayoutParams(params);
	}

	private void toggle(LinearLayout target, String label, boolean on, java.util.function.Consumer<Boolean> event) {
		com.google.android.material.materialswitch.MaterialSwitch row =
			(com.google.android.material.materialswitch.MaterialSwitch) getLayoutInflater().inflate(R.layout.row_switch,
				target, false);
		row.setId(android.view.View.generateViewId());
		row.setText(label);
		row.setChecked(on);
		row.setOnCheckedChangeListener((button, value) -> event.accept(value));
		target.addView(row);
	}

	/** Pide el permiso (Android 13+) la primera vez; si ya está concedido, activa el aviso al momento. */
	private void requestReminder(String kind) {
		if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
			requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) !=
			android.content.pm.PackageManager.PERMISSION_GRANTED) {
			pendingReminder = kind;
			notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
			return;
		}
		enableReminder(kind);
	}

	private void enableReminder(String kind) {
		if ("training".equals(kind))
			state.reminders.setTraining(true);
		else
			state.reminders.setWater(true);
	}

	private void pickTrainingTime() {
		if (getParentFragmentManager().findFragmentByTag("reminder-time") != null)
			return;
		int minute = state.reminders.plan().trainingMinute;
		com.google.android.material.timepicker.MaterialTimePicker picker =
			new com.google.android.material.timepicker.MaterialTimePicker.Builder()
				.setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H).setHour(minute / 60)
				.setMinute(minute % 60).setTitleText("Hora del aviso de entreno").build();
		listen(picker);
		picker.show(getParentFragmentManager(), "reminder-time");
	}

	/** Tras girar la pantalla el selector se restaura sin oyentes: se vuelven a enlazar en cada pintado. */
	private void listen(com.google.android.material.timepicker.MaterialTimePicker picker) {
		picker.clearOnPositiveButtonClickListeners();
		picker.addOnPositiveButtonClickListener(v ->
			state.reminders.setTrainingMinute(picker.getHour() * 60 + picker.getMinute()));
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

