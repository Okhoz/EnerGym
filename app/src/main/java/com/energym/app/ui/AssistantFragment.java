package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.assistant.*;
import com.energym.app.domain.*;
import com.energym.app.controller.AppController;

import android.widget.*;
import android.text.InputType;
import android.text.InputFilter;

/**
 * VISTA MVC: renderiza snapshots y remite eventos. Las reglas, red y escrituras pertenecen al controlador.
 */
public final class AssistantFragment extends ScreenFragment {
	public AssistantFragment() {
		super(R.layout.fragment_assistant);
	}

	protected void content() {
		ProposalController c = state.assistant;
		heading("APRENDE Y AJUSTA", "Asistente", c.connected() ?
			"Elige cómo conversar: guiado local gratuito por reglas o IA mediante tu servidor conectado." :
			"Guiado local · gratuito\nConversación por reglas para preparar rutinas y explicar tu ejercicio.");
		if (app.owner() == null) {
			LinearLayout sign = card("Conserva tu conversación",
				"Entra en demo local o conecta tu cuenta para continuar. Cada cuenta conserva su propia conversación en este dispositivo.");
			action(sign, "Abrir cuenta", () -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		if (c.exercise != null) {
			LinearLayout context = card("Ejercicio seleccionado",
				c.exercise.getName() + "\nLa explicación usa la versión que has abierto.");
			action(context, "Ver técnica del ejercicio", () -> app.openExercise(c.exercise));
		}
		LinearLayout composer = card("Tu mensaje",
			"Ejemplo: tres días en casa, sin material, 30 min. Después indica el objetivo y tu nivel.");
		textField(composer, "assistant_message", "Mensaje", c.composer, false, false, c::messageInput);
		EditText edit = composer.findViewWithTag("assistant_message");
		edit.setInputType(
			InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		edit.setMinLines(2);
		edit.setMaxLines(5);
		edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
		action(composer, c.busy ? "Procesando…" : "Enviar local", !c.busy, () -> c.sendLocal(c.composer));
		if (c.connected())
			action(composer, "Enviar al servidor opcional", !c.busy, () -> c.sendExternal(c.composer));
		if (!c.status.isEmpty())
			card("Estado", c.status);
		if (!c.messages.isEmpty()) {
			ConversationStore.Message last = c.messages.get(c.messages.size() - 1);
			card(last.role.equals("user") ? "Tu mensaje enviado" :
				last.source.equals("remote") ? "IA conectada" : "Asistente guiado", last.text);
		}
		if (c.preview != null) {
			Routine routine = c.preview;
			card("Vista previa · sin guardar",
				app.profileSummary(routine.getProfile()) + "\nTiempo estimado: " + routine.getEstimatedMinutes() +
					" min\n" + routine.getExplanation());
			for (RoutineItem item : routine.getItems()) {
				LinearLayout movement = card(item.getExercise().getName(), item.getEstimatedMinutes() + " min\n" +
					AppController.prescription(item.getExercise().getMetricType(), item.getPrescription()) + "\n" +
					item.getJustification());
				action(movement, "Consultar técnica", () -> app.openExercise(item.getExercise()));
			}
			LinearLayout decisions = card("Revisa y decide",
				"Aceptar guarda el perfil mostrado y una nueva versión de rutina. Rechazar conserva los datos actuales y las sesiones anteriores.");
			action(decisions, "Guardar perfil y aceptar", !c.busy && !routine.isEmpty(), c::accept);
			action(decisions, "Rechazar propuesta", !c.busy, c::reject);
			action(decisions, "Reducir 5 minutos", !c.busy, c::reduceTime);
			action(decisions, "Sustituir primer ejercicio", !c.busy, c::substituteFirst);
		}
		LinearLayout history = card("Conversación local", c.messages.size() +
			" mensajes conservados en este dispositivo. El servidor recibe como máximo 12 mensajes solo con el permiso de tu perfil; las sesiones históricas no se envían.");
		action(history, c.showHistory ? "Ocultar conversación" : "Ver conversación", c::toggleHistory);
		if (c.showHistory)
			for (ConversationStore.Message m : c.messages)
				card(m.role.equals("user") ? "Tú" : m.source.equals("remote") ? "IA conectada" : "Asistente guiado",
					m.text);
		LinearLayout connection = card("Servidor opcional de IA", c.connected() ?
			"Servidor emparejado. La IA real requiere un proveedor configurado; el envío es siempre explícito." :
			"Desactivado por defecto. Conecta un servidor con Gemini o ChatGPT configurado para usar IA real. El modo guiado local funciona sin esta conexión.");
		action(connection, c.showConnection ? "Ocultar conexión" : "Configurar conexión opcional", c::toggleConnection);
		if (c.showConnection) {
			card("Qué compartirás",
				"Al pulsar Enviar al servidor se envían tu mensaje, el perfil actual y el ID de ejercicio. Los datos de planificación que indiques en esta conversación se mantienen en un perfil provisional para continuar las preguntas. Los mensajes anteriores se adjuntan solo con el consentimiento opcional del perfil. El proveedor externo puede tener su propia política de retención.");
			textField(connection, "assistant_endpoint", "URL del servidor (HTTPS)", c.endpoint, false, false,
				c::endpointInput);
			textField(connection, "assistant_pairing", "Código privado de emparejamiento", c.pairingCode, false, false,
				c::pairingInput);
			EditText pairing = connection.findViewWithTag("assistant_pairing");
			pairing.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			pairing.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
			action(connection, "Conectar servidor", !c.busy, () -> c.connect(c.endpoint, c.pairingCode));
		}
		if (c.connected())
			action(connection, "Desconectar y revocar", !c.busy, c::disconnect);
	}
}



