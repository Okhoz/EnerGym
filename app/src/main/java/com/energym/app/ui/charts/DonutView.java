package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Gráfica de anillo por partes (como el reparto por grupo muscular de FitNotes): cada parte con su color y, en el
 * centro, la parte mayor y su porcentaje.
 */
public final class DonutView extends View {
	private final ChartPalette palette;
	private final Paint arc, center, caption;
	private final RectF bounds = new RectF();
	private double[] values = new double[0];
	private int[] colors = new int[0];
	private String title = "", subtitle = "";

	public DonutView(Context context) {
		this(context, null);
	}

	public DonutView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		arc = palette.stroke(palette.gold, 22);
		arc.setStrokeCap(Paint.Cap.BUTT);
		center = palette.text(palette.white, 18, true);
		center.setTextAlign(Paint.Align.CENTER);
		caption = palette.text(palette.secondary, 13, false);
		caption.setTextAlign(Paint.Align.CENTER);
	}

	public DonutView set(double[] values, int[] colors, String title, String subtitle, String description) {
		this.values = values.clone();
		this.colors = colors.clone();
		this.title = title;
		this.subtitle = subtitle;
		setContentDescription(description);
		invalidate();
		return this;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int width = MeasureSpec.getSize(widthSpec);
		setMeasuredDimension(width, (int) palette.dp(220));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float size = Math.min(getWidth(), getHeight()) - palette.dp(28);
		float cx = getWidth() / 2f, cy = getHeight() / 2f;
		bounds.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
		double total = 0;
		for (double v : values)
			total += v;
		float start = -90;
		if (total <= 0) {
			arc.setColor(palette.track);
			canvas.drawArc(bounds, 0, 360, false, arc);
		} else
			for (int i = 0; i < values.length; i++) {
				float sweep = (float) (values[i] / total * 360);
				arc.setColor(colors[i % colors.length]);
				// Un hueco fino entre partes para distinguirlas.
				canvas.drawArc(bounds, start, Math.max(0.5f, sweep - (values.length > 1 ? 1.5f : 0)), false, arc);
				start += sweep;
			}
		canvas.drawText(title, cx, cy, center);
		canvas.drawText(subtitle, cx, cy + palette.dp(20), caption);
	}
}
