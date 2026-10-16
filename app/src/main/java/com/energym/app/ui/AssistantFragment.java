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
			bubble("Escribiendo…", false).setAlpha(0.7f);
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
			primary(compact(row, "▶ Continuar entreno", !c.busy, () -> app.resume(open)));
		} else if (app.routines.accepted != null && !app.routines.accepted.isEmpty())
			primary(compact(row, "▶ Empezar entreno", !c.busy, app::startSession));
		if (app.progression() != null)
			compact(row, "📈 Subir de nivel", !c.busy, app::reviewProgression);
		compact(row, "🆕 Rutina nueva", !c.busy, () -> c.send("Hazme una rutina para hoy"));
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
		view.setContentDescription((mine ? "Tú: " : "Asistente: ") + message);
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
		primary(compact(choices, "Aceptar", !c.busy && !routine.isEmpty(), c::accept));
		compact(choices, "Rechazar", !c.busy, c::reject);
		LinearLayout tweaks = columns(rows, 8);
		compact(tweaks, "−5 min", !c.busy, c::reduceTime);
		compact(tweaks, "+5 min", !c.busy, c::extendTime);
		TextView hint = text(rows, "Toca un ejercicio para ver su técnica o 🔄 para cambiarlo:",
			R.style.EnerGym_FieldCaption, 14);
		hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		for (int i = 0; i < routine.getItems().size(); i++) {
			RoutineItem item = routine.getItems().get(i);
			int index = i;
			LinearLayout line = columns(rows, 8);
			MaterialButton open = compact(line, item.getExercise().getName(), true,
				() -> app.openExercise(item.getExercise()));
			open.setTextSize(14);
			open.setContentDescription("Consultar técnica de " + item.getExercise().getName());
			((LinearLayout.LayoutParams) open.getLayoutParams()).weight = 4;
			MaterialButton swap = compact(line, "🔄", !c.busy, () -> c.substitute(index));
			swap.setContentDescription("Cambiar " + item.getExercise().getName() + " por otro ejercicio");
		}
	}
}
