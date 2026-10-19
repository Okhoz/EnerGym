package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.energym.app.R;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.RoutineRequest;

import java.util.*;
import java.util.function.Consumer;

/**
 * VISTA: diálogo para elegir un ejercicio con buscador, como el «añadir ejercicio» de FitNotes. Muestra los
 * ejercicios agrupados por grupo muscular y filtra al escribir.
 */
final class ExercisePicker {
	private ExercisePicker() {
	}

	static void show(Context context, String title, List<Exercise> options, Consumer<Exercise> pick) {
		float density = context.getResources().getDisplayMetrics().density;
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (20 * density);
		root.setPadding(pad, (int) (8 * density), pad, 0);
		EditText search = new EditText(context);
		search.setHint(Txt.t(R.string.ep_search_hint));
		search.setSingleLine(true);
		search.setTag("picker_search");
		root.addView(search);
		LinearLayout list = new LinearLayout(context);
		list.setOrientation(LinearLayout.VERTICAL);
		ScrollView scroll = new ScrollView(context);
		scroll.addView(list);
		root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (420 * density)));
		androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
			.setTitle(title).setView(root).setNegativeButton(Txt.t(R.string.common_cancel), null).create();
		ExerciseSearch searcher = new ExerciseSearch(options);
		Runnable refresh = () -> {
			list.removeAllViews();
			String q = search.getText().toString().trim();
			List<Exercise> found = q.isEmpty() ? options : searcher.find(q, ExerciseSearch.Filters.all());
			if (found.isEmpty()) {
				list.addView(label(context, Txt.t(R.string.ep_none), false));
				return;
			}
			// Sin búsqueda, por grupo muscular; al buscar, lista única por relevancia (lo más parecido primero).
			Map<String, List<Exercise>> groups = new LinkedHashMap<>();
			// Títulos de grupo en el idioma de la app (el nombre interno del grupo sigue en español).
			String other = Txt.t(R.string.picker_other);
			if (!q.isEmpty())
				groups.put(Txt.t(R.string.picker_results), new ArrayList<>(found.subList(0, Math.min(40, found.size()))));
			else {
				for (RoutineRequest.Area a : RoutineRequest.Area.values())
					groups.put(a.display(), new ArrayList<>());
				groups.put(other, new ArrayList<>());
				for (Exercise e : found) {
					Set<RoutineRequest.Area> areas = RoutineRequest.areas(e);
					groups.get(areas.isEmpty() ? other : areas.iterator().next().display()).add(e);
				}
			}
			for (Map.Entry<String, List<Exercise>> g : groups.entrySet()) {
				if (g.getValue().isEmpty())
					continue;
				list.addView(label(context, g.getKey().toUpperCase(Txt.locale()), true));
				for (Exercise e : g.getValue()) {
					TextView row = label(context, e.getName(), false);
					row.setMinHeight((int) (48 * density));
					row.setGravity(android.view.Gravity.CENTER_VERTICAL);
					row.setContentDescription(Txt.t(R.string.man_add_named, e.getName()));
					row.setTag("picker_" + e.getId());
					row.setOnClickListener(v -> {
						dialog.dismiss();
						pick.accept(e);
					});
					android.util.TypedValue ripple = new android.util.TypedValue();
					context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
					row.setBackgroundResource(ripple.resourceId);
					list.addView(row);
				}
			}
		};
		search.addTextChangedListener(new android.text.TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void onTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void afterTextChanged(android.text.Editable s) {
				refresh.run();
			}
		});
		refresh.run();
		dialog.show();
	}

	private static TextView label(Context context, String text, boolean header) {
		TextView t = new TextView(context);
		t.setText(text);
		t.setTextAppearance(header ? R.style.EnerGym_FieldCaption : R.style.EnerGym_Body);
		if (header) {
			t.setTextColor(ContextCompat.getColor(context, R.color.gold));
			t.setPadding(0, (int) (14 * context.getResources().getDisplayMetrics().density), 0,
				(int) (4 * context.getResources().getDisplayMetrics().density));
		}
		t.setImportantForAccessibility(header ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
		return t;
	}
}
