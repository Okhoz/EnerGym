package com.energym.app.controller;

import android.app.Application;
import android.net.*;

import androidx.lifecycle.AndroidViewModel;
import androidx.core.content.ContextCompat;

import com.energym.app.auth.*;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.sync.*;
import com.energym.app.assistant.*;

import java.util.concurrent.*;

import com.energym.app.visual.*;

import android.content.Intent;

import com.energym.app.ui.AnatomyActivity;

import java.util.function.Consumer;
import java.util.Collections;

/**
 * Propietario Android del ciclo de vida, no ViewModel de negocio: conserva los controladores MVC al rotar.
 * Un único worker serializa SQLite/cloud; todos los eventos que renderizan se entregan en main.
 */
public final class AppState extends AndroidViewModel {
	// La instancia retenida conserva las revisiones; una instancia nueva debe restaurar la identidad.
	public final String instanceId = java.util.UUID.randomUUID().toString();
	public AppController app;
	// Estado transitorio de vista: sobrevive a técnica/back y se vacía al cambiar de cuenta o borrador.
	public final com.energym.app.ui.TrainingViewState training = new com.energym.app.ui.TrainingViewState();
	public final com.energym.app.ui.ExerciseBrowserState exerciseBrowser = new com.energym.app.ui.ExerciseBrowserState();
	public final com.energym.app.ui.ExerciseBrowserState manualExerciseBrowser = new com.energym.app.ui.ExerciseBrowserState();
	public AccountController accounts;
	public SyncController sync;
	public ProposalController assistant;
	// Distingue la identidad confirmada del estado provisional durante el arranque.
	public boolean accountResolved;
	public AccountController.Status accountStatus = AccountController.Status.CONFIGURATION_NEEDED;
	public SyncCoordinator.Status syncStatus = SyncCoordinator.Status.SIGNED_OUT;
	public AccountUser user;
	public String fatal = "", syncError = "";
	private final ExecutorService worker = Executors.newSingleThreadExecutor();
	private final CallbackExecutor cloudCallbacks = new CallbackExecutor(worker);
	private final Executor main;
	// Una consulta HTTP lenta no debe bloquear guardar perfiles/sesiones en el worker SQLite/cloud.
	private final ExecutorService chatWorker = Executors.newSingleThreadExecutor();
	private GoogleAccountGateway gateway;
	private Runnable observer = () -> {
	};
	private boolean cleared;
	public boolean deletingLocalData;
	public VisualizationController visualization;
	public com.energym.app.nutrition.NutritionController nutrition;
	public com.energym.app.progress.GoalsController goals;
	public com.energym.app.nutrition.DietPlanner diet;
	public com.energym.app.nutrition.WaterLog water;
	public com.energym.app.reminders.ReminderController reminders;
	/** Ejercicios creados por la persona, por cuenta y solo en este móvil. */
	public com.energym.app.catalog.CustomExercises customExercises;
	public com.energym.app.nutrition.SavedMeals savedMeals;
	public com.energym.app.nutrition.BodyMeasures measures;
	private com.energym.app.nutrition.SyncedNutritionStore nutritionStore;
	// Consultas a Open Food Facts: independientes del chat y de SQLite.
	private final ExecutorService lookupWorker = Executors.newSingleThreadExecutor();
	private Consumer<VisualizationController.Request> viewerLaunch;

	public void bindViewer(Consumer<VisualizationController.Request> launch) {
		viewerLaunch = launch;
		if (visualization != null)
			visualization.bind(launch);
	}

	public void unbindViewer(Consumer<VisualizationController.Request> launch) {
		if (viewerLaunch == launch)
			bindViewer(null);
	}

	private ConnectivityManager connectivity;
	private ConnectivityManager.NetworkCallback networkCallback;

	public AppState(Application application) {
		super(application);
		main = ContextCompat.getMainExecutor(application);
		worker.execute(() -> {
			try {
				CatalogRepository catalog = CatalogRepository.load(application);
				main.execute(() -> {
					if (!cleared)
						initialize(application, catalog);
				});
			} catch (Exception e) {
				main.execute(() -> {
					fatal = "No se pudo leer el catálogo: " + e.getMessage();
					changed();
				});
			}
		});
	}

