package com.energym.app.ui.effects;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.content.ContextCompat;

/**
 * Solo desenfoca una decoración estática acotada: nunca captura ni procesa vistas de usuario.
 */
public final class GlassBackdropController implements AutoCloseable {
	private final Activity activity;
	private final View decoration;
	private final boolean nativeViewer;
	private final Handler main = new Handler(Looper.getMainLooper());
	private final GlassPerformancePolicy.FrameBudget budget = new GlassPerformancePolicy.FrameBudget();
	private RenderEffect cachedEffect;
	private boolean resumed, listening, observing, memoryFallback, closed;
	private final Window.OnFrameMetricsAvailableListener frames = (window, metrics, dropped) -> {
		if (!resumed || !listening)
			return;
		if (budget.record(metrics.getMetric(FrameMetrics.TOTAL_DURATION))) {
			memoryFallback = true;
			disableBlur();
		}
		if (budget.complete())
			stopFrames();
	};
	private final BroadcastReceiver powerChanges = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			refresh();
		}
	};
	private final ContentObserver animationChanges = new ContentObserver(main) {
		@Override
		public void onChange(boolean selfChange) {
			refresh();
		}
	};

	public GlassBackdropController(Activity activity, View decoration, boolean nativeViewer) {
		this.activity = activity;
		this.decoration = decoration;
		this.nativeViewer = nativeViewer;
		// Dimensiones en píxeles reales, independientes de la densidad: como máximo 256 × 512.
		ViewGroup.LayoutParams params = decoration.getLayoutParams();
		params.width = GlassPerformancePolicy.MAX_WIDTH_PX;
		params.height = 192;
		decoration.setLayoutParams(params);
	}

	public void resume() {
		if (closed)
			return;
		resumed = true;
		if (!observing) {
			ContextCompat.registerReceiver(activity, powerChanges,
				new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
			activity.getContentResolver()
				.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false,
					animationChanges);
			observing = true;
		}
		// La vista ya está adjunta cuando se evalúa la aceleración por hardware.
		decoration.post(refreshAfterAttach);
	}

	private final Runnable refreshAfterAttach = this::refresh;

	private void refresh() {
		if (!resumed || closed)
			return;
		ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
		PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
		ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
		if (manager != null)
			manager.getMemoryInfo(memory);
		boolean allowed = manager != null &&
			GlassPerformancePolicy.allowBlur(Build.VERSION.SDK_INT, decoration.isHardwareAccelerated(),
				manager.isLowRamDevice(), memory.lowMemory || memoryFallback, manager.getMemoryClass(),
				power != null && power.isPowerSaveMode(), ValueAnimator.areAnimatorsEnabled(), nativeViewer);
		if (!allowed) {
			disableBlur();
			return;
		}
		if (Build.VERSION.SDK_INT >= 31) {
			if (cachedEffect == null)
				cachedEffect = RenderEffect.createBlurEffect(GlassPerformancePolicy.BLUR_RADIUS_PX,
					GlassPerformancePolicy.BLUR_RADIUS_PX, Shader.TileMode.CLAMP);
			decoration.setRenderEffect(cachedEffect);
		}
		if (!listening && !budget.complete()) {
			activity.getWindow().addOnFrameMetricsAvailableListener(frames, main);
			listening = true;
		}
	}

	public void lowMemory() {
		memoryFallback = true;
		disableBlur();
		cachedEffect = null;
	}

	private void disableBlur() {
		if (Build.VERSION.SDK_INT >= 31)
			decoration.setRenderEffect(null);
		stopFrames();
	}

	private void stopFrames() {
		if (listening)
			activity.getWindow().removeOnFrameMetricsAvailableListener(frames);
		listening = false;
	}

	public void pause() {
		resumed = false;
		decoration.removeCallbacks(refreshAfterAttach);
		disableBlur();
		if (observing) {
			activity.unregisterReceiver(powerChanges);
			activity.getContentResolver().unregisterContentObserver(animationChanges);
			observing = false;
		}
	}

	@Override
	public void close() {
		pause();
		cachedEffect = null;
		closed = true;
	}
}
