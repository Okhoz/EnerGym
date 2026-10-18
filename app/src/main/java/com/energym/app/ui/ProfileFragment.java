package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
		heading(Txt.t(R.string.pf_eyebrow), Txt.t(R.string.pf_title), Txt.t(R.string.pf_subtitle));
		LinearLayout fields = card(Txt.t(R.string.pf_training), "");
		boolean resources = false, details = false;
		// Secciones de presentación: conservan el orden y los eventos de todos los campos del controlador.
		for (InputField f : app.profileFields()) {
			if (f.key.startsWith("detail.") && !details) {
				fields = card(app.disciplineName(), Txt.t(R.string.pf_details));
				details = true;
			} else if (f.key.equals("daysPerWeek"))
				fields = card(Txt.t(R.string.pf_availability), Txt.t(R.string.pf_availability_body));
			else if (f.key.equals("experience"))
				fields = card(Txt.t(R.string.body_about_you), Txt.t(R.string.pf_about_body));
			else if (f.key.startsWith("resource.") && !resources) {
				fields = card(Txt.t(R.string.pf_equipment), Txt.t(R.string.pf_equipment_body));
				resources = true;
			}
			String label = FormFieldGuidance.profileLabel(f.key, f.label);
			String helper = FormFieldGuidance.profileHelper(f.key);
			if (f.bool)
				check(fields, f, helper);
			else if (!f.values.isEmpty())
				choice(fields, f.key, label, f.values, f.labels, f.value, helper, v -> app.profileInput(f.key, v));
			else {
				textField(fields, f.key, label, f.value, f.number, f.number, helper, v -> {
					app.profileInput(f.key, v);
					if (f.key.equals("experience"))
						syncLevel();
				});
				if (f.key.equals("durationHours"))
					timeInput(fields, f.key);
			}
		}
		primary(action(rows, Txt.t(R.string.pf_save), !app.profileSaving(), app::saveProfile));
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
