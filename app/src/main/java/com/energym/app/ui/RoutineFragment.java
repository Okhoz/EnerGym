package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.widget.LinearLayout;

import java.util.*;

/**
 * VISTA de propuesta: aceptar/rechazar/sustituir se coordinan exclusivamente en RoutineController.
 */
public final class RoutineFragment extends ScreenFragment {
	public RoutineFragment() {
		super(R.layout.fragment_routine);
	}

	protected void content() {
		Routine r = app.routines.preview != null ? app.routines.preview : app.routines.accepted;
		heading("REVISA ANTES DE EMPEZAR", app.routines.preview == null ? "Rutina aceptada" : "Tu propuesta",
			r == null ? "Crea una propuesta desde Entrenamiento." : r.getExplanation());
		if (r == null)
			return;
		card("Resumen", r.getItems().size() + " ejercicios · " + r.getEstimatedMinutes() + " min estimados\n" +
			app.profileSummary(r.getProfile()));
		int index = 0;
		for (RoutineItem item : r.getItems()) {
			final int i = index++;
			Exercise e = item.getExercise();
			LinearLayout row = card(index + ". " + e.getName(),
				AppController.label(e.getMetricType().name()) + " · " + item.getEstimatedMinutes() + " min\n" +
					AppController.prescription(e.getMetricType(), item.getPrescription()) + "\n\n" +
					item.getJustification());
			action(row, "Ver técnica: " + e.getName(), () -> app.openExercise(e));
			if (app.routines.preview != null)
				action(row, "Sustituir: " + e.getName(), !app.routines.busy, () -> {
					List<Exercise> alternatives = app.routines.alternatives(i);
					if (alternatives.isEmpty()) {
						app.error("No hay alternativas compatibles sin repetir ejercicios.");
						return;
					}
					String[] names = new String[alternatives.size()];
					for (int j = 0; j < names.length; j++)
						names[j] = alternatives.get(j).getName();
					new MaterialAlertDialogBuilder(requireContext()).setTitle("Sustituciones compatibles")
						.setItems(names, (dialog, which) -> app.routines.substitute(i, alternatives.get(which).getId()))
						.setNegativeButton("Cancelar", null).show();
				});
		}
		if (app.routines.preview != null) {
			action(rows, "Aceptar propuesta", app.routines.canAccept(), app.routines::accept);
			action(rows, "Rechazar propuesta", !app.routines.busy, () -> {
				app.routines.reject();
				app.back();
			});
		} else
			action(rows, "Empezar sesión", !app.sessions.busy, app::startSession);
	}
}

