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

	/** Copia en CSV: se elige dónde guardarla o qué archivo abrir con el selector de Android (sin permisos). */
	private final androidx.activity.result.ActivityResultLauncher<String> exportCsv = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"), this::writeCsv);
	private final androidx.activity.result.ActivityResultLauncher<String[]> importCsv = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::readCsv);

	private final androidx.activity.result.ActivityResultLauncher<String[]> healthPermissions = registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
			boolean ok = com.energym.app.health.HealthSync.granted(requireContext());
			com.energym.app.health.HealthSync.setEnabled(requireContext(), ok);
			if (ok)
				syncHealth();
			else
				toast("Sin permiso no se puede enviar a Health Connect");
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
		heading("", "Cuenta", "");
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
		// Ajustes como lista plegable: solo uno abierto a la vez para no saturar la pantalla.
		section("Ajustes", 32);
		if (fold("backup", "☁️  Respaldo")) {
			LinearLayout backup =
				card("Respaldo", state.syncText() + (state.syncError.isEmpty() ? "" : "\n" + state.syncError));
			if (user != null)
				action(backup, "Reintentar sincronización", !signing, state.sync::requestSync);
			text(backup, getString(R.string.account_separation), R.style.EnerGym_FieldCaption, 12);
		}
		if (app.owner() != null && state.assistant != null && fold("assistant", "✦  Entrenador con IA"))
			assistantServer();
		if (state.reminders != null && fold("reminders", "⏰  Recordatorios"))
			reminders();
		if (fold("preferences", "⚙️  Preferencias, Health Connect y CSV"))
			preferences();
		if (!fold("device", "🗑  Datos de este dispositivo"))
			return;
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

	/** Cabecera plegable de un ajuste; devuelve si está abierto. Abrir uno cierra el anterior. */
	private boolean fold(String key, String label) {
		boolean open = key.equals(openSetting);
		com.google.android.material.button.MaterialButton header = action(rows, label + (open ? "   ▴" : "   ›"), true, () -> {
			openSetting = open ? "" : key;
			render();
		});
		header.setTag("setting_" + key);
		header.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
		header.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
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
		if (app.owner() == null)
			return;
		if (com.energym.app.health.HealthSync.available()) {
			LinearLayout health = card("Health Connect", "Envía tus entrenos terminados y tu peso a Health Connect para " +
				"verlos en Samsung Health u otras apps. EnerGym no lee nada de allí.");
			toggle(health, "Enviar a Health Connect", com.energym.app.health.HealthSync.enabled(requireContext()), on -> {
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
						toast("Health Connect no está disponible en este móvil");
					}
			});
			if (com.energym.app.health.HealthSync.enabled(requireContext()))
				action(health, "Enviar ahora", true, this::syncHealth).setTag("health_sync");
		}
		LinearLayout data = card("Tus datos en CSV", "Exporta tus entrenos serie a serie, comidas (últimas " +
			com.energym.app.nutrition.NutritionController.HISTORY_DAYS / 7 + " semanas), peso y medidas para abrirlos en " +
			"Excel o Google Sheets. Al importar se recuperan comidas, peso y medidas sin duplicarlas.");
		action(data, "Exportar a CSV", true, () -> exportCsv.launch("energym-" + java.time.LocalDate.now() + ".csv"))
			.setTag("export_csv");
		action(data, "Importar CSV", state.nutrition != null && !state.nutrition.busy,
			() -> importCsv.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain"}))
			.setTag("import_csv");
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
			toast("Exportado");
		} catch (java.io.IOException | RuntimeException e) {
			toast("No se pudo guardar el archivo");
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
			toast("No se pudo leer el archivo");
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
					.setTitle("Importación terminada")
					.setMessage(foods + " comidas, " + weights + " pesos y " + savedMeasures + " días de medidas añadidos." +
						(skipped > 0 ? "\n" + skipped + " filas no se entendieron y se han saltado." : "") +
						"\nLos entrenos ya están en tu cuenta, así que no se importan de nuevo.")
					.setPositiveButton("Vale", null).show();
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

