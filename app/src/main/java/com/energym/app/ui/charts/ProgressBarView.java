package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Barra horizontal de avance hacia un objetivo, con porcentaje a la derecha. Dorada en curso, verde al cumplirlo.
 */
public final class ProgressBarView extends View {
	private final ChartPalette palette;
	private final Paint track, fill, label;
	private final RectF rect = new RectF();
	private double ratio;

	public ProgressBarView(Context context) {
		this(context, null);
	}

	public ProgressBarView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		track = palette.fill(palette.track);
		fill = palette.fill(palette.gold);
		label = palette.text(palette.white, 13, true);
		label.setTextAlign(Paint.Align.RIGHT);
	}

	public ProgressBarView set(double ratio, String description) {
		this.ratio = Math.max(0, Math.min(1, ratio));
		fill.setColor(this.ratio >= 1 ? palette.carbs : palette.gold);
		setContentDescription(description + ": " + Math.round(this.ratio * 100) + " %");
		invalidate();
		return this;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) palette.dp(22));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float textWidth = palette.dp(48);
		float barHeight = palette.dp(10), top = (getHeight() - barHeight) / 2;
		float right = getWidth() - textWidth;
		float radius = barHeight / 2;
		rect.set(0, top, right, top + barHeight);
		canvas.drawRoundRect(rect, radius, radius, track);
		if (ratio > 0) {
			rect.set(0, top, Math.max(barHeight, (float) (right * ratio)), top + barHeight);
			canvas.drawRoundRect(rect, radius, radius, fill);
		}
		canvas.drawText(Math.round(ratio * 100) + " %", getWidth(), getHeight() / 2f - (label.descent() + label.ascent()) / 2,
			label);
	}
}
