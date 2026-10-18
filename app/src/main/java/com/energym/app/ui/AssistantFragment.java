package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.assistant.*;
import com.energym.app.domain.*;
import com.energym.app.controller.AppController;
import com.google.android.material.button.MaterialButton;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

/**
 * VISTA de chat: burbujas de la conversación y compositor fijo. Solo remite eventos; las reglas, la red
 * y las escrituras pertenecen al controlador. La conexión del servidor se configura en Cuenta.
 */
public final class AssistantFragment extends ScreenFragment {
	public AssistantFragment() {
		super(R.layout.fragment_assistant);
	}

	@Override
	public void onViewCreated(View view, Bundle saved) {
		EditText input = view.findViewById(R.id.chat_input);
		input.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ProposalController c = controller();
				if (c != null)
					c.messageInput(s.toString());
			}

			public void afterTextChanged(Editable e) {
			}
		});
		view.findViewById(R.id.chat_send).setOnClickListener(v -> send(input));
		super.onViewCreated(view, saved);
		ProposalController c = controller();
		if (c != null && !c.composer.isEmpty()) {
			input.setText(c.composer);
			input.setSelection(input.length());
		}
	}

	private ProposalController controller() {
		return state == null ? ((com.energym.app.MainActivity) requireActivity()).state().assistant : state.assistant;
	}

	private void send(EditText input) {
		ProposalController c = controller();
		if (c == null || c.busy || app == null)
			return;
		String text = input.getText().toString();
		if (text.trim().isEmpty())
			return;
		// Sin IA online el mensaje se queda escrito; la IA del móvil no disponible responde con el guiado local.
		boolean unavailable = c.automaticConnection() && !c.localGuidance() && !c.remoteActive() && !c.onDevice();
		if (!unavailable)
			input.setText("");
		c.send(text);
	}

	protected void content() {
		ProposalController c = state.assistant;
		View composer = getView().findViewById(R.id.chat_composer);
		if (app.owner() == null || c == null) {
			composer.setVisibility(View.GONE);
			bubble(Txt.t(R.string.as_hello), false);
			MaterialButton open = action(rows, Txt.t(R.string.as_sign_in), true, () -> app.navigate(AppController.Screen.ACCOUNT));
			primary(open);
			return;
		}
		composer.setVisibility(View.VISIBLE);
		String modeLabel = c.onDevice() ?
			(c.localGuidance() ? Txt.t(R.string.as_mode_local) : c.remoteActive() ? Txt.t(R.string.as_mode_device, c.modelLabel()) :
				Txt.t(R.string.as_mode_device_unavailable)) : c.automaticConnection() ?
			(c.localGuidance() ? Txt.t(R.string.as_mode_local) : c.remoteActive() ? Txt.t(R.string.as_mode_online) :
				Txt.t(R.string.as_mode_online_unavailable)) :
			(c.remoteActive() ? Txt.t(R.string.as_mode_server) : Txt.t(R.string.as_mode_local));
		TextView mode = text(rows, modeLabel, R.style.EnerGym_FieldCaption, 4);
		mode.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		if (c.automaticConnection()) {
			String ai = Txt.t(c.onDevice() ? R.string.as_device_ai : R.string.as_online_ai);
			if (c.localGuidance())
				action(rows, Txt.t(R.string.as_use, ai), !c.busy, () -> c.selectLocalGuidance(false));
			else {
				if (!c.remoteActive()) {
					text(rows, c.connectionNotice(), R.style.EnerGym_Body, 8);
					if (!c.downloadOffer().isEmpty())
						primary(action(rows, c.downloadOffer(), !c.busy, c::downloadModel));
					if (c.connected())
						action(rows, Txt.t(R.string.as_retry, ai), !c.busy, () -> c.selectLocalGuidance(false));
				}
				action(rows, Txt.t(R.string.as_use_local), !c.busy, () -> c.selectLocalGuidance(true));
			}
			if (c.busy)
				action(rows, Txt.t(R.string.as_cancel_request), true, c::cancelPending);
		} else if (!c.remoteActive())
			action(rows, Txt.t(R.string.as_connect_in_account), !c.busy, () -> app.navigate(AppController.Screen.ACCOUNT));
		if (c.onDevice())
			text(rows, Txt.t(R.string.as_on_device_notice),
				R.style.EnerGym_FieldCaption, 4);
		else
			historyConsent(c);
		conversation(c, getView().findViewById(R.id.chat_send));
	}

	/**
	 * Permiso para enviar mensajes previos a una IA externa; con la IA del móvil no hace falta.
	 */
	private void historyConsent(ProposalController c) {
		com.google.android.material.checkbox.MaterialCheckBox context =
			new com.google.android.material.checkbox.MaterialCheckBox(requireContext());
		context.setTag("assistant_history_context");
		context.setText(Txt.t(R.string.as_remember));
		context.setChecked(c.historyContextEnabled());
		context.setMinHeight(dp(48));
		context.setOnCheckedChangeListener((button, enabled) -> c.historyContext(enabled));
		add(rows, context, 4);
		text(rows, Txt.t(R.string.as_remember_notice),
			R.style.EnerGym_FieldCaption, 4);
	}

	private void conversation(ProposalController c, MaterialButton send) {
		send.setEnabled(!c.busy);
		send.setAlpha(c.busy ? 0.5f : 1f);
		if (c.exercise != null) {
			MaterialButton chip = action(rows, Txt.t(R.string.as_talking_about, c.exercise.getName()) + "  ›", true, () ->
				app.openExercise(c.exercise));
		}
		// La tarjeta de rutina va justo tras la respuesta que la propuso; lo hablado después queda debajo.
		int anchor = Math.min(c.previewAnchor, c.messages.size());
		for (int i = 0; i < c.messages.size(); i++) {
			if (i == anchor && c.preview != null)
				proposal(c, c.preview);
			ConversationStore.Message m = c.messages.get(i);
			bubble(m.text, m.role.equals("user"));
		}
		if (c.preview != null && anchor >= c.messages.size())
			proposal(c, c.preview);
		if (c.busy)
			bubble(Txt.t(R.string.as_typing), false).setAlpha(0.7f);
		if (!c.status.isEmpty()) {
			TextView note = text(rows, c.status, R.style.EnerGym_FieldCaption, 12);
			note.setGravity(Gravity.CENTER);
			note.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		}
		shortcuts(c);
		ScrollView scroll = getView().findViewById(R.id.chat_scroll);
		scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
	}

	/**
	 * Atajos de entrenamiento junto al chat: solo los que tienen sentido ahora (continuar, empezar, subir de
	 * nivel) y pedir una rutina nueva, para pasar de la conversación al entreno sin salir del chat.
	 */
	private void shortcuts(ProposalController c) {
		if (c.preview != null)
			return;
		Session draft = null;
		for (Session s : app.pending)
			if (s.getStatus() == Session.Status.DRAFT) {
				draft = s;
				break;
			}
		LinearLayout row = columns(rows, 14);
		if (draft != null) {
			Session open = draft;
			primary(compact(row, Txt.t(R.string.hist_continue_workout), !c.busy, () -> app.resume(open)));
		} else if (app.routines.accepted != null && !app.routines.accepted.isEmpty())
			primary(compact(row, Txt.t(R.string.as_start_workout), !c.busy, app::startSession));
		if (app.progression() != null)
			compact(row, Txt.t(R.string.as_level_up), !c.busy, app::reviewProgression);
		compact(row, Txt.t(R.string.as_new_routine), !c.busy, () -> c.send("Hazme una rutina para hoy"));
	}

	private TextView bubble(String message, boolean mine) {
		TextView view = new TextView(requireContext());
		view.setTextAppearance(mine ? R.style.EnerGym_ChatUser : R.style.EnerGym_ChatAssistant);
		view.setBackgroundResource(mine ? R.drawable.bubble_user : R.drawable.bubble_assistant);
		int h = dp(18), v = dp(14);
		view.setPadding(h, v, h, v);
		view.setLineSpacing(dp(2), 1f);
		view.setTextIsSelectable(true);
		view.setText(message);
		view.setContentDescription(Txt.t(mine ? R.string.as_you : R.string.as_assistant, message));
		view.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8f));
		LinearLayout.LayoutParams params =
			new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = mine ? Gravity.END : Gravity.START;
		params.topMargin = dp(16);
		rows.addView(view, params);
		return view;
	}

	/**
	 * La propuesta aparece como respuesta del asistente: resumen, ejercicios y decisiones sin guardar nada antes.
	 */
	private void proposal(ProposalController c, Routine routine) {
		bubble(com.energym.app.assistant.RoutineCard.text(routine), false);
		LinearLayout choices = columns(rows, 10);
		primary(compact(choices, Txt.t(R.string.as_accept), !c.busy && !routine.isEmpty(), c::accept));
		compact(choices, Txt.t(R.string.as_reject), !c.busy, c::reject);
		LinearLayout tweaks = columns(rows, 8);
		compact(tweaks, "−5 min", !c.busy, c::reduceTime);
		compact(tweaks, "+5 min", !c.busy, c::extendTime);
		TextView hint = text(rows, Txt.t(R.string.as_proposal_hint),
			R.style.EnerGym_FieldCaption, 14);
		hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		for (int i = 0; i < routine.getItems().size(); i++) {
			RoutineItem item = routine.getItems().get(i);
			int index = i;
			LinearLayout line = columns(rows, 8);
			MaterialButton open = compact(line, item.getExercise().getName(), true,
				() -> app.openExercise(item.getExercise()));
			open.setContentDescription(Txt.t(R.string.as_check_technique, item.getExercise().getName()));
			((LinearLayout.LayoutParams) open.getLayoutParams()).weight = 4;
			MaterialButton swap = compact(line, "🔄", !c.busy, () -> c.substitute(index));
			swap.setContentDescription(Txt.t(R.string.as_swap, item.getExercise().getName()));
		}
	}
}
