package com.energym.app;

import android.os.Bundle;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.energym.app.visual.VisualizationController;

import java.util.function.Consumer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;

import com.energym.app.controller.*;
import com.energym.app.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.constraintlayout.motion.widget.MotionLayout;

import com.energym.app.ui.effects.GlassBackdropController;
import com.energym.app.ui.effects.MotionPreferences;

/**
 * VISTA raíz MVC: aplica insets, muestra fragmentos y remite navegación. Nunca ejecuta SQL ni crea planes.
 */
public final class MainActivity extends AppCompatActivity {
	private AppState state;
	private ActivityResultLauncher<Intent> viewer;
	private String launchedLease;
	private final Consumer<VisualizationController.Request> viewerLaunch = request -> {
		launchedLease = request.lease;
		viewer.launch(
			new Intent(this, AnatomyActivity.class).putExtra(AnatomyActivity.EXTRA_EXERCISE_ID, request.exerciseId)
				.putExtra(AnatomyActivity.EXTRA_LEASE, request.lease));
	};
	private boolean rendering;
	private GlassBackdropController backdrop;
	private MotionLayout screenMotion;

	public AppState state() {
		return state;
	}

	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		screenMotion = findViewById(R.id.screen_motion);
		backdrop = new GlassBackdropController(this, findViewById(R.id.glass_decoration), false);
		// El diseño es oscuro también con el sistema en modo claro: iconos blancos en ambas barras.
		androidx.core.view.WindowInsetsControllerCompat barsController =
			new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
		barsController.setAppearanceLightStatusBars(false);
		barsController.setAppearanceLightNavigationBars(false);
		// La raíz aplica todos los márgenes: consumir evita que Material vuelva a añadir IME a la navegación.
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
		if (saved != null) {
			launchedLease = saved.getString("viewerLease");
		}
		viewer = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			String token =
				result.getData() == null ? launchedLease : result.getData().getStringExtra(AnatomyActivity.EXTRA_LEASE);
			if (state != null && state.visualization != null)
				state.visualization.result(token);
			launchedLease = null;
		});
		state = new ViewModelProvider(this).get(AppState.class);
		if (saved != null)
			pendingAvatar.restore(saved.getString("avatarOwner"), saved.getLong("avatarRevision", -1),
				saved.getString("avatarUri"), state.instanceId.equals(saved.getString("avatarStateInstance")));
		state.bindViewer(viewerLaunch);
		avatarWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
		remoteAvatar = new RemoteAvatarController<>(avatarWorker, androidx.core.content.ContextCompat.getMainExecutor(this),
			GooglePhotoLoader::fetch, this::renderAvatar);
		findViewById(R.id.avatar).setOnClickListener(this::openMenu);
		findViewById(R.id.back).setOnClickListener(v -> {
			if (state.app != null)
				state.app.back();
		});
		BottomNavigationView nav = findViewById(R.id.navigation);
		// Burbuja líquida detrás del icono elegido: se desliza como el agua de una pestaña a otra.
		navBubble = new com.energym.app.ui.LiquidIndicator(this);
		navBubble.setColor(0x38E6B95C);
		((android.widget.FrameLayout) findViewById(R.id.navigation_frame)).addView(navBubble, 0,
			new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		nav.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeNavBubble(false));
		nav.setOnItemSelectedListener(item -> {
			if (rendering || state.app == null)
				return true;
			AppController.Screen screen = AppController.Screen.HOME;
			int id = item.getItemId();
			if (id == R.id.tab_training)
				screen = AppController.Screen.TRAINING;
			else if (id == R.id.tab_assistant)
				screen = AppController.Screen.ASSISTANT;
			else if (id == R.id.tab_history)
				screen = AppController.Screen.HISTORY;
			else if (id == R.id.tab_nutrition)
				screen = AppController.Screen.NUTRITION;
			state.app.navigate(screen);
			if (screen == AppController.Screen.HISTORY)
				state.app.reload();
			return true;
		});
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			public void handleOnBackPressed() {
				if (state.app != null && state.app.screen != state.app.tab)
					state.app.back();
				else {
					setEnabled(false);
					getOnBackPressedDispatcher().onBackPressed();
					setEnabled(true);
				}
			}
		});
		state.observe(this::render);
	}

	private void render() {
		if (state == null || isFinishing()) return;
		// El avatar se invalida aunque los fragmentos deban esperar a onResume.
		if (state.app != null) updateAvatar();
		if (getSupportFragmentManager().isStateSaved()) return;
		((TextView) findViewById(R.id.status)).setText(
			state.app == null ? (state.fatal.isEmpty() ? "Preparando tu espacio…" : state.fatal) :
				state.accountText() + " · " + state.syncText());
		if (state.app == null)
			return;
		queuePendingAvatar();
		rendering = true;
		try {
			AppController app = state.app;
			BottomNavigationView nav = findViewById(R.id.navigation);
			int tab = R.id.tab_home;
			if (app.tab == AppController.Screen.TRAINING)
				tab = R.id.tab_training;
			else if (app.tab == AppController.Screen.ASSISTANT)
				tab = R.id.tab_assistant;
			else if (app.tab == AppController.Screen.HISTORY)
				tab = R.id.tab_history;
			else if (app.tab == AppController.Screen.NUTRITION)
				tab = R.id.tab_nutrition;
			nav.setSelectedItemId(tab);
			if (tab != navBubbleTab) {
				boolean first = navBubbleTab == 0;
				navBubbleTab = tab;
				nav.post(() -> placeNavBubble(!first));
			}
			findViewById(R.id.back).setVisibility(app.screen == app.tab ? View.GONE : View.VISIBLE);
			Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
			String tag = app.screen.name();
			if (current == null || !tag.equals(current.getTag())) {
				// Solo un cambio de ruta inicia movimiento; los datos, teclado y red no lo reinician.
				boolean animate = current != null && MotionPreferences.enabled(this);
				screenMotion.setProgress(1f);
				getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment(app.screen), tag)
					.commitNow();
				if (animate) {
					screenMotion.setProgress(0f);
					screenMotion.transitionToEnd();
				}
			} else if (current instanceof ScreenFragment)
				((ScreenFragment) current).render();
			if (!app.error.isEmpty()) {
				String error = app.error;
				app.dismissError();
				new MaterialAlertDialogBuilder(this).setTitle("Revisa esta acción").setMessage(error)
					.setPositiveButton("Entendido", null).show();
			}
		} finally {
			rendering = false;
		}
	}

	/**
	 * Menú de la foto de perfil: perfil, datos físicos y cuenta (con el entrenador IA).
	 * Sin cuenta, perfil y datos físicos llevan primero a Cuenta para entrar.
	 */
	private void openMenu(View anchor) {
		// Dos toques seguidos no abren un segundo panel encima del primero.
		if (state.app == null || (menuSheet != null && menuSheet.isShowing()))
			return;
		String owner = state.app.owner();
		long menuRevision = state.app.accountRevision();
		android.graphics.Bitmap photo = owner == null ? null : avatars().load(owner);
		if (photo == null && owner != null && remoteAvatar != null)
			photo = remoteAvatar.imageFor(owner, menuRevision, googlePhotoUrl(owner));
		String name, subtitle;
		if (owner == null) {
			name = "Sin cuenta";
			subtitle = "Entra para guardar tu progreso";
		} else if (state.user != null && owner.equals(state.user.getUid())) {
			name = state.user.getDisplayName() == null || state.user.getDisplayName().isEmpty() ? "Tu cuenta" :
				state.user.getDisplayName();
			subtitle = state.user.getEmail() == null ? "Cuenta de Google" : state.user.getEmail();
		} else {
			name = "Demo local";
			subtitle = "Datos solo en este móvil";
		}
		ProfileSheet sheet = new ProfileSheet(this, photo, name, subtitle);
		// Un panel abierto antes de cambiar de cuenta no actúa sobre ninguna de las dos cuentas.
		java.util.function.IntConsumer go = id -> {
			if (java.util.Objects.equals(owner, state.app.owner()) && menuRevision == state.app.accountRevision())
				menuSelected(id);
		};
		sheet.item(R.drawable.ic_training, getString(R.string.menu_profile), false, () -> go.accept(R.id.menu_profile))
			.item(R.drawable.ic_scale, getString(R.string.menu_body), false, () -> go.accept(R.id.menu_body))
			.item(R.drawable.ic_settings, getString(R.string.menu_account), false, () -> go.accept(R.id.menu_account));
		// La foto es el propio botón del menú: con cuenta, aquí se elige, cambia o quita.
		if (owner != null) {
			boolean has = avatars().has(owner);
			sheet.divider().item(R.drawable.ic_photo, has ? "Cambiar foto de perfil" : "Elegir foto de perfil", false, () -> {
				if (!java.util.Objects.equals(owner, state.app.owner()) || menuRevision != state.app.accountRevision())
					return;
				releaseAvatarUri(pendingAvatar.uri());
				pendingAvatar.begin(owner, menuRevision);
				avatarPicker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
					.setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
			});
			if (has)
				sheet.item(R.drawable.ic_account, googlePhotoUrl(owner).isEmpty() ? "Quitar foto" : "Usar foto de Google",
					true, () -> {
						if (!java.util.Objects.equals(owner, state.app.owner()) || menuRevision != state.app.accountRevision())
							return;
						avatars().delete(owner);
						shownAvatar = "";
						updateAvatar();
					});
		}
		menuSheet = sheet;
		sheet.show();
	}

	private ProfileSheet menuSheet;
	private com.energym.app.ui.LiquidIndicator navBubble;
	private int navBubbleTab;

	/** Coloca (o desliza) la burbuja bajo el icono de la pestaña activa, en coordenadas del marco del menú. */
	private void placeNavBubble(boolean animate) {
		if (navBubble == null || navBubbleTab == 0)
			return;
		BottomNavigationView nav = findViewById(R.id.navigation);
		View item = nav.findViewById(navBubbleTab);
		if (item == null || item.getWidth() == 0)
			return;
		View icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
		int[] frame = new int[2], at = new int[2];
		findViewById(R.id.navigation_frame).getLocationInWindow(frame);
		(icon != null ? icon : item).getLocationInWindow(at);
		float density = getResources().getDisplayMetrics().density;
		float cx = at[0] - frame[0] + (icon != null ? icon.getWidth() : item.getWidth()) / 2f;
		float cy = at[1] - frame[1] + (icon != null ? icon.getHeight() / 2f : item.getHeight() / 3f);
		android.graphics.RectF target = new android.graphics.RectF(cx - 32 * density, cy - 16 * density,
			cx + 32 * density, cy + 16 * density);
		navBubble.slideTo(target, animate && com.energym.app.ui.effects.MotionPreferences.enabled(this), null);
	}


	private AvatarStore avatars;
	private String shownAvatar = "";
	private java.util.concurrent.ExecutorService avatarWorker;
	private RemoteAvatarController<android.graphics.Bitmap> remoteAvatar;
	private final PendingAvatarSelection pendingAvatar = new PendingAvatarSelection();
	private boolean avatarCheckPosted;
	private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> avatarPicker =
		registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
			if (uri == null)
				releaseAvatarUri(pendingAvatar.uri());
			if (uri != null && pendingAvatar.owner() != null) {
				// El URI puede esperar a que termine la restauración de la cuenta, incluso tras otro reinicio.
				try {
					getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
				} catch (SecurityException ignored) {
					// Algunos proveedores solo conceden acceso temporal al resultado.
				}
			}
			pendingAvatar.result(uri == null ? null : uri.toString());
			queuePendingAvatar();
		});

	private void queuePendingAvatar() {
		if (pendingAvatar.owner() == null || avatarCheckPosted || state == null || state.app == null ||
			state.accounts == null || !state.accountResolved)
			return;
		avatarCheckPosted = true;
		// La identidad debe estar confirmada; no se deduce del estado provisional al inicializar la app.
		findViewById(R.id.avatar).post(() -> {
			avatarCheckPosted = false;
			if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved() || state.app == null ||
				state.accounts == null || !state.accountResolved)
				return;
			String owner = pendingAvatar.owner(), previousUri = pendingAvatar.uri();
			String uri = pendingAvatar.take(state.app.owner(), state.app.accountRevision(),
				state.accountStatus != AccountController.Status.SIGNING_IN);
			if (uri == null) {
				if (pendingAvatar.owner() == null)
					releaseAvatarUri(previousUri);
				return;
			}
			try {
				avatars().save(owner, android.net.Uri.parse(uri));
			} catch (Exception e) {
				new MaterialAlertDialogBuilder(this).setTitle("Foto de perfil")
					.setMessage("No se pudo usar esa imagen. Prueba con otra.").setPositiveButton("Entendido", null).show();
			} finally {
				releaseAvatarUri(uri);
			}
			shownAvatar = "";
			updateAvatar();
		});
	}

	private void releaseAvatarUri(String uri) {
		if (uri == null)
			return;
		try {
			getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(uri),
				Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException ignored) {
			// No hay permiso persistente si el proveedor solo concedió acceso temporal.
		}
	}

	private AvatarStore avatars() {
		if (avatars == null)
			avatars = new AvatarStore(this);
		return avatars;
	}

	/** Solo usa metadatos de la misma identidad que tiene abierto el almacenamiento. */
	private String googlePhotoUrl(String owner) {
		return owner != null && state.user != null && owner.equals(state.user.getUid()) ? state.user.getPhotoUrl() : "";
	}

	/** La foto manual elegida por el usuario prevalece; en su ausencia se carga la foto de Google. */
	private void updateAvatar() {
		if (remoteAvatar == null) return;
		String owner = state == null || state.app == null ? null : state.app.owner();
		long revision = state.app.accountRevision();
		boolean local = owner != null && avatars().has(owner);
		String url = local ? "" : googlePhotoUrl(owner);
		String key = owner + ":" + revision + ":" + local + ":" + url;
		if (key.equals(shownAvatar)) return;
		shownAvatar = key;
		remoteAvatar.update(local ? null : owner, revision, url);
		renderAvatar(remoteAvatar.image());
	}

	/** La imagen cambia, pero el listener del avatar sigue abriendo el mismo menú. */
	private void renderAvatar(android.graphics.Bitmap ignored) {
		if (isFinishing() || isDestroyed()) return;
		String owner = state == null || state.app == null ? null : state.app.owner();
		android.graphics.Bitmap photo = owner == null ? null : avatars().load(owner);
		if (photo == null && owner != null && remoteAvatar != null)
			photo = remoteAvatar.imageFor(owner, state.app.accountRevision(), googlePhotoUrl(owner));
		android.widget.ImageView avatar = findViewById(R.id.avatar);
		int padding = photo == null ? Math.round(10 * getResources().getDisplayMetrics().density) : 0;
		avatar.setPadding(padding, padding, padding, padding);
		if (photo == null) avatar.setImageResource(R.drawable.ic_account);
		else avatar.setImageBitmap(photo);
	}

	/**
	 * Destino de cada opción del menú de perfil (también lo usan las pruebas instrumentadas).
	 */
	public boolean menuSelected(int id) {
		AppController app = state.app;
		if (app == null)
			return false;
		if (id != R.id.menu_account && app.owner() == null)
			app.navigate(AppController.Screen.ACCOUNT);
		else if (id == R.id.menu_profile)
			app.navigate(AppController.Screen.PROFILE);
		else if (id == R.id.menu_body) {
			if (state.nutrition != null)
				state.nutrition.fillBody();
			app.navigate(AppController.Screen.BODY);
		} else
			app.navigate(AppController.Screen.ACCOUNT);
		return true;
	}

	private Fragment fragment(AppController.Screen screen) {
		switch (screen) {
			case TRAINING:
				return new TrainingFragment();
			case ASSISTANT:
				return new AssistantFragment();
			case HISTORY:
				return new HistoryFragment();
			case PROFILE:
				return new ProfileFragment();
			case ROUTINE:
				return new RoutineFragment();
			case MANUAL_ROUTINE:
				return new ManualRoutineFragment();
			case SESSION:
				return new SessionFragment();
			case EXERCISE:
				return new ExerciseFragment();
			case ACCOUNT:
				return new AccountFragment();
			case NUTRITION:
				return new NutritionFragment();
			case FOOD:
				return new FoodFragment();
			case BODY:
				return new BodyFragment();
			default:
				return new HomeFragment();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (backdrop != null)
			backdrop.resume();
		render();
	}

	@Override
	protected void onSaveInstanceState(Bundle out) {
		out.putString("viewerLease", launchedLease);
		out.putString("avatarOwner", pendingAvatar.owner());
		out.putLong("avatarRevision", pendingAvatar.revision());
		out.putString("avatarUri", pendingAvatar.uri());
		out.putString("avatarStateInstance", state.instanceId);
		super.onSaveInstanceState(out);
	}

	@Override
	protected void onPause() {
		MotionPreferences.cancelButtons(findViewById(R.id.main));
		if (backdrop != null)
			backdrop.pause();
		if (screenMotion != null)
			screenMotion.setProgress(1f);
		super.onPause();
	}

	@Override
	public void onLowMemory() {
		if (backdrop != null)
			backdrop.lowMemory();
		super.onLowMemory();
	}

	@Override
	public void onTrimMemory(int level) {
		if (backdrop != null && (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
			level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
			level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
			backdrop.lowMemory();
		super.onTrimMemory(level);
	}

	/** Cada vez que se abre la app, los anillos de progreso se rellenan de nuevo hasta su valor. */
	@Override
	protected void onStart() {
		super.onStart();
		com.energym.app.ui.charts.RingView.replayOnNextShow();
	}

	@Override
	protected void onDestroy() {
		if (menuSheet != null && menuSheet.isShowing()) menuSheet.dismiss();
		menuSheet = null;
		if (remoteAvatar != null) remoteAvatar.close();
		if (avatarWorker != null) avatarWorker.shutdownNow();
		if (isFinishing())
			releaseAvatarUri(pendingAvatar.uri());
		if (backdrop != null) {
			backdrop.close();
		}
		state.unbindViewer(viewerLaunch);
		state.observe(null);
		super.onDestroy();
	}
}
