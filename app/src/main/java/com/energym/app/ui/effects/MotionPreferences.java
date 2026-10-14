package com.energym.app.ui.effects;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;

/**
 * Consulta las preferencias en cada interacción, sin retener Activity ni observadores.
 */
public final class MotionPreferences {
	private MotionPreferences() {
	}

	public static boolean enabled(Context context) {
		PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		return GlassPerformancePolicy.allowMotion(ValueAnimator.areAnimatorsEnabled(), power != null && power.isPowerSaveMode());
	}

	/**
	 * La pausa cancela solo resortes de botones; no toca gestos ni el SurfaceView nativo.
	 */
	public static void cancelButtons(View view) {
		if (view instanceof GlassButton)
			((GlassButton) view).resetMotion();
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++)
				cancelButtons(group.getChildAt(i));
		}
	}
}
