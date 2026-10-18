package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.ActivityManager;
import android.app.Application;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.energym.app.R;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.Exercise;
import com.energym.app.visual.*;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * VISTA MVC privada: muestra estados y remite controles; no interpreta reglas del ejercicio.
 */
public final class AnatomyActivity extends AppCompatActivity implements ViewerController.View {
	public static final String EXTRA_EXERCISE_ID = "com.energym.app.visual.EXERCISE_ID";
	public static final String EXTRA_LEASE = "com.energym.app.visual.LEASE";
	public static final String ACTION_CLOSE = "com.energym.app.visual.CLOSE";
	private static int liveActivities;
	private boolean counted, receiverRegistered, contextCancelled;
	private final BroadcastReceiver contextClose = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_CLOSE.equals(intent.getAction()) && getPackageName().equals(intent.getPackage())) {
				contextCancelled = true;
				finish();
			}
		}
	};
	private final Handler main = new Handler(Looper.getMainLooper());
	private final ExecutorService io = Executors.newSingleThreadExecutor();
	private Future<?> read;
	private ViewerController controller;
	private NativeAnimationRenderer renderer;
	private ResourceManifest manifest;
	private boolean destroyed, resumed;
	private long generation;
	private String exerciseId, lease;
	private boolean writtenReferenceAvailable;
	private ViewerState restored;
	private SurfaceView surface;
	private TextView status, loadMessage;
	private android.view.View loading, controls;
	private ProgressBar progress;

	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		liveActivities++;
		counted = true;
		setContentView(R.layout.activity_anatomy);
		ViewCompat.setAccessibilityHeading(findViewById(R.id.viewer_title), true);
		ViewCompat.setAccessibilityHeading(findViewById(R.id.viewer_availability_title), true);
		androidx.core.content.ContextCompat.registerReceiver(this, contextClose, new IntentFilter(ACTION_CLOSE),
			androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
		receiverRegistered = true;
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.anatomy_root), (v, insets) -> {
			androidx.core.graphics.Insets bars =
				insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
		surface = findViewById(R.id.viewer_surface);
		status = findViewById(R.id.viewer_status);
		loadMessage = findViewById(R.id.viewer_load_message);
		progress = findViewById(R.id.viewer_progress);
		loading = findViewById(R.id.viewer_loading);
		controls = findViewById(R.id.viewer_controls);
		findViewById(R.id.viewer_back).setOnClickListener(v -> finish());
		try {
			exerciseId = getIntent().getStringExtra(EXTRA_EXERCISE_ID);
			lease = getIntent().getStringExtra(EXTRA_LEASE);
		} catch (RuntimeException e) {
			failure(Txt.t(R.string.an_bad_context));
			return;
		}
		if (exerciseId == null || !exerciseId.matches("[a-z][a-z0-9-]{0,79}")) {
			failure(Txt.t(R.string.an_pick_exercise));
			return;
		}
		if (!authorized())
			return;
		if (saved != null && exerciseId.equals(saved.getString("exerciseId")))
			restored =
				new ViewerState(false, false, saved.getBoolean("playing", true), false, saved.getFloat("playhead"),
					saved.getFloat("speed", 1), saved.getFloat("yaw", 90), saved.getFloat("pitch", 25),
					saved.getFloat("zoom", 1), "");
		bindControls();
		load();
	}

	/**
	 * La lectura completa y el hash corren en I/O; una generación descarta resultados tras salir.
	 */
	private void load() {
		long token = ++generation;
		read = io.submit(() -> {
			try {
				CatalogRepository catalog = CatalogRepository.load(getApplicationContext());
				Exercise exercise = catalog.findExercise(exerciseId);
				if (exercise == null)
					throw new IOException(Txt.t(R.string.an_not_in_catalog));
				// La referencia escrita no depende de que el archivo animado pase su hash o llegue a JNI.
				main.post(() -> {
					if (current(token))
						showExercise(exercise);
				});
				ResourceRepository repo = PackagedResources.load(getApplicationContext());
				ResourceManifest resource = repo.forExercise(exerciseId);
				if (resource == null) {
					main.post(() -> {
						if (current(token))
							failure(Txt.t(R.string.an_no_animation));
					});
					return;
				}
				byte[] bytes = repo.loadVerified(resource);
				main.post(() -> {
					if (!current(token))
						return;
					manifest = resource;
					((TextView) findViewById(R.id.viewer_credits)).setText(credits(resource));
					controller = new ViewerController(manifest, this);
					controller.restore(restored);
					try {
						renderer = new NativeAnimationRenderer(this, surface, controller,
							new NativeAnimationRenderer.Listener() {
								public void progress(float value) {
									if (!current(token))
										return;
									progress.setIndeterminate(false);
									progress.setProgress(Math.round(value * 100));
									loadMessage.setText(Txt.t(R.string.an_loading, Math.round(value * 100)));
								}

								public void ready(String evidence) {
									if (current(token)) {
										controller.ready();
									}
								}

								public void failure(String message) {
									main.post(() -> {
										if (current(token))
											AnatomyActivity.this.failure(message);
									});
								}
							});
						renderer.load(bytes, manifest);
						if (resumed)
							renderer.resume();
					} catch (RuntimeException | LinkageError e) {
						Log.e("EnerGymViewer", "Inicio nativo fallido", e);
						failure(e instanceof UnsupportedOperationException ?
							Txt.t(R.string.an_no_vulkan) : Txt.t(R.string.an_viewer_failed));
					}
				});
			} catch (IOException | RuntimeException e) {
				main.post(() -> {
					if (current(token)) {
						Log.w("EnerGymViewer", "Recurso no disponible", e);
						failure(e.getMessage() == null ? Txt.t(R.string.an_resource_unavailable) : e.getMessage());
					}
				});
			}
		});
	}

	private static String credits(ResourceManifest resource) {
		if (resource.kind == ResourceManifest.Kind.SCHEMATIC)
			return Txt.t(R.string.an_credits_generated);
		return Txt.t(R.string.anatomy_credits);
	}

	private boolean current(long token) {
		return !destroyed && token == generation && authorized();
	}

	private boolean authorized() {
		if (ViewerLeaseProvider.valid(this, lease, exerciseId))
			return true;
		contextCancelled = true;
		finish();
		return false;
	}

	private void showExercise(Exercise exercise) {
		writtenReferenceAvailable = true;
		((TextView) findViewById(R.id.viewer_title)).setText(exercise.getName());
		((TextView) findViewById(R.id.viewer_written_reference)).setText(
			Txt.t(R.string.an_written_reference) + "\n" + exercise.getDescription());
	}

	private void bindControls() {
		findViewById(R.id.viewer_play).setOnClickListener(v -> send(
			controller != null && controller.state().playing ? ViewerCommand.Action.PAUSE : ViewerCommand.Action.PLAY,
			"", 0, 0));
		bind(R.id.viewer_speed_quarter, ViewerCommand.Action.SPEED, "", .25f);
		bind(R.id.viewer_speed_half, ViewerCommand.Action.SPEED, "", .5f);
		bind(R.id.viewer_speed_normal, ViewerCommand.Action.SPEED, "", 1);
		bind(R.id.viewer_initial, ViewerCommand.Action.MARKER, "initial", 0);
		bind(R.id.viewer_middle, ViewerCommand.Action.MARKER, "middle", 0);
		bind(R.id.viewer_final, ViewerCommand.Action.MARKER, "final", 0);
		bind(R.id.viewer_rotate_left, ViewerCommand.Action.ROTATE, "", -30);
		bind(R.id.viewer_rotate_right, ViewerCommand.Action.ROTATE, "", 30);
		bind(R.id.viewer_zoom_in, ViewerCommand.Action.ZOOM, "", .8f);
		bind(R.id.viewer_zoom_out, ViewerCommand.Action.ZOOM, "", 1.25f);
		bind(R.id.viewer_front, ViewerCommand.Action.VIEW, "front", 0);
		bind(R.id.viewer_side, ViewerCommand.Action.VIEW, "side", 0);
		bind(R.id.viewer_reset, ViewerCommand.Action.VIEW, "reset", 0);
		ScaleGestureDetector scale =
			new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
				@Override
				public boolean onScale(ScaleGestureDetector d) {
					send(ViewerCommand.Action.ZOOM, "", Math.max(.5f, Math.min(2f, 1f / d.getScaleFactor())), 0);
					return true;
				}
			});
		GestureDetector orbit = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(android.view.MotionEvent e) {
				return true;
			}

			@Override
			public boolean onScroll(android.view.MotionEvent a, android.view.MotionEvent b, float x, float y) {
				if (!scale.isInProgress())
					send(ViewerCommand.Action.ROTATE, "", Math.max(-180, Math.min(180, -x * .3f)),
						Math.max(-90, Math.min(90, y * .3f)));
				return true;
			}

			@Override
			public boolean onSingleTapUp(android.view.MotionEvent e) {
				surface.performClick();
				return true;
			}
		});
		surface.setOnTouchListener((v, event) -> {
			scale.onTouchEvent(event);
			orbit.onTouchEvent(event);
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
				v.getParent().requestDisallowInterceptTouchEvent(true);
			if (event.getActionMasked() == MotionEvent.ACTION_UP ||
				event.getActionMasked() == MotionEvent.ACTION_CANCEL)
				v.getParent().requestDisallowInterceptTouchEvent(false);
			return true;
		});
	}

	private void bind(int id, ViewerCommand.Action action, String target, float amount) {
		findViewById(id).setOnClickListener(v -> send(action, target, amount, 0));
	}

	private void send(ViewerCommand.Action action, String target, float amount, float secondary) {
		if (authorized() && controller != null)
			controller.command(new ViewerCommand(exerciseId, action, target, amount, secondary));
	}

	/**
	 * Puente futuro para órdenes tipadas: conserva la misma validación que los botones.
	 */
	public boolean acceptCommand(ViewerCommand command) {
		return authorized() && controller != null && controller.command(command);
	}

	/**
	 * Instantánea de presentación; útil para restaurar y comprobar los controles sin exponer JNI.
	 */
	public ViewerState viewerState() {
		return controller == null ? null : controller.state();
	}

	@Override
	public void render(ViewerState state) {
		controls.setVisibility(state.ready ? android.view.View.VISIBLE : android.view.View.GONE);
		loading.setVisibility(state.ready ? android.view.View.GONE : android.view.View.VISIBLE);
		((TextView) findViewById(R.id.viewer_play)).setText(state.playing ? "Pausar" : "Reproducir");
		if (state.ready)
			status.setText(Txt.t(R.string.an_offline) + " · " + (manifest == null ? "" : manifest.label() + " · ") +
				Txt.t(state.playing ? R.string.an_playing : R.string.an_paused) + " · " +
				String.format(Txt.locale(), "%.2f×", state.speed));
		findViewById(R.id.viewer_speed_quarter).setSelected(state.speed == .25f);
		findViewById(R.id.viewer_speed_half).setSelected(state.speed == .5f);
		findViewById(R.id.viewer_speed_normal).setSelected(state.speed == 1);
	}

	private void failure(String message) {
		closeRenderer();
		if (controller != null)
			controller.failure(message);
		progress.setVisibility(android.view.View.GONE);
		loading.setVisibility(android.view.View.VISIBLE);
		controls.setVisibility(android.view.View.GONE);
		loadMessage.setText(message);
		status.setText(Txt.t(writtenReferenceAvailable ? R.string.an_unavailable_written : R.string.an_unavailable_back));
	}

	private void closeRenderer() {
		if (renderer != null) {
			try {
				renderer.close();
			} catch (RuntimeException | LinkageError e) {
				Log.e("EnerGymViewer", "Error al liberar visor", e);
			}
			renderer = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!authorized())
			return;
		resumed = true;
		if (renderer != null)
			renderer.resume();
	}

	@Override
	protected void onPause() {
		com.energym.app.ui.effects.MotionPreferences.cancelButtons(findViewById(R.id.anatomy_root));
		resumed = false;
		if (renderer != null)
			renderer.pause();
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle out) {
		if (controller != null) {
			ViewerState s = controller.state();
			out.putString("exerciseId", exerciseId);
			out.putBoolean("playing", s.playing);
			out.putFloat("playhead", s.playhead);
			out.putFloat("speed", s.speed);
			out.putFloat("yaw", s.yaw);
			out.putFloat("pitch", s.pitch);
			out.putFloat("zoom", s.zoom);
		}
		super.onSaveInstanceState(out);
	}

	@Override
	public void finish() {
		setResult(contextCancelled ? RESULT_CANCELED : RESULT_OK, new Intent().putExtra(EXTRA_LEASE, lease));
		super.finish();
	}

	@Override
	protected void onDestroy() {
		destroyed = true;
		generation++;
		if (read != null)
			read.cancel(true);
		io.shutdownNow();
		main.removeCallbacksAndMessages(null);
		if (receiverRegistered) {
			unregisterReceiver(contextClose);
			receiverRegistered = false;
		}
		closeRenderer();
		if (controller != null)
			controller.close();
		if (counted) {
			liveActivities--;
			counted = false;
		}
		boolean reclaim = !isChangingConfigurations() && liveActivities == 0;
		String expectedProcess = getPackageName() + ":viewer";
		Context application = getApplicationContext();
		super.onDestroy();
		// El driver retiene memoria aunque se libere Filament. Solo el proceso privado puede reclamarse.
		// La recreación de configuración conserva el proceso; tampoco se corta otro visor aún vivo.
		if (reclaim)
			new Handler(Looper.getMainLooper()).postDelayed(() -> {
				if (liveActivities == 0 && expectedProcess.equals(processName(application))) {
					if (com.energym.app.BuildConfig.DEBUG) Log.i("EnerGymViewer", "Private viewer process reclaimed after final close");
					android.os.Process.killProcess(android.os.Process.myPid());
				}
			}, 200);
	}

	private static String processName(Context application) {
		if (Build.VERSION.SDK_INT >= 28)
			return Application.getProcessName();
		ActivityManager manager = (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
		java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
		if (processes != null)
			for (ActivityManager.RunningAppProcessInfo p : processes)
				if (p.pid == android.os.Process.myPid())
					return p.processName;
		return "";
	}
}
