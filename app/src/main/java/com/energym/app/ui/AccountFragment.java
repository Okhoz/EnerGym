package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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

	/** Copia en CSV: se elige dónde guardarla o qué archivo abrir con el selector de Android (sin permisos). */
	private final androidx.activity.result.ActivityResultLauncher<String> exportCsv = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"), this::writeCsv);
	private final androidx.activity.result.ActivityResultLauncher<String[]> importCsv = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::readCsv);

	/** Cambiarse desde Strong o Hevy: se lee su CSV; y se exporta en su formato para llevarse los datos. */
	private final androidx.activity.result.ActivityResultLauncher<String[]> importWorkouts = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::readWorkouts);
	private final androidx.activity.result.ActivityResultLauncher<String> exportStrong = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"), this::writeStrong);

	private final androidx.activity.result.ActivityResultLauncher<String[]> healthPermissions = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
			boolean ok = com.energym.app.health.HealthSync.granted(requireContext());
			com.energym.app.health.HealthSync.setEnabled(requireContext(), ok);
			if (ok)
				syncHealth();
			else
				toast(Txt.t(R.string.acc_health_no_permission));
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
		heading("", Txt.t(R.string.account), "");
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
			action(account, Txt.t(R.string.acc_demo), !signing, () -> {
				state.accounts.enterDemo();
				app.back();
			});
		else if (app.owner() != null)
			action(account, getString(R.string.account_sign_out), !signing, state.accounts::signOut);
		// Ajustes como lista plegable: solo uno abierto a la vez para no saturar la pantalla.
		section(Txt.t(R.string.acc_settings), 32);
		if (fold("backup", Txt.t(R.string.acc_backup_fold))) {
			LinearLayout backup =
				card(Txt.t(R.string.acc_backup), state.syncText() + (state.syncError.isEmpty() ? "" : "\n" + state.syncError));
			if (user != null)
				action(backup, Txt.t(R.string.acc_retry_sync), !signing, state.sync::requestSync);
			text(backup, getString(R.string.account_separation), R.style.EnerGym_FieldCaption, 12);
		}
		if (app.owner() != null && state.assistant != null && fold("assistant", Txt.t(R.string.acc_trainer_fold)))
			assistantServer();
		if (state.reminders != null && fold("reminders", Txt.t(R.string.acc_reminders_fold)))
			reminders();
		if (fold("preferences", Txt.t(R.string.acc_preferences_fold)))
			preferences();
		if (!fold("device", Txt.t(R.string.acc_device_fold)))
			return;
		LinearLayout local = card(Txt.t(R.string.acc_device),
			getString(R.string.account_local_data));
		action(local, Txt.t(R.string.acc_delete_local), app.owner() != null && !signing, () -> {
			if (getParentFragmentManager().findFragmentByTag("delete-local-confirmation") != null)
				return;
			DeleteLocalConfirmation dialog = new DeleteLocalConfirmation();
			android.os.Bundle args = new android.os.Bundle();
			args.putString("owner", app.owner());
			dialog.setArguments(args);
			dialog.show(getParentFragmentManager(), "delete-local-confirmation");
		});
	}

	/** Cabecera plegable de un ajuste; devuelve si está abierto. Abrir uno cierra el anterior. */
	private boolean fold(String key, String label) {
		boolean open = key.equals(openSetting);
		// La tarjeta desplegada del apartado anterior queda a la misma distancia que el resto de cabeceras.
		if (rows.getChildCount() > 0) {
			android.view.View previous = rows.getChildAt(rows.getChildCount() - 1);
			if (previous.findViewById(R.id.card_actions) != null &&
				previous.getLayoutParams() instanceof LinearLayout.LayoutParams) {
				((LinearLayout.LayoutParams) previous.getLayoutParams()).bottomMargin = dp(16);
				previous.requestLayout();
			}
		}
		android.view.View header = listRow(rows, label, "", open ? "▴" : "▾", true, () -> {
			openSetting = open ? "" : key;
			render();
		});
		header.setTag("setting_" + key);
		header.setPadding(dp(20), dp(14), dp(20), dp(14));
		header.setBackground(rounded(open ? R.color.surface_selected : R.color.surface_card, 18, R.color.gold, open ? 1 : 0));
		// Misma separación entre cabeceras y entre una cabecera y su contenido desplegado.
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) header.getLayoutParams();
		lp.topMargin = 0;
		lp.bottomMargin = dp(16);
		header.setLayoutParams(lp);
		return open;
	}

	/** Ajuste desplegado; sobrevive a los repintados mientras la app está abierta. */
	private static String openSetting = "";

	/**
	 * Ajustes del asistente fuera del chat: servidor de IA emparejado o guiado local gratuito por reglas.
	 */
	private void assistantServer() {
		com.energym.app.assistant.ProposalController c = state.assistant;
		if (c == null || app.owner() == null)
			return;
		if (c.onDevice()) {
			LinearLayout local = card(Txt.t(R.string.acc_trainer), c.connectionNotice());
			if (!c.downloadOffer().isEmpty())
				primary(action(local, c.downloadOffer(), !c.busy, c::downloadModel));
			text(local, Txt.t(R.string.acc_trainer_on_device), R.style.EnerGym_FieldCaption, 8);
			if (!c.status.isEmpty()) text(local, c.status, R.style.EnerGym_Body, 12);
			return;
		}
		if (c.automaticConnection()) {
			LinearLayout online = card(Txt.t(R.string.acc_ai_assistant), c.connectionNotice());
			text(online, Txt.t(R.string.acc_online_notice), R.style.EnerGym_FieldCaption, 8);
			if (!c.status.isEmpty()) text(online, c.status, R.style.EnerGym_Body, 12);
			return;
		}
		LinearLayout server = card(Txt.t(R.string.acc_ai_assistant), Txt.t(c.remoteActive() ?
			R.string.acc_server_connected : R.string.acc_server_none));
		if (c.connected()) {
			action(server, Txt.t(R.string.acc_disconnect), !c.busy, c::disconnect);
			return;
		}
		action(server, Txt.t(c.showConnection ? R.string.acc_hide_connection : R.string.acc_connect_server), c::toggleConnection);
		if (!c.showConnection)
			return;
		textField(server, "assistant_endpoint", Txt.t(R.string.acc_server_url), c.endpoint, false, false, c::endpointInput);
		textField(server, "assistant_pairing", Txt.t(R.string.acc_pairing_code), c.pairingCode, false, false,
			c::pairingInput);
		android.widget.EditText pairing = server.findViewWithTag("assistant_pairing");
		pairing.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
		pairing.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
		action(server, Txt.t(R.string.acc_connect), !c.busy, () -> c.connect(c.endpoint, c.pairingCode));
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
		LinearLayout card = card(Txt.t(R.string.acc_reminders),
			Txt.t(R.string.acc_reminders_body));
		String days = plan.daysText();
		toggle(card, Txt.t(R.string.acc_training_reminder), plan.training, on -> {
			if (on) requestReminder("training");
			else r.setTraining(false);
		});
		caption(card, days.isEmpty() ? Txt.t(R.string.acc_training_reminder_empty) :
			Txt.t(R.string.acc_training_reminder_days, days, plan.daysPerWeek));
		if (plan.training)
			action(card, Txt.t(R.string.acc_reminder_time, plan.trainingTimeText()), this::pickTrainingTime);
		toggle(card, Txt.t(R.string.acc_water_reminder), plan.water, on -> {
			if (on) requestReminder("water");
			else r.setWater(false);
		});
		caption(card, Txt.t(R.string.acc_water_reminder_hours, com.energym.app.reminders.ReminderPlan.WATER_START_HOUR,
			com.energym.app.reminders.ReminderPlan.WATER_END_HOUR));
		if (plan.water)
			choice(card, "reminder_water_hours", Txt.t(R.string.acc_frequency), java.util.Arrays.asList("1", "2", "3", "4"),
				java.util.Arrays.asList(Txt.n(R.plurals.acc_every_hours, 1), Txt.n(R.plurals.acc_every_hours, 2),
					Txt.n(R.plurals.acc_every_hours, 3), Txt.n(R.plurals.acc_every_hours, 4)),
				Integer.toString(plan.waterHours), value -> r.setWaterHours(Integer.parseInt(value)));
		if ((plan.training || plan.water || notificationsDenied) && !r.canNotify()) {
			text(card, Txt.t(R.string.acc_notifications_off), R.style.EnerGym_Body, 12);
			action(card, Txt.t(R.string.acc_open_notification_settings), () -> startActivity(
				new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
					.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())));
		}
	}

	/** Preferencias de este móvil: unidad de peso del registro de entreno. */
	private void preferences() {
		LinearLayout card = card(Txt.t(R.string.acc_preferences), Txt.t(R.string.acc_preferences_body));
		choice(card, "weight_unit", Txt.t(R.string.acc_weight_unit), java.util.Arrays.asList("kg", "lb"),
			java.util.Arrays.asList(Txt.t(R.string.acc_kilos), Txt.t(R.string.acc_pounds)), Units.lb(requireContext()) ? "lb" : "kg",
			Txt.t(R.string.acc_weight_unit_help),
			v -> Units.setLb(requireContext(), "lb".equals(v)));
		action(card, Txt.t(R.string.acc_replay_tutorial), true,
			() -> startActivity(new android.content.Intent(requireContext(), TutorialActivity.class)))
			.setTag("replay_tutorial");
		if (app.owner() == null)
			return;
		if (com.energym.app.health.HealthSync.available()) {
			LinearLayout health = card("Health Connect", Txt.t(R.string.acc_health_body));
			toggle(health, Txt.t(R.string.acc_health_send), com.energym.app.health.HealthSync.enabled(requireContext()), on -> {
				if (!on) {
					com.energym.app.health.HealthSync.setEnabled(requireContext(), false);
					render();
				} else if (com.energym.app.health.HealthSync.granted(requireContext())) {
					com.energym.app.health.HealthSync.setEnabled(requireContext(), true);
					syncHealth();
					render();
				} else
					try {
						healthPermissions.launch(com.energym.app.health.HealthSync.PERMISSIONS);
					} catch (RuntimeException e) {
						toast(Txt.t(R.string.acc_health_unavailable));
					}
			});
			if (com.energym.app.health.HealthSync.enabled(requireContext()))
				action(health, Txt.t(R.string.acc_send_now), true, this::syncHealth).setTag("health_sync");
		}
		LinearLayout data = card(Txt.t(R.string.acc_csv), Txt.t(R.string.acc_csv_body,
			com.energym.app.nutrition.NutritionController.HISTORY_DAYS / 7));
		action(data, Txt.t(R.string.acc_export_csv), true, () -> exportCsv.launch("energym-" + java.time.LocalDate.now() + ".csv"))
			.setTag("export_csv");
		action(data, Txt.t(R.string.acc_import_csv), state.nutrition != null && !state.nutrition.busy,
			() -> importCsv.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain"}))
			.setTag("import_csv");
		LinearLayout apps = card(getString(R.string.switch_apps_title), getString(R.string.switch_apps_body));
		com.google.android.material.button.MaterialButton importButton = action(apps,
			getString(R.string.switch_apps_import), !app.trainingMutation && app.profile != null,
			() -> importWorkouts.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain"}));
		importButton.setTag("import_workouts");
		primary(importButton);
		action(apps, getString(R.string.switch_apps_export), !app.history.isEmpty(),
			() -> exportStrong.launch("energym-strong-" + java.time.LocalDate.now() + ".csv")).setTag("export_strong");
	}

	private void writeStrong(android.net.Uri uri) {
		if (uri == null || app.owner() == null)
			return;
		String csv = com.energym.app.progress.WorkoutImport.exportStrong(app.history, java.time.ZoneId.systemDefault());
		try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
			if (out == null)
				throw new java.io.IOException();
			out.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			toast(getString(R.string.switch_apps_exported));
		} catch (java.io.IOException | RuntimeException e) {
			toast("No se pudo guardar el archivo");
		}
	}

	private void readWorkouts(android.net.Uri uri) {
		String text = uri == null || app.owner() == null ? null : readText(uri);
		if (text == null)
			return;
		app.importWorkouts(text, (result, added) -> {
			if (!isAdded())
				return;
			String message;
			if (result == null)
				message = getString(R.string.switch_apps_unknown);
			else {
				message = getString(R.string.switch_apps_done, added, result.source == com.energym.app.progress.WorkoutImport.Source.HEVY ?
					"Hevy" : "Strong", result.sessions.size() - added);
				if (!result.unmatched.isEmpty())
					message += "\n\n" + getString(R.string.switch_apps_unmatched, String.join(", ", result.unmatched));
			}
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.switch_apps_title).setMessage(message).setPositiveButton(Txt.t(R.string.common_ok), null).show();
		});
	}

	/** Texto de un archivo elegido (máx. 10 MB, sin BOM); null y aviso si no se puede leer. */
	private String readText(android.net.Uri uri) {
		try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
			if (in == null)
				throw new java.io.IOException();
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			for (int r; (r = in.read(buffer)) > 0; ) {
				bytes.write(buffer, 0, r);
				if (bytes.size() > 10_000_000)
					throw new java.io.IOException("Archivo demasiado grande");
			}
			return bytes.toString("UTF-8").replace("\uFEFF", "");
		} catch (java.io.IOException | RuntimeException e) {
			toast("No se pudo leer el archivo");
			return null;
		}
	}

	private void writeCsv(android.net.Uri uri) {
		if (uri == null || app.owner() == null)
			return;
		com.energym.app.nutrition.NutritionController n = state.nutrition;
		String csv = com.energym.app.progress.CsvBackup.export(app.history, java.time.ZoneId.systemDefault(),
			n == null ? java.util.Collections.emptyList() : n.entries,
			n == null ? new java.util.TreeMap<>() : n.weights, state.measures.days());
		try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
			if (out == null)
				throw new java.io.IOException();
			// BOM para que Excel lea bien las tildes.
			out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
			out.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			toast(Txt.t(R.string.acc_exported));
		} catch (java.io.IOException | RuntimeException e) {
			toast(Txt.t(R.string.acc_save_file_failed));
		}
	}

	private void readCsv(android.net.Uri uri) {
		if (uri == null || app.owner() == null || state.nutrition == null)
			return;
		String text;
		try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
			if (in == null)
				throw new java.io.IOException();
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			for (int r; (r = in.read(buffer)) > 0; ) {
				bytes.write(buffer, 0, r);
				if (bytes.size() > 10_000_000)
					throw new java.io.IOException("Archivo demasiado grande");
			}
			text = bytes.toString("UTF-8").replace("\uFEFF", "");
		} catch (java.io.IOException | RuntimeException e) {
			toast(Txt.t(R.string.acc_read_file_failed));
			return;
		}
		com.energym.app.progress.CsvBackup.Imported data = com.energym.app.progress.CsvBackup.parse(text);
		int measures = 0;
		for (java.util.Map.Entry<java.time.LocalDate, java.util.Map<com.energym.app.nutrition.BodyMeasures.Part, String>> m :
			data.measures.entrySet())
			if (!state.measures.days().containsKey(m.getKey()) && state.measures.save(m.getKey(), m.getValue()).isEmpty())
				measures++;
		int savedMeasures = measures;
		int skipped = data.skipped;
		state.nutrition.importBackup(data.foods, data.weights, (foods, weights) -> {
			if (isAdded())
				new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle(Txt.t(R.string.acc_import_done))
					.setMessage(Txt.t(R.string.acc_import_summary, foods, weights, savedMeasures) +
						(skipped > 0 ? "\n" + Txt.n(R.plurals.acc_import_skipped, skipped) : "") +
						"\n" + Txt.t(R.string.acc_import_workouts))
					.setPositiveButton(Txt.t(R.string.common_ok), null).show();
		});
	}

	private void syncHealth() {
		com.energym.app.nutrition.NutritionController n = state.nutrition;
		com.energym.app.health.HealthSync.sync(requireContext(), app.history,
			n == null ? new java.util.TreeMap<>() : n.weights, message -> {
				if (isAdded())
					toast(message);
			});
	}

	private void toast(String text) {
		android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show();
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
				.setMinute(minute % 60).setTitleText(Txt.t(R.string.acc_reminder_time_title)).build();
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
				.setTitle(Txt.t(R.string.acc_delete_local_title))
				.setMessage(Txt.t(R.string.acc_delete_local_message))
				.setNegativeButton(Txt.t(R.string.common_cancel), null)
				.setPositiveButton(Txt.t(R.string.acc_delete_and_exit), (dialog, which) ->
					((com.energym.app.MainActivity) requireActivity()).state()
						.clearLocalData(requireArguments().getString("owner")))
				.create();
		}
	}
}

