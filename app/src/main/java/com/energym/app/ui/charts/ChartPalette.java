package com.energym.app.ui.charts;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.core.content.ContextCompat;

import com.energym.app.R;

/**
 * Colores y pinceles compartidos por las gráficas: dorado para el dato, blanco para cifras y gris para ejes.
 */
final class ChartPalette {
	final int gold, white, secondary, track, protein, carbs, fat, over;
	final float density;

	ChartPalette(Context context) {
		gold = ContextCompat.getColor(context, R.color.gold);
		white = ContextCompat.getColor(context, R.color.white);
		secondary = ContextCompat.getColor(context, R.color.text_secondary);
		track = ContextCompat.getColor(context, R.color.chart_track);
		protein = ContextCompat.getColor(context, R.color.macro_protein);
		carbs = ContextCompat.getColor(context, R.color.macro_carbs);
		fat = ContextCompat.getColor(context, R.color.macro_fat);
		over = ContextCompat.getColor(context, R.color.chart_over);
		density = context.getResources().getDisplayMetrics().density;
	}

	float dp(float value) {
		return value * density;
	}

	Paint text(int color, float sp, boolean bold) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(color);
		p.setTextSize(sp * density);
		p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		p.setTextAlign(Paint.Align.CENTER);
		return p;
	}

	Paint stroke(int color, float widthDp) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(color);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(dp(widthDp));
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		return p;
	}

	Paint fill(int color) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(color);
		return p;
	}
}
