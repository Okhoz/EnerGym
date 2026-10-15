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
		input.setText("");
		// Con servidor emparejado responde la IA; sin él, el guiado local gratuito.
		if (c.connected())
			c.sendExternal(text);
		else
			c.sendLocal(text);
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
		MaterialButton send = getView().findViewById(R.id.chat_send);
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
	}
}
