package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;

import android.widget.LinearLayout;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import android.content.res.ColorStateList;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * VISTA de inicio: resumen del estado y accesos a los eventos del controlador.
 */
public final class HomeFragment extends ScreenFragment {
	public HomeFragment() {
		super(R.layout.fragment_home);
	}

	protected void content() {
		heading(getString(R.string.home_eyebrow), getString(R.string.home_title), "");
		View overview = getLayoutInflater().inflate(R.layout.row_home_overview, rows, false);
		Locale locale = getResources().getConfiguration().getLocales().isEmpty() ? Locale.getDefault() :
			getResources().getConfiguration().getLocales().get(0);
		NumberFormat counts = NumberFormat.getIntegerInstance(locale);
		((TextView) overview.findViewById(R.id.home_sessions)).setText(counts.format(app.history.size()));
		((TextView) overview.findViewById(R.id.home_drafts)).setText(counts.format(app.pending.size()));
		View profileShortcut = overview.findViewById(R.id.quick_profile);
		profileShortcut.setContentDescription(getString(app.owner() == null ?
			R.string.profile_shortcut_signed_out : R.string.profile_shortcut_description));
		profileShortcut.setOnClickListener(v -> app.navigate(app.owner() == null ? Screen.ACCOUNT : Screen.PROFILE));
		overview.findViewById(R.id.quick_training).setOnClickListener(v -> app.navigate(Screen.TRAINING));
		overview.findViewById(R.id.quick_assistant).setOnClickListener(v -> app.navigate(Screen.ASSISTANT));
		rows.addView(overview);
		LinearLayout next = card(getString(R.string.home_next_step), app.owner() == null ?
			getString(R.string.home_account_prompt) :
			app.profile == null ? getString(R.string.home_profile_prompt) : app.profileSummary());
		MaterialButton primary;
		if (app.owner() == null)
			primary = action(next, getString(R.string.home_choose_account), true, () -> app.navigate(Screen.ACCOUNT));
		else
			primary = action(next,
				getString(app.profile == null ? R.string.home_configure_profile : R.string.home_edit_profile), true,
				() -> app.navigate(Screen.PROFILE));
		// La acción principal tiene más contraste; conserva el mismo evento del controlador.
		primary.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gold)));
		primary.setTextColor(ContextCompat.getColor(requireContext(), R.color.surface));
		LinearLayout training = card(getString(R.string.home_training_title), getString(R.string.home_training_body));
		action(training, getString(R.string.home_view_training), () -> app.navigate(Screen.TRAINING));
		if (app.owner() != null && state.nutrition != null) {
			double[] today = state.nutrition.totals(state.nutrition.today());
			com.energym.app.nutrition.BodyProfile.Targets targets = state.nutrition.targets();
			LinearLayout food = card(getString(R.string.nutrition), targets == null ?
				"Escanea tus alimentos y sigue calorías y macros del día." :
				"Hoy: " + Math.round(today[0]) + " de " + Math.round(targets.kcal) + " kcal · proteínas " +
					Math.round(today[1]) + " de " + Math.round(targets.protein) + " g");
			action(food, "Abrir nutrición", () -> app.navigate(Screen.NUTRITION));
		}
		LinearLayout history = card(getString(R.string.home_record_title), state.syncText());
		action(history, getString(R.string.home_view_history), () -> app.navigate(Screen.HISTORY));
		if (!app.notice.isEmpty())
			card(getString(R.string.home_last_action), app.notice);
	}
}
