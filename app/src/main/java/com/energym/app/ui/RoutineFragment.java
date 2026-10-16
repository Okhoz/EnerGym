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
		heading("", app.routines.preview == null ? "Tu rutina" : "Tu propuesta",
			r == null ? "Crea una propuesta desde Entreno." : r.getTitle() + " · " + r.getItems().size() +
				" ejercicios · " + r.getEstimatedMinutes() + " min");
		if (r == null)
			return;
		// Lista compacta: cada ejercicio en una fila; técnica y sustituir al tocarla.
		LinearLayout list = card("Ejercicios", "Toca uno para ver su técnica" +
			(app.routines.preview != null ? " o cambiarlo." : "."));
		int index = 0;
		for (RoutineItem item : r.getItems()) {
			final int i = index++;
			Exercise e = item.getExercise();
			listRow(list, index + ". " + e.getName(), PlanFragment.target(item), "›", true, () -> exerciseOptions(i, e))
				.setTag("proposal_item_" + i);
		}
		if (app.routines.preview != null) {
			Routine proposal = app.routines.preview;
			// Una propuesta (de la IA o automática) se hace ya, se guarda para después, se edita o se descarta.
			com.google.android.material.button.MaterialButton start = action(rows, "Empezar ya",
				app.routines.canAccept() && !app.trainingMutation, () -> app.acceptAndStart(proposal, true));
			start.setTag("proposal_start");
			primary(start);
			action(rows, "Guardar rutina", app.routines.canAccept(), app.routines::accept).setTag("proposal_accept");
			action(rows, "✏️ Editar antes de guardar", !app.routines.busy && !app.trainingMutation,
				() -> app.editProposal(proposal)).setTag("proposal_edit");
			action(rows, "Descartar", !app.routines.busy, () -> {
				app.routines.reject();
				app.back();
			}).setTag("proposal_reject");
		} else {
			Routine accepted = app.routines.accepted;
			primary(action(rows, "Empezar entreno", !app.sessions.busy, app::startSession));
			action(rows, "✏️ Editar rutina", !app.routines.busy && !app.trainingMutation,
				() -> app.editRoutine(accepted)).setTag("routine_edit");
			action(rows, "Eliminar rutina", !app.routines.busy && !app.trainingMutation, () -> {
				long scope = app.accountRevision();
				new MaterialAlertDialogBuilder(requireContext()).setTitle("¿Eliminar esta rutina?")
					.setMessage("Las sesiones que ya has registrado se conservan.")
					.setNegativeButton("Cancelar", null)
					.setPositiveButton("Eliminar", (d, w) -> {
						if (scope == app.accountRevision()) {
							app.deleteRoutine(accepted);
							app.back();
						}
					}).show();
			}).setTag("routine_delete");
		}
		text(rows, app.profileSummary(r.getProfile()).replace('\n', ' ') + "\n" + r.getExplanation(),
			R.style.EnerGym_FieldCaption, 24);
	}

	private void exerciseOptions(int i, Exercise e) {
		boolean proposal = app.routines.preview != null;
		String[] choices = proposal ? new String[]{"Ver técnica", "Cambiar por otro"} : new String[]{"Ver técnica"};
		new MaterialAlertDialogBuilder(requireContext()).setTitle(e.getName()).setItems(choices, (d, which) -> {
			if (which == 0) {
				app.openExercise(e);
				return;
			}
			List<Exercise> alternatives = app.routines.alternatives(i);
			if (alternatives.isEmpty()) {
				app.error("No hay alternativas compatibles sin repetir ejercicios.");
				return;
			}
			String[] names = new String[alternatives.size()];
			for (int j = 0; j < names.length; j++)
				names[j] = alternatives.get(j).getName();
			new MaterialAlertDialogBuilder(requireContext()).setTitle("Cambiar por")
				.setItems(names, (dialog, w) -> app.routines.substitute(i, alternatives.get(w).getId()))
				.setNegativeButton("Cancelar", null).show();
		}).setNegativeButton("Cancelar", null).show();
	}
}

