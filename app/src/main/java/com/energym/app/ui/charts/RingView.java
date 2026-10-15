package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Anillo de progreso: valor consumido frente a objetivo, con cifra central y etiqueta inferior.
 * Al superar el objetivo el arco cambia de color y se anuncia en la descripción accesible.
 */
public final class RingView extends View {
	private final ChartPalette palette;
	private final RectF bounds = new RectF();
	private Paint track, arc, value, label, caption;
	private double current, target;
	private String center = "", unit = "", title = "";
	private int color;

	public RingView(Context context) {
		this(context, null);
	}

	public RingView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		color = palette.gold;
		build(10);
	}

	private void build(float width) {
		track = palette.stroke(palette.track, width);
		arc = palette.stroke(color, width);
		value = palette.text(palette.white, width > 9 ? 22 : 15, true);
		label = palette.text(palette.secondary, 11, false);
		caption = palette.text(palette.white, 13, true);
	}

	/**
	 * @param strokeDp grosor del anillo; los anillos grandes usan 12dp y los de macros 7dp.
	 */
	public RingView set(String title, double current, double target, String unit, int color, float strokeDp) {
		this.title = title;
		this.current = current;
		this.target = target;
		this.unit = unit;
		this.color = color;
		this.center = Long.toString(Math.round(current));
		build(strokeDp);
		setContentDescription(title + ": " + Math.round(current) + " de " + Math.round(target) + " " + unit +
			(target > 0 && current > target ? ", por encima del objetivo" : ""));
		invalidate();
		return this;
	}

	public int gold() {
		return palette.gold;
	}

	public int protein() {
		return palette.protein;
	}

	public int carbs() {
		return palette.carbs;
	}

	public int fat() {
		return palette.fat;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int width = MeasureSpec.getSize(widthSpec);
		int size = Math.min(width, (int) palette.dp(arc.getStrokeWidth() > palette.dp(9) ? 170 : 104));
		setMeasuredDimension(width, (int) (size + palette.dp(26)));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float stroke = arc.getStrokeWidth();
		float size = Math.min(getWidth(), getHeight() - palette.dp(26)) - stroke;
		float cx = getWidth() / 2f, cy = stroke / 2 + size / 2;
		bounds.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
		canvas.drawArc(bounds, 0, 360, false, track);
		double ratio = target <= 0 ? 0 : current / target;
		arc.setColor(ratio > 1.05 ? palette.over : color);
		if (ratio > 0)
			canvas.drawArc(bounds, -90, (float) (Math.min(1, ratio) * 360), false, arc);
		float valueY = cy - (value.descent() + value.ascent()) / 2 - palette.dp(6);
		canvas.drawText(center, cx, valueY, value);
		canvas.drawText(target > 0 ? "de " + Math.round(target) + " " + unit : unit, cx,
			valueY + palette.dp(16), label);
		canvas.drawText(title, cx, getHeight() - palette.dp(6), caption);
	}
}
