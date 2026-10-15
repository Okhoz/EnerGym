package com.energym.app;

import android.os.Bundle;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.energym.app.visual.VisualizationController;

import java.util.function.Consumer;

import android.view.View;
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
		if (saved != null)
			launchedLease = saved.getString("viewerLease");
		viewer = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			String token =
				result.getData() == null ? launchedLease : result.getData().getStringExtra(AnatomyActivity.EXTRA_LEASE);
			if (state != null && state.visualization != null)
				state.visualization.result(token);
			launchedLease = null;
		});
		state = new ViewModelProvider(this).get(AppState.class);
		state.bindViewer(viewerLaunch);
		findViewById(R.id.account).setOnClickListener(v -> {
			if (state.app != null)
				state.app.navigate(AppController.Screen.ACCOUNT);
		});
		findViewById(R.id.back).setOnClickListener(v -> {
			if (state.app != null)
				state.app.back();
		});
		BottomNavigationView nav = findViewById(R.id.navigation);
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
		if (state == null || isFinishing() || getSupportFragmentManager().isStateSaved())
			return;
		((TextView) findViewById(R.id.status)).setText(
			state.app == null ? (state.fatal.isEmpty() ? "Preparando tu espacio…" : state.fatal) :
				state.accountText() + " · " + state.syncText());
		if (state.app == null)
			return;
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

	@Override
	protected void onDestroy() {
		if (backdrop != null) {
			backdrop.close();
		}
		state.unbindViewer(viewerLaunch);
		state.observe(null);
		super.onDestroy();
	}
}