	private void initialize(Application context, CatalogRepository catalog) {
		gateway = new GoogleAccountGateway(context, main);
		sync = new SyncController(context, gateway, new FirebaseCloudGateway(gateway, cloudCallbacks), worker, main,
			(status, error) -> {
				syncStatus = status;
				syncError = error == null ? "" : error.getMessage();
				if (app != null && status == SyncCoordinator.Status.SYNCED) {
					app.reload();
					// Una restauración también puede traer diario, peso y objetivos de otro dispositivo.
					if (nutrition != null)
						nutrition.reload();
					if (goals != null)
						goals.reload();
				}
				changed();
			});
		AndroidAppStore store = new AndroidAppStore(sync);
		app = new AppController(store, catalog);
		nutritionStore = new com.energym.app.nutrition.SyncedNutritionStore(context, sync);
		// Tabla de alimentos habituales incluida en la app (sin red).
		try (java.io.InputStream foods = context.getAssets().open("foods.tsv")) {
			com.energym.app.nutrition.CommonFoods.load(foods);
		} catch (java.io.IOException ignored) {
			// Sin tabla, el buscador de alimentos habituales queda vacío; escanear y la etiqueta siguen igual.
		}
		String version;
		try {
			version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (Exception e) {
			version = "1";
		}
		goals = new com.energym.app.progress.GoalsController(new com.energym.app.progress.SyncedGoalStore(sync),
			this::changed, message -> app.error(message));
		nutrition = new com.energym.app.nutrition.NutritionController(nutritionStore,
			new com.energym.app.nutrition.OpenFoodFactsLookup(lookupWorker, main, version), java.time.LocalDate::now,
			this::changed, message -> app.error(message));
		// Entrenador personal con Gemini Nano en el propio móvil: sin servidor de IA ni coste. Si el móvil no lo
		// admite, el chat usa el guiado local por reglas (los clientes online quedan sin usar).
		ChainedModel onDevice = new ChainedModel(new GeminiNanoModel(chatWorker), new GemmaModel(context, chatWorker));
		OnDeviceChatClient trainer = new OnDeviceChatClient(onDevice, catalog.getExercises(), catalog.getVersion(),
			chatWorker, main);
		android.content.SharedPreferences glasses = context.getSharedPreferences("water_log", android.content.Context.MODE_PRIVATE);
		water = new com.energym.app.nutrition.WaterLog(new com.energym.app.nutrition.WaterLog.Store() {
			public int load(String key) {
				return glasses.getInt(key, 0);
			}

			public void save(String key, int ml) {
				glasses.edit().putInt(key, ml).apply();
			}
		}, this::changed);
		android.content.SharedPreferences customPrefs = context.getSharedPreferences("custom_exercises",
			android.content.Context.MODE_PRIVATE);
		customExercises = new com.energym.app.catalog.CustomExercises(new com.energym.app.catalog.CustomExercises.Store() {
			public java.util.List<com.energym.app.domain.Exercise> load(String owner) {
				java.util.List<com.energym.app.domain.Exercise> result = new java.util.ArrayList<>();
				try {
					org.json.JSONArray rows = new org.json.JSONArray(customPrefs.getString(owner, "[]"));
					for (int i = 0; i < rows.length(); i++)
						result.add(com.energym.app.data.SnapshotCodec.decodeExercise(rows.getString(i)));
				} catch (Exception ignored) {
					// Datos dañados: se empieza sin ejercicios propios.
				}
				return result;
			}

			public void save(String owner, java.util.List<com.energym.app.domain.Exercise> exercises) {
				org.json.JSONArray rows = new org.json.JSONArray();
				for (com.energym.app.domain.Exercise e : exercises)
					rows.put(com.energym.app.data.SnapshotCodec.encodeExercise(e));
				customPrefs.edit().putString(owner, rows.toString()).apply();
			}
		}, catalog, this::changed);
		android.content.SharedPreferences mealPrefs = context.getSharedPreferences("saved_meals",
			android.content.Context.MODE_PRIVATE);
		savedMeals = new com.energym.app.nutrition.SavedMeals(new com.energym.app.nutrition.SavedMeals.Store() {
			public String load(String owner) {
				return mealPrefs.getString(owner, "");
			}

			public void save(String owner, String encoded) {
				mealPrefs.edit().putString(owner, encoded).apply();
			}
		});
		android.content.SharedPreferences measurePrefs = context.getSharedPreferences("body_measures",
			android.content.Context.MODE_PRIVATE);
		measures = new com.energym.app.nutrition.BodyMeasures(new com.energym.app.nutrition.BodyMeasures.Store() {
			public String load(String owner) {
				return measurePrefs.getString(owner, "");
			}

			public void save(String owner, String encoded) {
				measurePrefs.edit().putString(owner, encoded).apply();
			}
		});
		// Recordatorios de entreno y agua: alarmas y avisos locales, sin servidor.
		reminders = new com.energym.app.reminders.ReminderController(context, this::changed);
		// Dietas con la misma IA del móvil; el último menú de cada cuenta se guarda solo en este teléfono.
		android.content.SharedPreferences diets = context.getSharedPreferences("diet_plans", android.content.Context.MODE_PRIVATE);
		diet = new com.energym.app.nutrition.DietPlanner(onDevice, new com.energym.app.nutrition.DietPlanner.Store() {
			public String load(String owner) {
				return diets.getString(owner, "");
			}

			public void save(String owner, String json) {
				diets.edit().putString(owner, json).apply();
			}
		}, main, this::changed, () -> app.profile == null ? "" : app.profile.getDaysPerWeek() + " días por semana de " +
			app.disciplineName() + ", sesiones de " + app.profile.getDurationMinutes() + " min",
			java.time.LocalDate::now, day -> nutrition.historyCalories(day));
		trainer.listen(this::changed);
		// Al abrir la app (aunque aún no haya cuenta) se comprueba el modelo y, si falta, empieza su descarga.
		trainer.activate(null);
		assistant = new ProposalController(store, new LocalConversationStore(context, worker), trainer,
			catalog.getExercises(), catalog.getVersion(), () -> app.profile, this::changed, r -> {
			app.routineAccepted(r);
		});
		assistant.carryOver(app::continueFrom);
		assistant.weeklyPlan(asked -> {
			app.createPlan(asked);
			if (app.planPreview.isEmpty())
				return "No he podido preparar el plan: revisa en Perfil tus días por semana, lugar y material.";
			java.util.List<String> days = new java.util.ArrayList<>();
			for (com.energym.app.domain.Routine r : app.planPreview)
				days.add("• " + r.getTitle() + " (" + r.getItems().size() + " ejercicios)");
			return "📅 Te he preparado un plan de " + app.planPreview.size() + " días:\n" + String.join("\n", days) +
				"\n\nRevísalo y pulsa «Guardar plan». Al terminar cada día te dejo listo el siguiente y subo las " +
				"cargas cuando cumplas el objetivo. 💪";
		});
		// El entrenador ve todo lo que la persona ha hecho en la app (solo en el móvil, nunca en la red).
		assistant.trainingContext(() -> AssistantContext.build(app, nutrition, goals, diet, java.time.LocalDate.now(), water));
		ExerciseResourcePolicy resourcePolicy;
		try {
			resourcePolicy = new ExerciseResourcePolicy(catalog, PackagedResources.load(context).all());
		} catch (Exception e) {
			resourcePolicy = new ExerciseResourcePolicy(catalog, Collections.emptyList());
		}
		visualization = new VisualizationController(resourcePolicy, new VisualizationController.Leases() {
			public void publish(VisualizationController.Request request) {
				ViewerLeaseRegistry.publish(request.lease, request.exerciseId);
			}

			public void revoke(String lease) {
				ViewerLeaseRegistry.revoke(lease);
			}
		}, () -> context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName())));
		visualization.bind(viewerLaunch);
		app.visualizations((exercise, profile) -> visualization.open(exercise), resourcePolicy::available,
			resourcePolicy.available());
		app.visualizationExplanations(resourcePolicy::explanation);
		assistant.openCommands(app::navigationRevision, command -> {
			if (!visualization.assistant(command.snapshot, assistant.exercise, command.conversationId,
				command.requestId, command.originRevision)) {
				assistant.status =
					"Esta referencia no está disponible en el contexto actual. Consulta la explicación escrita del ejercicio seleccionado.";
			}
		});
		app.explanations((exercise, profile) -> {
			assistant.explain(exercise);
			app.navigate(AppController.Screen.ASSISTANT);
		});
		app.observe(() -> {
			assistant.activate(app.owner());
			nutrition.activate(app.owner());
			goals.activate(app.owner());
			diet.activate(app.owner());
			water.activate(app.owner());
			customExercises.activate(app.owner());
			savedMeals.activate(app.owner());
			measures.activate(app.owner());
			changed();
		});
		accounts = new AccountController(gateway, sync, main, (status, value, error) -> {
			accountResolved = true;
			accountStatus = status;
			user = value;
			if (status == AccountController.Status.AUTHENTICATED ||
				(status == AccountController.Status.ERROR && value != null))
				app.activate(value.getUid());
			else if (status == AccountController.Status.DEMO_LOCAL)
				app.activate(EnerGymDatabase.DEMO_OWNER_UID);
			else if (status != AccountController.Status.ERROR)
				app.activate(null);
			if (error != null)
				app.error("Cuenta: " + error.getMessage());
			changed();
		});
		accounts.start();
		monitorNetwork(context);
		changed();
	}

	public void observe(Runnable observer) {
		this.observer = observer == null ? () -> {
		} : observer;
		changed();
	}

	private void changed() {
		if (app != null) {
			training.resetFor(app.owner());
			exerciseBrowser.resetFor(app.owner(), 0);
			manualExerciseBrowser.resetFor(app.owner(), app.manual.draftRevision());
			if (reminders != null && app.profile != null)
				reminders.syncDays(app.profile.getDaysPerWeek());
			if (reminders != null && water != null && nutrition != null && app.owner() != null) {
				java.time.LocalDate today = java.time.LocalDate.now();
				boolean trained = false;
				for (com.energym.app.domain.Session s : app.history)
					if (s.getStatus() == com.energym.app.domain.Session.Status.FINISHED && java.time.Instant
						.ofEpochMilli(s.getCompletedAt()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
						.equals(today))
						trained = true;
				reminders.today(today, water.ml(today),
					com.energym.app.nutrition.WaterLog.goal(nutrition.effectiveBody()), trained);
			}
		}
		if (visualization != null && app != null)
			visualization.context(app.owner(), app.navigationRevision(), app.screen, app.exercise);
		observer.run();
	}

	public String accountText() {
		if (accountStatus == AccountController.Status.DEMO_LOCAL)
			return "Demo · solo en este dispositivo";
		if (user != null)
			return "Google · " + (user.getDisplayName().isEmpty() ? user.getEmail() : user.getDisplayName());
		if (accountStatus == AccountController.Status.SIGNING_IN)
			return "Conectando con Google…";
		return "Cuenta sin conectar";
	}

	/**
	 * Solo se llama después de confirmar en Cuenta. Salir impide restaurar Firebase hasta otra entrada.
	 */
	public void clearLocalData(String expectedOwner) {
		if (cleared || deletingLocalData || app == null || expectedOwner == null || !expectedOwner.equals(app.owner()))
			return;
		deletingLocalData = true;
		assistant.clearLocalData();
		// El diario sincronizable se borra con la base de la cuenta; aquí solo resta el archivo de la versión anterior.
		worker.execute(() -> nutritionStore.clearLegacy(expectedOwner));
		if (visualization != null)
			visualization.revoke();
		app.activate(null);
		sync.clearLocalData(new SyncController.DataCallback<Void>() {
			public void onSuccess(Void ignored) {
				deletingLocalData = false;
				if (!cleared) {
					app.notice =
						"Datos locales borrados. Al conectar Google de nuevo se restaurarán los datos confirmados en Firebase.";
					app.navigate(AppController.Screen.ACCOUNT);
				}
			}

			public void onFailure(Exception error) {
				deletingLocalData = false;
				if (!cleared)
					app.error("No se pudo completar el borrado local: " + error.getMessage());
			}
		});
		accounts.signOut();
		changed();
	}

	public String syncText() {
		if (app != null && EnerGymDatabase.DEMO_OWNER_UID.equals(app.owner()))
			return "Sin respaldo cloud · demo local";
		switch (syncStatus) {
			case SYNCED:
				return "Respaldo confirmado";
			case SYNCING:
				return "Sincronizando · respaldo pendiente";
			case CONFLICT:
				return "Conflicto · cambios locales pendientes";
			case ERROR:
				return "Error de respaldo · reintenta con conexión";
			case IDLE:
				return "Respaldo pendiente";
			default:
				return "Conecta Google para respaldar";
		}
	}

	/**
	 * Reintento solo al recuperar conectividad, sin descargas por pulsación ni sondeo periódico.
	 */
	private void monitorNetwork(Application context) {
		connectivity = context.getSystemService(ConnectivityManager.class);
		networkCallback = new ConnectivityManager.NetworkCallback() {
			private boolean disconnected;

			@Override
			public void onLost(Network network) {
				disconnected = true;
			}

			@Override
			public void onAvailable(Network network) {
				if (disconnected) {
					disconnected = false;
					main.execute(() -> {
						if (!cleared)
							sync.requestSync();
					});
				}
			}
		};
		try {
			connectivity.registerDefaultNetworkCallback(networkCallback);
		} catch (RuntimeException e) {
			networkCallback = null;
		}
	}

	@Override
	protected void onCleared() {
		cleared = true;
		cloudCallbacks.close();
		if (visualization != null)
			visualization.revoke();
		viewerLaunch = null;
		observer = () -> {
		};
		if (assistant != null)
			assistant.disconnect();
		chatWorker.shutdown();
		lookupWorker.shutdown();
		if (app != null)
			app.observe(null);
		if (networkCallback != null)
			connectivity.unregisterNetworkCallback(networkCallback);
		if (accounts != null)
			accounts.close();
		if (sync != null)
			sync.close();
		worker.shutdown();
	}
}

