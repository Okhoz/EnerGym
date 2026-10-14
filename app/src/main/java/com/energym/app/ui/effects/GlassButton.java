package com.energym.app.ui.effects;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.button.MaterialButton;

/** Botón Material con respuesta física; conserva click, accesibilidad y cancelación de gestos. */
public final class GlassButton extends MaterialButton {
	private SpringAnimation scaleXSpring, scaleYSpring;
	public GlassButton(Context context) { this(context, null); }
	public GlassButton(Context context, AttributeSet attrs) { this(context, attrs, com.google.android.material.R.attr.materialButtonStyle); }
	public GlassButton(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }

	@Override public void setPressed(boolean pressed) {
		boolean changed = pressed != isPressed();
		super.setPressed(pressed);
		if (!changed) return;
		if (!isEnabled() || !isAttachedToWindow() || !MotionPreferences.enabled(getContext())) {
			resetMotion();
			return;
		}
		if (scaleXSpring == null) {
			scaleXSpring = spring(DynamicAnimation.SCALE_X);
			scaleYSpring = spring(DynamicAnimation.SCALE_Y);
		}
		float target = pressed ? .975f : 1f;
		scaleXSpring.animateToFinalPosition(target);
		scaleYSpring.animateToFinalPosition(target);
	}

	private SpringAnimation spring(DynamicAnimation.ViewProperty property) {
		return new SpringAnimation(this, property).setSpring(new SpringForce(1f)
				.setDampingRatio(.82f).setStiffness(SpringForce.STIFFNESS_MEDIUM));
	}

	@Override public boolean performClick() {
		if (isEnabled()) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
		return super.performClick();
	}

	public void resetMotion() {
		if (scaleXSpring != null) { scaleXSpring.cancel(); scaleYSpring.cancel(); }
		setScaleX(1f);
		setScaleY(1f);
	}

	@Override protected void onDetachedFromWindow() {
		resetMotion();
		super.onDetachedFromWindow();
	}
	@Override public void onWindowFocusChanged(boolean focused) {
		super.onWindowFocusChanged(focused);
		if (!focused) resetMotion();
	}
}
