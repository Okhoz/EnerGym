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
	public AppController app;
	public AccountController accounts;
	public SyncController sync;
	public ProposalController assistant;
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
		assistant = new ProposalController(store, new LocalConversationStore(context, worker),
			new HttpChatClient(chatWorker, main,
				(context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0),
			catalog.getExercises(), catalog.getVersion(), () -> app.profile, this::changed, r -> {
			app.routineAccepted(r);
		});
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
			changed();
		});
		accounts = new AccountController(gateway, sync, main, (status, value, error) -> {
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

