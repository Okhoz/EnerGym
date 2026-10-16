package com.energym.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Burbuja de selección que se desliza como el agua: al cambiar de opción el borde delantero sale primero, el
 * trasero lo sigue más tarde (la burbuja se estira) y al llegar se recoge con un pequeño rebote. Solo dibuja;
 * quien la usa indica el rectángulo de cada opción.
 */
public final class LiquidIndicator extends View {
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF from = new RectF(), to = new RectF(), shape = new RectF();
	private final PathInterpolator lead = new PathInterpolator(0.3f, 0f, 0.1f, 1f),
		trail = new PathInterpolator(0.7f, 0f, 0.3f, 1f);
	private ValueAnimator animator;
	private float progress = 1, radius;
	private boolean placed;

	public LiquidIndicator(Context context) {
		super(context);
		setWillNotDraw(false);
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
	}

	/** No pide espacio propio: ocupa lo que mida su contenedor (que vuelve a medirla a su tamaño exacto). */
	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		setMeasuredDimension(resolveSize(0, widthSpec), resolveSize(0, heightSpec));
	}

	public void setColor(int color) {
		paint.setColor(color);
		invalidate();
	}

	/** Radio de las esquinas; por defecto, media altura (forma de píldora). */
	public void setRadius(float px) {
		radius = px;
		invalidate();
	}

	/** Coloca la burbuja sin animación (primer dibujo o sin movimiento reducido). */
	public void jumpTo(RectF target) {
		cancel();
		from.set(target);
		to.set(target);
		progress = 1;
		placed = true;
		invalidate();
	}

	/** Desliza la burbuja hasta {@code target}; {@code done} se ejecuta al llegar (o al momento sin animación). */
	public void slideTo(RectF target, boolean animate, Runnable done) {
		if (!placed || !animate || target.equals(to)) {
			jumpTo(target);
			if (done != null) done.run();
			return;
		}
		cancel();
		from.set(current());
		to.set(target);
		animator = ValueAnimator.ofFloat(0, 1);
		animator.setDuration(460);
		animator.addUpdateListener(a -> {
			progress = (float) a.getAnimatedValue();
			invalidate();
		});
		animator.addListener(new android.animation.AnimatorListenerAdapter() {
			private boolean cancelled;

			@Override
			public void onAnimationCancel(android.animation.Animator animation) {
				cancelled = true;
			}

			@Override
			public void onAnimationEnd(android.animation.Animator animation) {
				if (!cancelled && done != null) done.run();
			}
		});
		animator.start();
	}

	private void cancel() {
		if (animator != null) {
			ValueAnimator old = animator;
			animator = null;
			old.cancel();
		}
	}

	/** Forma visible ahora mismo: el borde que avanza va por delante del que se queda atrás. */
	private RectF current() {
		boolean right = to.centerX() >= from.centerX();
		float fast = lead.getInterpolation(Math.min(1, progress * 1.15f));
		float slow = trail.getInterpolation(Math.max(0, (progress - 0.12f) / 0.88f));
		float left = right ? lerp(from.left, to.left, slow) : lerp(from.left, to.left, fast);
		float rightEdge = right ? lerp(from.right, to.right, fast) : lerp(from.right, to.right, slow);
		// Mientras se estira, la gota adelgaza un poco y al llegar recupera su altura con un rebote suave.
		float stretch = (float) Math.sin(Math.PI * progress);
		float squash = stretch * 0.12f * to.height();
		float settle = progress > 0.85f ? (float) Math.sin((progress - 0.85f) / 0.15f * Math.PI) * 0.04f * to.height() : 0;
		float top = lerp(from.top, to.top, fast) + squash / 2 - settle;
		float bottom = lerp(from.bottom, to.bottom, fast) - squash / 2 + settle;
		shape.set(left, top, rightEdge, bottom);
		return shape;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (!placed)
			return;
		RectF r = current();
		float corner = radius > 0 ? Math.min(radius, r.height() / 2) : r.height() / 2;
		canvas.drawRoundRect(r, corner, corner, paint);
	}

	@Override
	protected void onDetachedFromWindow() {
		cancel();
		super.onDetachedFromWindow();
	}
}
