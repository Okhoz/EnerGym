package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Línea de evolución con área degradada, puntos y valores mínimo/máximo. Útil para peso y volumen.
 */
public final class LineChartView extends View {
	private final ChartPalette palette;
	private final Paint line, dot, area, grid, label, last;
	private final Path path = new Path(), fill = new Path();
	private double[] values = new double[0];
	private String[] labels = new String[0];
	private String unit = "";

	public LineChartView(Context context) {
		this(context, null);
	}

	public LineChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		line = palette.stroke(palette.gold, 2.5f);
		dot = palette.fill(palette.gold);
		area = palette.fill(palette.gold);
		grid = palette.stroke(palette.track, 1);
		label = palette.text(palette.secondary, 11, false);
		last = palette.text(palette.white, 12, true);
	}

	public LineChartView set(double[] values, String[] labels, String unit, String description) {
		this.values = values.clone();
		this.labels = labels.clone();
		this.unit = unit;
		setContentDescription(description);
		invalidate();
		return this;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) palette.dp(170));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		area.setShader(new LinearGradient(0, 0, 0, h, Color.argb(90, Color.red(palette.gold),
			Color.green(palette.gold), Color.blue(palette.gold)), Color.TRANSPARENT, Shader.TileMode.CLAMP));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (values.length == 0)
			return;
		float left = palette.dp(8), right = getWidth() - palette.dp(8);
		float top = palette.dp(22), bottom = getHeight() - palette.dp(24);
		double min = values[0], max = values[0];
		for (double v : values) {
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		double pad = Math.max((max - min) * 0.15, Math.abs(max) * 0.02 + 0.5);
		min -= pad;
		max += pad;
		for (int i = 0; i < 3; i++) {
			float y = top + (bottom - top) * i / 2f;
			canvas.drawLine(left, y, right, y, grid);
		}
		path.reset();
		fill.reset();
		float step = values.length == 1 ? 0 : (right - left) / (values.length - 1);
		float x = 0, y = 0;
		for (int i = 0; i < values.length; i++) {
			x = values.length == 1 ? (left + right) / 2 : left + step * i;
			y = (float) (bottom - (values[i] - min) / (max - min) * (bottom - top));
			if (i == 0) {
				path.moveTo(x, y);
				fill.moveTo(x, bottom);
				fill.lineTo(x, y);
			} else {
				path.lineTo(x, y);
				fill.lineTo(x, y);
			}
		}
		fill.lineTo(x, bottom);
		fill.close();
		canvas.drawPath(fill, area);
		canvas.drawPath(path, line);
		for (int i = 0; i < values.length; i++) {
			float px = values.length == 1 ? (left + right) / 2 : left + step * i;
			float py = (float) (bottom - (values[i] - min) / (max - min) * (bottom - top));
			canvas.drawCircle(px, py, palette.dp(i == values.length - 1 ? 5 : 3), dot);
		}
		Paint end = last;
		end.setTextAlign(x > getWidth() - palette.dp(40) ? Paint.Align.RIGHT : Paint.Align.CENTER);
		canvas.drawText(BarChartView.compact(values[values.length - 1]) + " " + unit, x, y - palette.dp(10), end);
		if (labels.length > 0) {
			label.setTextAlign(Paint.Align.LEFT);
			canvas.drawText(labels[0], left, getHeight() - palette.dp(6), label);
			label.setTextAlign(Paint.Align.RIGHT);
			canvas.drawText(labels[labels.length - 1], right, getHeight() - palette.dp(6), label);
		}
	}
}
