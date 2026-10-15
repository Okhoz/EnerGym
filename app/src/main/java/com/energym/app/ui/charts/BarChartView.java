package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Barras verticales con etiqueta por columna y línea discontinua opcional de objetivo.
 * La última barra (periodo actual) se resalta en dorado; las anteriores en dorado atenuado.
 */
public final class BarChartView extends View {
	private final ChartPalette palette;
	private final Paint bar, current, axis, label, valueText, targetLine;
	private final RectF rect = new RectF();
	private double[] values = new double[0];
	private String[] labels = new String[0];
	private double target;
	private boolean highlightLast = true;

	public BarChartView(Context context) {
		this(context, null);
	}

	public BarChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		bar = palette.fill(palette.gold);
		bar.setAlpha(110);
		current = palette.fill(palette.gold);
		axis = palette.stroke(palette.track, 1);
		label = palette.text(palette.secondary, 11, false);
		valueText = palette.text(palette.white, 11, true);
		targetLine = palette.stroke(palette.white, 1.2f);
		targetLine.setAlpha(150);
		targetLine.setPathEffect(new DashPathEffect(new float[]{palette.dp(4), palette.dp(4)}, 0));
	}

	public BarChartView set(double[] values, String[] labels, double target, String description) {
		this.values = values.clone();
		this.labels = labels.clone();
		this.target = target;
		setContentDescription(description);
		invalidate();
		return this;
	}

	public BarChartView highlightLast(boolean value) {
		highlightLast = value;
		invalidate();
		return this;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) palette.dp(180));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (values.length == 0)
			return;
		float top = palette.dp(20), bottom = getHeight() - palette.dp(24);
		double max = target;
		for (double v : values)
			max = Math.max(max, v);
		if (max <= 0)
			max = 1;
		canvas.drawLine(0, bottom, getWidth(), bottom, axis);
		float slot = getWidth() / (float) values.length;
		float width = Math.min(palette.dp(28), slot * 0.56f);
		for (int i = 0; i < values.length; i++) {
			float cx = slot * i + slot / 2;
			float height = (float) (values[i] / max) * (bottom - top);
			rect.set(cx - width / 2, bottom - Math.max(height, values[i] > 0 ? palette.dp(3) : 0), cx + width / 2,
				bottom);
			float radius = palette.dp(5);
			canvas.drawRoundRect(rect, radius, radius, highlightLast && i == values.length - 1 ? current : bar);
			if (values[i] > 0)
				canvas.drawText(compact(values[i]), cx, rect.top - palette.dp(5), valueText);
			if (i < labels.length)
				canvas.drawText(labels[i], cx, getHeight() - palette.dp(6), label);
		}
		if (target > 0) {
			float y = bottom - (float) (target / max) * (bottom - top);
			canvas.drawLine(0, y, getWidth(), y, targetLine);
		}
	}

	static String compact(double v) {
		if (v >= 10000)
			return String.format(Locale.ROOT, "%.0fk", v / 1000);
		if (v >= 1000)
			return String.format(Locale.ROOT, "%.1fk", v / 1000).replace('.', ',');
		if (v == Math.rint(v) || v >= 100)
			return Long.toString(Math.round(v));
		return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
	}
}
