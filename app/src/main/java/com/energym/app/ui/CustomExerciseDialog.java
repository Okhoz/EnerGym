package com.energym.app.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.catalog.CustomExercises;
import com.energym.app.domain.MetricType;
import com.energym.app.domain.RoutineRequest.Area;
import com.energym.app.domain.TrainingProfile.Resource;

import java.util.*;

/**
 * VISTA: formulario para crear un ejercicio propio (nombre, tipo, grupo muscular, material y notas).
 */
final class CustomExerciseDialog {
	private static final MetricType[] TYPES = {MetricType.STRENGTH, MetricType.ISOMETRIC, MetricType.ENDURANCE,
		MetricType.MOBILITY};
	private static final String[] TYPE_LABELS = {"Fuerza (peso × repeticiones)", "Isométrico (segundos)",
		"Cardio (minutos)", "Movilidad / estiramiento"};
	private static final Area[] AREAS = {Area.CHEST, Area.BACK, Area.LEGS, Area.GLUTES, Area.SHOULDERS, Area.ARMS,
		Area.CORE, Area.CARDIO, Area.MOBILITY};
	private static final Resource[] MATERIAL = {null, Resource.DUMBBELLS, Resource.BARBELL, Resource.MACHINE,
		Resource.BANDS, Resource.BENCH, Resource.MAT};
	private static final String[] MATERIAL_LABELS = {"Ninguno (peso corporal)", "Mancuernas", "Barra", "Máquina o polea",
		"Bandas elásticas", "Banco", "Esterilla"};

	private CustomExerciseDialog() {
	}

	static void show(Context context, CustomExercises store, java.util.function.Consumer<String> error) {
		float d = context.getResources().getDisplayMetrics().density;
		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), 0);
		EditText name = new EditText(context);
		name.setHint("Nombre (p. ej. Press en máquina convergente)");
		name.setSingleLine(true);
		name.setTag("custom_name");
		form.addView(label(context, "Nombre"));
		form.addView(name);
		Spinner type = spinner(context, TYPE_LABELS, "custom_type");
		form.addView(label(context, "Tipo"));
		form.addView(type);
		String[] areaLabels = new String[AREAS.length];
		for (int i = 0; i < AREAS.length; i++)
			areaLabels[i] = Character.toUpperCase(AREAS[i].label.charAt(0)) + AREAS[i].label.substring(1);
		Spinner area = spinner(context, areaLabels, "custom_area");
		form.addView(label(context, "Grupo muscular"));
		form.addView(area);
		Spinner material = spinner(context, MATERIAL_LABELS, "custom_material");
		form.addView(label(context, "Material"));
		form.addView(material);
		EditText notes = new EditText(context);
		notes.setHint("Notas de técnica (opcional)");
		notes.setTag("custom_notes");
		form.addView(label(context, "Notas"));
		form.addView(notes);
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Nuevo ejercicio")
			.setView(form).setNegativeButton("Cancelar", null)
			.setPositiveButton("Guardar ejercicio", (dialog, which) -> {
				Resource chosen = MATERIAL[material.getSelectedItemPosition()];
				String message = store.add(name.getText().toString(), TYPES[type.getSelectedItemPosition()],
					AREAS[area.getSelectedItemPosition()], chosen == null ? EnumSet.noneOf(Resource.class) :
						EnumSet.of(chosen), notes.getText().toString());
				if (!message.isEmpty())
					error.accept(message);
			}).show();
	}

	private static TextView label(Context context, String text) {
		TextView t = new TextView(context);
		t.setText(text);
		t.setTextAppearance(R.style.EnerGym_FieldCaption);
		t.setPadding(0, (int) (12 * context.getResources().getDisplayMetrics().density), 0, 0);
		return t;
	}

	private static Spinner spinner(Context context, String[] labels, String tag) {
		Spinner s = new Spinner(context);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.row_choice_text, labels);
		adapter.setDropDownViewResource(R.layout.row_choice_option);
		s.setAdapter(adapter);
		s.setTag(tag);
		return s;
	}
}
