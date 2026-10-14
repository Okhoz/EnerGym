package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.InputField;

import android.widget.LinearLayout;

/**
 * VISTA del perfil: cada campo remite texto/selección; construcción y validación pertenecen al controlador.
 */
public final class ProfileFragment extends ScreenFragment {
	public ProfileFragment() {
		super(R.layout.fragment_profile);
	}

	protected void content() {
		heading("PERSONALIZA", "Tu perfil",
			"Elige lo que tienes y el tiempo disponible. Tus textos se guardan, pero el plan local aún no los interpreta.");
		LinearLayout fields = card("Tu entrenamiento", "Elige la actividad, el objetivo y el entorno.");
		boolean resources = false;
		// Secciones de presentación: conservan el orden y los eventos de todos los campos del controlador.
		for (InputField f : app.profileFields()) {
			if (f.key.equals("daysPerWeek"))
				fields = card("Tu disponibilidad", "Organiza el tiempo que puedes dedicar.");
			else if (f.key.equals("level"))
				fields = card("Tu contexto", "Añade tu experiencia, preferencias y limitaciones.");
			else if (f.key.startsWith("resource.") && !resources) {
				fields = card("Material disponible",
					"Los recursos son independientes del lugar. Selecciona solo los disponibles.");
				resources = true;
			} else if (f.key.equals("historyConsent"))
				fields = card("Personalización y privacidad",
					"Permiso opcional para enviar hasta 12 mensajes anteriores al servidor que conectes en Asistente. Solo se envían al pulsar Enviar al servidor; las sesiones históricas no se adjuntan. Puedes revocarlo aquí. El modo guiado local no envía datos.");
			if (f.bool)
				check(fields, f);
			else if (!f.values.isEmpty())
				choice(fields, f.key, f.label, f.values, f.labels, f.value, v -> app.profileInput(f.key, v));
			else
				textField(fields, f.key, f.label, f.value, f.number, f.number, v -> app.profileInput(f.key, v));
		}
		action(rows, "Guardar perfil", !app.profileSaving(), app::saveProfile);
	}
}
