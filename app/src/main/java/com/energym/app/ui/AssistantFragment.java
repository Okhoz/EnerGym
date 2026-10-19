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
	/** Respuestas ya mostradas enteras: solo la nueva se «escribe» poco a poco; -1 hasta el primer pintado. */
	private int shown = -1;
	private final android.os.Handler typing = new android.os.Handler(android.os.Looper.getMainLooper());

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
		// Una sola línea de estado con acciones pequeñas: el chat ocupa la pantalla, no los avisos.
		String modeLabel = c.onDevice() ?
			(c.localGuidance() ? getString(R.string.chat_mode_guided) : c.remoteActive() ?
				getString(R.string.chat_mode_device, c.modelLabel()) : getString(R.string.chat_mode_device_off)) :
			c.automaticConnection() ? (c.localGuidance() ? getString(R.string.chat_mode_guided) : c.remoteActive() ?
				getString(R.string.chat_mode_online) : getString(R.string.chat_mode_online_off)) :
				(c.remoteActive() ? getString(R.string.chat_mode_paired) : getString(R.string.chat_mode_guided));
		LinearLayout status = new LinearLayout(requireContext());
		status.setOrientation(LinearLayout.HORIZONTAL);
		status.setGravity(Gravity.CENTER_VERTICAL);
		TextView mode = new TextView(requireContext());
		mode.setTextAppearance(R.style.EnerGym_FieldCaption);
		mode.setText(modeLabel);
		mode.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		status.addView(mode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		String ai = c.onDevice() ? getString(R.string.chat_use_device) : getString(R.string.chat_use_online);
		if (c.automaticConnection()) {
			if (c.busy)
				small(status, getString(R.string.chat_cancel), getString(R.string.chat_cancel), "chat_cancel", true,
					c::cancelPending);
			else if (c.localGuidance())
				small(status, ai, ai, "chat_use_ai", true, () -> c.selectLocalGuidance(false));
			else if (!c.remoteActive() && c.connected())
				small(status, getString(R.string.chat_retry), getString(R.string.chat_retry), "chat_retry", true,
					() -> c.selectLocalGuidance(false));
		} else if (!c.remoteActive())
			small(status, getString(R.string.chat_connect), getString(R.string.chat_connect), "chat_connect", !c.busy,
				() -> app.navigate(AppController.Screen.ACCOUNT));
		add(rows, status, 0);
		// Lo único que merece un botón grande: descargar el modelo para tener IA de verdad.
		if (c.automaticConnection() && !c.localGuidance() && !c.remoteActive() && !c.downloadOffer().isEmpty()) {
			text(rows, c.connectionNotice(), R.style.EnerGym_FieldCaption, 4);
			primary(action(rows, c.downloadOffer(), !c.busy, c::downloadModel));
		}
		if (!c.onDevice())
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
		typing.removeCallbacksAndMessages(null);
		if (shown < 0)
			shown = c.messages.size();
		if (c.messages.isEmpty() && c.preview == null && !c.busy)
			greeting(c);
		int last = c.messages.size() - 1;
		boolean replied = last >= 0 && !c.busy && !c.messages.get(last).role.equals("user");
		// La tarjeta de rutina va justo tras la respuesta que la propuso; lo hablado después queda debajo.
		int anchor = Math.min(c.previewAnchor, c.messages.size());
		for (int i = 0; i < c.messages.size(); i++) {
			if (i == anchor && c.preview != null)
				proposal(c, c.preview);
			ConversationStore.Message m = c.messages.get(i);
			TextView view = bubble(m.text, m.role.equals("user"));
			if (i == last && replied && i >= shown)
				type(view, ChatFormat.format(m.text));
			if (i == last && replied)
				replyActions(c, m.text);
		}
		shown = c.messages.size();
		if (c.preview != null && anchor >= c.messages.size())
			proposal(c, c.preview);
		if (c.busy)
			bubble(getString(R.string.chat_thinking), false).setAlpha(0.7f);
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
		compact(row, Txt.t(R.string.as_new_routine), !c.busy, () -> c.send(Txt.t(R.string.as_new_routine_message)));
	}

	private TextView bubble(String message, boolean mine) {
		TextView view = new TextView(requireContext());
		view.setTextAppearance(mine ? R.style.EnerGym_ChatUser : R.style.EnerGym_ChatAssistant);
		view.setBackgroundResource(mine ? R.drawable.bubble_user : R.drawable.bubble_assistant);
		int h = dp(18), v = dp(14);
		view.setPadding(h, v, h, v);
		view.setLineSpacing(dp(2), 1f);
		view.setTextIsSelectable(true);
		view.setText(mine ? message : styled(ChatFormat.format(message), Integer.MAX_VALUE));
		view.setContentDescription(Txt.t(mine ? R.string.as_you : R.string.as_assistant, ChatFormat.plain(message)));
		view.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8f));
		LinearLayout.LayoutParams params =
			new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = mine ? Gravity.END : Gravity.START;
		params.topMargin = dp(16);
		rows.addView(view, params);
		return view;
	}

	private static CharSequence styled(ChatFormat.Formatted f, int chars) {
		int end = ChatFormat.reveal(f.text, chars);
		android.text.SpannableString s = new android.text.SpannableString(f.text.substring(0, end));
		for (int[] b : f.bold)
			if (b[0] < end)
				s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), b[0], Math.min(b[1], end),
					android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return s;
	}

	/** Efecto de escritura de la respuesta nueva (~1 s); si la pantalla se repinta antes, sale entera. */
	private void type(TextView view, ChatFormat.Formatted f) {
		int step = Math.max(2, f.text.length() / 60);
		int[] at = {0};
		Runnable next = new Runnable() {
			public void run() {
				at[0] = ChatFormat.reveal(f.text, at[0] + step);
				view.setText(styled(f, at[0]));
				// La burbuja crece mientras se escribe: se sigue bajando para que no se quede oculta.
				View root = getView();
				ScrollView scroll = root == null ? null : root.findViewById(R.id.chat_scroll);
				if (scroll != null)
					scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
				if (at[0] < f.text.length())
					typing.postDelayed(this, 16);
			}
		};
		next.run();
	}

	/** Pantalla vacía al estilo Gemini: saludo y sugerencias para empezar con un toque. */
	private void greeting(ProposalController c) {
		TextView hello = text(rows, getString(R.string.chat_greeting), R.style.EnerGym_Body, 24);
		hello.setTextSize(24);
		text(rows, getString(R.string.chat_greeting_body), R.style.EnerGym_FieldCaption, 8);
		chips(c, FollowUps.STARTERS);
	}

	/** Bajo la última respuesta: copiar, regenerar y preguntas de seguimiento. */
	private void replyActions(ProposalController c, String reply) {
		// Todo lo que ha preguntado el cliente: las sugerencias siguen el hilo y no repiten lo ya preguntado.
		java.util.List<String> questions = new java.util.ArrayList<>();
		for (int i = 0; i < c.messages.size(); i++)
			if (c.messages.get(i).role.equals("user"))
				questions.add(c.messages.get(i).text);
		String asked = questions.isEmpty() ? null : questions.get(questions.size() - 1);
		// Solo la bienvenida, sin nada preguntado aún: sugerencias para empezar, sin copiar ni regenerar.
		if (asked == null) {
			chips(c, FollowUps.STARTERS);
			return;
		}
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		small(row, "📋 " + getString(R.string.chat_copy), getString(R.string.chat_copy_description), "chat_copy",
			true, () -> {
				android.content.ClipboardManager clip = (android.content.ClipboardManager)
					requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
				clip.setPrimaryClip(android.content.ClipData.newPlainText("EnerGym", ChatFormat.plain(reply)));
				Toast.makeText(requireContext(), R.string.chat_copied, Toast.LENGTH_SHORT).show();
			});
		String again = asked;
		small(row, "🔄 " + getString(R.string.chat_regenerate), getString(R.string.chat_regenerate_description),
			"chat_regenerate", !c.busy, () -> c.send(again));
		add(rows, row, 2);
		chips(c, FollowUps.suggest(questions, ChatFormat.plain(reply), c.preview != null));
	}

	/** Botón de texto pequeño bajo la burbuja, como los iconos de acción de Gemini. */
	private void small(LinearLayout row, String label, String description, String tag, boolean enabled,
		Runnable click) {
		MaterialButton b = new MaterialButton(requireContext(), null,
			com.google.android.material.R.attr.borderlessButtonStyle);
		b.setText(label);
		b.setTextSize(13);
		b.setAllCaps(false);
		b.setEnabled(enabled);
		b.setContentDescription(description);
		b.setTag(tag);
		b.setOnClickListener(v -> click.run());
		row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
	}

	private void chips(ProposalController c, java.util.List<FollowUps.Topic> topics) {
		com.google.android.material.chip.ChipGroup group = new com.google.android.material.chip.ChipGroup(requireContext());
		group.setTag("chat_followups");
		for (FollowUps.Topic t : topics) {
			String label = getString(label(t));
			com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
			chip.setText(label);
			chip.setEnabled(!c.busy);
			chip.setOnClickListener(v -> c.send(label.replace(" 💪", "")));
			group.addView(chip);
		}
		add(rows, group, 8);
	}

	private static int label(FollowUps.Topic t) {
		switch (t) {
			case ROUTINE_TODAY:
				return R.string.followup_routine_today;
			case PROGRESSION:
				return R.string.followup_progression;
			case TECHNIQUE:
				return R.string.followup_technique;
			case ALTERNATIVE:
				return R.string.followup_alternative;
			case WARMUP:
				return R.string.followup_warmup;
			case DELOAD:
				return R.string.followup_deload;
			case NUTRITION:
				return R.string.followup_nutrition;
			case REST:
				return R.string.followup_rest;
			case REST_SETS:
				return R.string.followup_rest_sets;
			case SETS_PER_MUSCLE:
				return R.string.followup_sets_per_muscle;
			case SUPERSETS:
				return R.string.followup_supersets;
			case PROTEIN:
				return R.string.followup_protein;
			case DEFICIT:
				return R.string.followup_deficit;
			case CARDIO:
				return R.string.followup_cardio;
			case SLEEP:
				return R.string.followup_sleep;
			case SPLIT_DAYS:
				return R.string.followup_split_days;
			case PRE_WORKOUT:
				return R.string.followup_pre_workout;
			case MOBILITY:
				return R.string.followup_mobility;
			case HOME:
				return R.string.followup_home;
			default:
				return R.string.followup_motivation;
		}
	}

	@Override
	public void onDestroyView() {
		typing.removeCallbacksAndMessages(null);
		super.onDestroyView();
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
