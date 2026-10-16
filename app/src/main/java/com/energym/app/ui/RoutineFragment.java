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
		heading("REVISA ANTES DE EMPEZAR", app.routines.preview == null ? "Tu rutina" : "Tu propuesta",
			r == null ? "Crea una propuesta desde Entreno." : r.getItems().size() + " ejercicios · " +
				r.getEstimatedMinutes() + " min");
		if (r == null)
			return;
		card("Para quién es", app.profileSummary(r.getProfile()));
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
		text(rows, r.getExplanation(), R.style.EnerGym_FieldCaption, 12);
		if (app.routines.preview != null) {
			Routine proposal = app.routines.preview;
			// Una propuesta (de la IA o automática) se puede cambiar antes de guardarla, o hacerla ya.
			action(rows, "✏️ Editar antes de guardar", !app.routines.busy && !app.trainingMutation,
				() -> app.editProposal(proposal)).setTag("proposal_edit");
			action(rows, "Rechazar propuesta", !app.routines.busy, () -> {
				app.routines.reject();
				app.back();
			});
			action(rows, "Aceptar y empezar ya", app.routines.canAccept() && !app.trainingMutation,
				() -> app.acceptAndStart(proposal, true)).setTag("proposal_start");
			primary(action(rows, "Aceptar propuesta", app.routines.canAccept(), app.routines::accept));
		} else {
			Routine accepted = app.routines.accepted;
			action(rows, "✏️ Editar rutina", !app.routines.busy && !app.trainingMutation,
				() -> app.editRoutine(accepted)).setTag("routine_edit");
			primary(action(rows, "Empezar entreno", !app.sessions.busy, app::startSession));
		}
	}
}

