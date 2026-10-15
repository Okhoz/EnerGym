package com.energym.app.ui;

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
		boolean unavailable = c.automaticConnection() && !c.localGuidance() && !c.remoteActive();
		if (!unavailable)
			input.setText("");
		c.send(text);
	}

	protected void content() {
		ProposalController c = state.assistant;
		View composer = getView().findViewById(R.id.chat_composer);
		if (app.owner() == null || c == null) {
			composer.setVisibility(View.GONE);
			bubble("¡Hola! Soy tu asistente de EnerGym. Entra con tu cuenta para guardar nuestra conversación.", false);
			MaterialButton open = action(rows, "Entrar", true, () -> app.navigate(AppController.Screen.ACCOUNT));
			primary(open);
			return;
		}
		composer.setVisibility(View.VISIBLE);
		String modeLabel = c.onDevice() ?
			(c.localGuidance() ? "Guiado local · sin IA" : c.remoteActive() ? "Entrenador IA · " + c.modelLabel() + " en tu móvil" :
				"IA del móvil no disponible · guiado local") : c.automaticConnection() ?
			(c.localGuidance() ? "Guiado local · sin IA" : c.remoteActive() ? "IA online · cuenta de Google" : "IA online no disponible") :
			(c.remoteActive() ? "Servidor de IA emparejado · conversación" : "Guiado local · sin IA");
		TextView mode = text(rows, modeLabel, R.style.EnerGym_FieldCaption, 4);
		mode.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		if (c.automaticConnection()) {
			String ai = c.onDevice() ? "IA del móvil" : "IA online";
			if (c.localGuidance())
				action(rows, "Usar " + ai, !c.busy, () -> c.selectLocalGuidance(false));
			else {
				if (!c.remoteActive()) {
					text(rows, c.connectionNotice(), R.style.EnerGym_Body, 8);
					if (!c.downloadOffer().isEmpty())
						primary(action(rows, c.downloadOffer(), !c.busy, c::downloadModel));
					if (c.connected())
						action(rows, "Reintentar " + ai, !c.busy, () -> c.selectLocalGuidance(false));
				}
				action(rows, "Usar guiado local", !c.busy, () -> c.selectLocalGuidance(true));
			}
			if (c.busy)
				action(rows, "Cancelar solicitud", true, c::cancelPending);
		} else if (!c.remoteActive())
			action(rows, "Conectar IA en Cuenta", !c.busy, () -> app.navigate(AppController.Screen.ACCOUNT));
		if (c.onDevice())
			text(rows, "Todo se procesa en tu móvil: ningún mensaje sale del dispositivo, no necesita internet " +
				"y no tiene coste. Tu entrenador recuerda los últimos mensajes de esta conversación.",
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
		context.setText("Recordar esta conversación (hasta 12 mensajes)");
		context.setChecked(c.historyContextEnabled());
		context.setMinHeight(dp(48));
		context.setOnCheckedChangeListener((button, enabled) -> c.historyContext(enabled));
		add(rows, context, 4);
		text(rows, "Al activar contexto se comparten con la IA hasta 12 mensajes anteriores de este chat. " +
			"El permiso del chat dura esta conversación y no cambia tu perfil. No se adjuntan tu historial de entrenamientos " +
			"ni tus datos de nutrición o perfil físico. Al pedir una rutina se comparten los filtros y limitaciones que indicaste " +
			"para adaptarla. El mensaje actual se envía siempre al usar IA.",
			R.style.EnerGym_FieldCaption, 4);
	}

	private void conversation(ProposalController c, MaterialButton send) {
		send.setEnabled(!c.busy);
		send.setAlpha(c.busy ? 0.5f : 1f);
		if (c.exercise != null) {
			MaterialButton chip = action(rows, "Hablando de: " + c.exercise.getName() + "  ›", true, () ->
				app.openExercise(c.exercise));
			chip.setMinHeight(dp(40));
			chip.setTextSize(14);
			((LinearLayout.LayoutParams) chip.getLayoutParams()).topMargin = dp(4);
			((LinearLayout.LayoutParams) chip.getLayoutParams()).bottomMargin = dp(8);
		}
		for (ConversationStore.Message m : c.messages)
			bubble(m.text, m.role.equals("user"));
		if (c.busy)
			bubble("Escribiendo…", false).setAlpha(0.7f);
		if (c.preview != null)
			proposal(c, c.preview);
		if (!c.status.isEmpty()) {
			TextView note = text(rows, c.status, R.style.EnerGym_FieldCaption, 12);
			note.setGravity(Gravity.CENTER);
			note.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		}
		ScrollView scroll = getView().findViewById(R.id.chat_scroll);
		scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
	}

	private TextView bubble(String message, boolean mine) {
		TextView view = new TextView(requireContext());
		view.setTextAppearance(mine ? R.style.EnerGym_ChatUser : R.style.EnerGym_ChatAssistant);
		view.setBackgroundResource(mine ? R.drawable.bubble_user : R.drawable.bubble_assistant);
		int h = dp(16), v = dp(12);
		view.setPadding(h, v, h, v);
		view.setTextIsSelectable(true);
		view.setText(message);
		view.setContentDescription((mine ? "Tú: " : "Asistente: ") + message);
		view.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8f));
		LinearLayout.LayoutParams params =
			new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = mine ? Gravity.END : Gravity.START;
		params.topMargin = dp(10);
		rows.addView(view, params);
		return view;
	}

	/**
	 * La propuesta aparece como respuesta del asistente: resumen, ejercicios y decisiones sin guardar nada antes.
	 */
	private void proposal(ProposalController c, Routine routine) {
		StringBuilder text = new StringBuilder("Propuesta · sin guardar\n").append(routine.getTitle()).append(" · ")
			.append(routine.getEstimatedMinutes()).append(" min");
		for (RoutineItem item : routine.getItems())
			text.append("\n\n• ").append(item.getExercise().getName()).append(" (").append(item.getEstimatedMinutes())
				.append(" min)\n").append(AppController.prescription(item.getExercise().getMetricType(),
					item.getPrescription()));
		text.append("\n\nEjemplo sin revisión profesional. Al aceptar se guardan el perfil mostrado y la rutina.");
		bubble(text.toString(), false);
		LinearLayout choices = columns(rows, 10);
		primary(compact(choices, "Aceptar", !c.busy && !routine.isEmpty(), c::accept));
		compact(choices, "Rechazar", !c.busy, c::reject);
		LinearLayout tweaks = columns(rows, 8);
		compact(tweaks, "−5 min", !c.busy, c::reduceTime);
		compact(tweaks, "Cambiar 1.º", !c.busy, c::substituteFirst).setContentDescription("Sustituir primer ejercicio");
		TextView hint = text(rows, "Toca un ejercicio para ver su técnica:", R.style.EnerGym_FieldCaption, 14);
		hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		for (RoutineItem item : routine.getItems()) {
			MaterialButton open = action(rows, item.getExercise().getName(), true,
				() -> app.openExercise(item.getExercise()));
			open.setMinHeight(dp(44));
			open.setTextSize(14);
			open.setContentDescription("Consultar técnica de " + item.getExercise().getName());
			((LinearLayout.LayoutParams) open.getLayoutParams()).topMargin = dp(8);
		}
	}
}
