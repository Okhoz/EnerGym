package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.InputField;

import android.widget.LinearLayout;

/**
 * VISTA del perfil: cada campo remite texto/selección; construcción y validación pertenecen al controlador.
 * Los campos cambian con la disciplina; al elegir otra, el controlador vuelve a pintar el formulario.
 */
public final class ProfileFragment extends ScreenFragment {
	public ProfileFragment() {
		super(R.layout.fragment_profile);
	}

	protected void content() {
		heading("PERSONALIZA", "Tu perfil",
			"Define cómo quieres entrenar. Guardar conserva tu perfil; después podrás crear una rutina desde Entrenamiento.");
		LinearLayout fields = card("Tu entrenamiento", "");
		boolean resources = false, details = false;
		// Secciones de presentación: conservan el orden y los eventos de todos los campos del controlador.
		for (InputField f : app.profileFields()) {
			if (f.key.startsWith("detail.") && !details) {
				fields = card(app.disciplineName(), "Datos opcionales para describir tu práctica deportiva.");
				details = true;
			} else if (f.key.equals("daysPerWeek"))
				fields = card("Tu disponibilidad", "Usa una disponibilidad que puedas mantener.");
			else if (f.key.equals("experience"))
				fields = card("Sobre ti", "Si indicas cuánto tiempo llevas (p. ej. «2 años»), el nivel se ajusta solo; puedes cambiarlo.");
			else if (f.key.startsWith("resource.") && !resources) {
				fields = card("Material disponible",
					"Marca solo el material que podrás usar en las sesiones.");
				resources = true;
			}
			String label = FormFieldGuidance.profileLabel(f.key, f.label);
			String helper = FormFieldGuidance.profileHelper(f.key);
			if (f.bool)
				check(fields, f, helper);
			else if (!f.values.isEmpty())
				choice(fields, f.key, label, f.values, f.labels, f.value, helper, v -> app.profileInput(f.key, v));
			else
				textField(fields, f.key, label, f.value, f.number, f.number && !f.key.equals("durationHours"), helper,
					v -> {
						app.profileInput(f.key, v);
						if (f.key.equals("experience"))
							syncLevel();
					});
		}
		primary(action(rows, "Guardar perfil", !app.profileSaving(), app::saveProfile));
	}

	/** Refleja en el selector el nivel deducido de la experiencia escrita. */
	private void syncLevel() {
		android.view.View view = getView() == null ? null : getView().findViewWithTag("level");
		if (!(view instanceof android.widget.Spinner))
			return;
		android.widget.Spinner spinner = (android.widget.Spinner) view;
		String level = app.profileValue("level");
		for (int i = 0; i < spinner.getCount(); i++)
			if (level.equals(spinner.getItemAtPosition(i)) && spinner.getSelectedItemPosition() != i)
				spinner.setSelection(i);
	}
}
