package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.controller.AccountController;
import com.energym.app.domain.Session;
import com.energym.app.nutrition.BodyProfile;
import com.energym.app.progress.ProgressStats;
import com.energym.app.ui.charts.RingView;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * VISTA de inicio: lo importante de hoy en tres bloques (entreno, alimentación, racha) y una acción principal.
 */
public final class HomeFragment extends ScreenFragment {
	public HomeFragment() {
		super(R.layout.fragment_home);
	}

	protected void content() {
		String date = LocalDate.now().format(DateTimeFormatter.ofPattern(Txt.t(R.string.pattern_weekday_day_month), Txt.locale()));
		heading(getString(R.string.home_eyebrow), greeting(),
			Character.toUpperCase(date.charAt(0)) + date.substring(1));
		// Inicio solo dice qué toca hoy; todas las cifras y gráficas están en Progreso.

		if (app.owner() == null) {
			LinearLayout start = card(getString(R.string.home_next_step), getString(R.string.home_account_prompt));
			boolean signing = state.accountStatus == AccountController.Status.SIGNING_IN;
			boolean busy = signing || state.deletingLocalData;
			if (state.accountStatus == AccountController.Status.CONFIGURATION_NEEDED)
				text(start, getString(R.string.google_configuration_missing), R.style.EnerGym_FieldCaption, 8);
			primary(action(start, getString(signing ? R.string.google_connecting : R.string.google_continue), !busy,
				() -> state.accounts.signInWithGoogle(requireActivity(), false)));
			action(start, getString(R.string.home_choose_account), !busy, () -> app.navigate(Screen.ACCOUNT));
			return;
		}
		today();
		food();
		if (!app.notice.isEmpty())
			text(rows, app.notice, R.style.EnerGym_FieldCaption, 4);
	}

	/**
	 * Una única acción principal según el punto en el que esté la persona.
	 */
	private void today() {
		if (app.profile == null) {
			LinearLayout next = card(getString(R.string.home_next_step), getString(R.string.home_profile_prompt));
			primary(action(next, getString(R.string.home_configure_profile), true, () -> app.navigate(Screen.PROFILE)));
			return;
		}
		Session draft = app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT ?
			app.sessions.session : app.pending.isEmpty() ? null : app.pending.get(0);
		if (draft != null) {
			LinearLayout resume = card(Txt.t(R.string.home_half_done), draft.getRoutine().getTitle() + " · " +
				Txt.n(R.plurals.hist_exercises_of_total, draft.getRoutine().getItems().size(), draft.getResults().size(), draft.getRoutine().getItems().size()));
			primary(action(resume, Txt.t(R.string.home_continue), !app.sessions.busy, () -> {
				if (draft == app.sessions.session)
					app.navigate(Screen.SESSION);
				else
					app.resume(draft);
			}));
			return;
		}
		com.energym.app.domain.ProgressionEngine.Plan level = app.progression();
		if (level != null && app.routines.preview == null) {
			LinearLayout up = card(Txt.t(R.string.home_level_up), summary(level));
			primary(action(up, Txt.t(R.string.home_review_progression), !app.routines.busy, app::reviewProgression));
			action(up, Txt.t(R.string.home_repeat_current), !app.sessions.busy, app::startSession);
			return;
		}
		if (app.routines.accepted != null) {
			LinearLayout ready = card(Txt.t(R.string.home_today_workout), app.routines.accepted.getTitle() + " · " +
				Txt.n(R.plurals.tr_summary, app.routines.accepted.getItems().size(), app.routines.accepted.getItems().size(), app.routines.accepted.getEstimatedMinutes()));
			primary(action(ready, Txt.t(R.string.home_start), !app.sessions.busy, app::startSession));
			return;
		}
		LinearLayout create = card(Txt.t(R.string.home_today_workout), Txt.t(R.string.home_no_routine));
		primary(action(create, Txt.t(R.string.home_create_routine), !app.routines.busy, () -> app.navigate(Screen.TRAINING)));
	}

	/** Tu día de un vistazo: calorías y agua, con añadir comida o un vaso a un toque. */
	private void food() {
		if (state.nutrition == null)
			return;
		double[] today = state.nutrition.totals(state.nutrition.today());
		BodyProfile.Targets targets = state.nutrition.targets();
		String water = "";
		if (state.water != null) {
			int ml = state.water.ml(state.nutrition.today());
			water = "\n" + Txt.t(R.string.home_water, com.energym.app.nutrition.WaterLog.liters(ml),
				com.energym.app.nutrition.WaterLog.liters(com.energym.app.nutrition.WaterLog.goal(state.nutrition.effectiveBody())));
		}
		LinearLayout food = card(Txt.t(R.string.home_your_day), (targets == null ? Txt.t(R.string.home_food_empty) :
			Txt.t(R.string.home_food_kcal, Math.round(today[0]), Math.round(targets.kcal))) + water);
		LinearLayout buttons = columns(food, 16);
		compact(buttons, targets == null ? Txt.t(R.string.nutrition) : Txt.t(R.string.home_add_meal), true, () -> app.navigate(Screen.NUTRITION))
			.setTag("home_add_food");
		if (state.water != null)
			compact(buttons, Txt.t(R.string.nut_glass_more), true, () -> state.water.add(state.nutrition.today(),
				com.energym.app.nutrition.WaterLog.GLASS)).setContentDescription(Txt.t(R.string.home_add_glass));
	}

	static String summary(com.energym.app.domain.ProgressionEngine.Plan plan) {
		StringBuilder text = new StringBuilder(Txt.t(R.string.home_progress_intro));
		for (int i = 0; i < Math.min(3, plan.changes.size()); i++)
			text.append("\n• ").append(plan.changes.get(i));
		if (plan.changes.size() > 3)
			text.append("\n• ").append(Txt.n(R.plurals.home_more_changes, plan.changes.size() - 3));
		return text.toString();
	}

	private static String greeting() {
		int hour = LocalTime.now().getHour();
		return Txt.t(hour < 6 ? R.string.home_good_night : hour < 14 ? R.string.home_good_morning :
			hour < 21 ? R.string.home_good_afternoon : R.string.home_good_night);
	}
}
