package com.energym.app.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.domain.Plates;
import com.energym.app.domain.SetLog;

import java.util.*;

/** Calculadora de discos para el peso escrito y tabla de porcentajes de tu 1RM estimado. */
final class PlateDialog {
	private PlateDialog() {
	}

	static void show(Context c, double kg, double oneRepMaxKg, boolean lb) {
		LinearLayout box = new LinearLayout(c);
		box.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (24 * c.getResources().getDisplayMetrics().density);
		box.setPadding(pad, pad / 2, pad, 0);
		String unit = Plates.unit(lb);
		double bar = lb ? Plates.LB_BAR : Plates.KG_BAR;
		double target = Plates.toUnit(kg, lb);
		StringBuilder s = new StringBuilder();
		if (target <= 0)
			s.append("Escribe un peso para ver qué discos poner.");
		else {
			Plates.Load load = Plates.load(target, bar, lb ? Plates.LB_PLATES : Plates.KG_PLATES);
			s.append("Barra de ").append(SetLog.format(bar)).append(' ').append(unit).append(" + en cada lado:\n");
			if (load.perSide.isEmpty())
				s.append("  sin discos");
			else {
				// «2 × 20» en vez de repetir el mismo disco.
				Map<Double, Integer> count = new LinkedHashMap<>();
				for (double p : load.perSide)
					count.merge(p, 1, Integer::sum);
				List<String> parts = new ArrayList<>();
				for (Map.Entry<Double, Integer> e : count.entrySet())
					parts.add((e.getValue() > 1 ? e.getValue() + " × " : "") + SetLog.format(e.getKey()));
				s.append("  ").append(String.join("  +  ", parts)).append(' ').append(unit);
			}
			if (Math.abs(load.total - target) > 0.001)
				s.append("\n\nCon estos discos lo más cercano es ").append(SetLog.format(load.total)).append(' ').append(unit)
					.append('.');
		}
		add(box, c, "🏋️ Discos para " + (target > 0 ? SetLog.format(target) + " " + unit : "tu serie"), R.style.EnerGym_CardTitle);
		add(box, c, s.toString(), R.style.EnerGym_Body);
		if (oneRepMaxKg > 0) {
			add(box, c, "\n📈 Tu 1RM estimado: " + Plates.text(oneRepMaxKg, lb), R.style.EnerGym_CardTitle);
			StringBuilder table = new StringBuilder();
			for (int p : Plates.PERCENTS)
				table.append(String.format(Locale.ROOT, "%3d %%   %s %s   ~%d rep%s\n", p,
					SetLog.format(Plates.atPercent(oneRepMaxKg, p, lb)), unit, Plates.repsAt(p), Plates.repsAt(p) == 1 ? "" : "s"));
			TextView t = add(box, c, table.toString().trim(), R.style.EnerGym_Body);
			t.setTypeface(android.graphics.Typeface.MONOSPACE);
		}
		android.widget.ScrollView scroll = new android.widget.ScrollView(c);
		scroll.addView(box);
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(c).setView(scroll)
			.setPositiveButton("Cerrar", null).show();
	}

	private static TextView add(LinearLayout box, Context c, String text, int style) {
		TextView t = new TextView(c);
		t.setTextAppearance(style);
		t.setText(text);
		t.setPadding(0, 0, 0, (int) (8 * c.getResources().getDisplayMetrics().density));
		box.addView(t);
		return t;
	}
}
