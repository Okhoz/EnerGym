package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
		heading("", Txt.t(app.routines.preview == null ? R.string.rt_routine : R.string.rt_proposal),
			r == null ? Txt.t(R.string.rt_create_from_training) : r.getTitle() + " · " +
				Txt.n(R.plurals.tr_summary, r.getItems().size(), r.getItems().size(), r.getEstimatedMinutes()));
		if (r == null)
			return;
		// Lista compacta: cada ejercicio en una fila; técnica y sustituir al tocarla.
		LinearLayout list = card(Txt.t(R.string.tr_tab_exercises),
			Txt.t(app.routines.preview != null ? R.string.rt_tap_or_swap : R.string.rt_tap));
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
			com.google.android.material.button.MaterialButton start = action(rows, Txt.t(R.string.rt_start_now),
				app.routines.canAccept() && !app.trainingMutation, () -> app.acceptAndStart(proposal, true));
			start.setTag("proposal_start");
			primary(start);
			action(rows, Txt.t(R.string.rt_save), app.routines.canAccept(), app.routines::accept).setTag("proposal_accept");
			action(rows, Txt.t(R.string.rt_edit_before_saving), !app.routines.busy && !app.trainingMutation,
				() -> app.editProposal(proposal)).setTag("proposal_edit");
			action(rows, Txt.t(R.string.ses_discard_confirm), !app.routines.busy, () -> {
				app.routines.reject();
				app.back();
			}).setTag("proposal_reject");
		} else {
			Routine accepted = app.routines.accepted;
			primary(action(rows, Txt.t(R.string.tr_start_workout), !app.sessions.busy, app::startSession));
			action(rows, Txt.t(R.string.rt_edit), !app.routines.busy && !app.trainingMutation,
				() -> app.editRoutine(accepted)).setTag("routine_edit");
			action(rows, Txt.t(R.string.tr_delete_routine), !app.routines.busy && !app.trainingMutation, () -> {
				long scope = app.accountRevision();
				new MaterialAlertDialogBuilder(requireContext()).setTitle(Txt.t(R.string.tr_delete_routine_title))
					.setMessage(Txt.t(R.string.rt_delete_message))
					.setNegativeButton(Txt.t(R.string.common_cancel), null)
					.setPositiveButton(Txt.t(R.string.common_remove), (d, w) -> {
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
		String[] choices = proposal ? new String[]{Txt.t(R.string.ses_see_technique), Txt.t(R.string.rt_swap)} :
			new String[]{Txt.t(R.string.ses_see_technique)};
		new MaterialAlertDialogBuilder(requireContext()).setTitle(e.getName()).setItems(choices, (d, which) -> {
			if (which == 0) {
				app.openExercise(e);
				return;
			}
			List<Exercise> alternatives = app.routines.alternatives(i);
			if (alternatives.isEmpty()) {
				app.error(Txt.t(R.string.rt_no_alternatives));
				return;
			}
			String[] names = new String[alternatives.size()];
			for (int j = 0; j < names.length; j++)
				names[j] = alternatives.get(j).getName();
			new MaterialAlertDialogBuilder(requireContext()).setTitle(Txt.t(R.string.rt_swap_for))
				.setItems(names, (dialog, w) -> app.routines.substitute(i, alternatives.get(w).getId()))
				.setNegativeButton(Txt.t(R.string.common_cancel), null).show();
		}).setNegativeButton(Txt.t(R.string.common_cancel), null).show();
	}
}

