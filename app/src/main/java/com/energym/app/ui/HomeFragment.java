package com.energym.app.ui;

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
		String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", NutritionFragment.SPANISH));
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
		MaterialButton open = action(rows, "📊 Ver tu progreso", true, () -> app.navigate(Screen.HISTORY));
		open.setContentDescription(getString(R.string.home_view_history));
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
			LinearLayout resume = card("Entreno a medias", draft.getRoutine().getTitle() + " · " +
				draft.getResults().size() + " de " + draft.getRoutine().getItems().size() + " ejercicios");
			primary(action(resume, "Continuar entreno", !app.sessions.busy, () -> {
				if (draft == app.sessions.session)
					app.navigate(Screen.SESSION);
				else
					app.resume(draft);
			}));
			return;
		}
		com.energym.app.domain.ProgressionEngine.Plan level = app.progression();
		if (level != null && app.routines.preview == null) {
			LinearLayout up = card("Sube de nivel", summary(level));
			primary(action(up, "Revisar progresión", !app.routines.busy, app::reviewProgression));
			action(up, "Repetir rutina actual", !app.sessions.busy, app::startSession);
			return;
		}
		if (app.routines.accepted != null) {
			LinearLayout ready = card("Tu entreno de hoy", app.routines.accepted.getTitle() + " · " +
				app.routines.accepted.getItems().size() + " ejercicios · " + app.routines.accepted.getEstimatedMinutes() +
				" min");
			primary(action(ready, "Empezar", !app.sessions.busy, app::startSession));
			return;
		}
		LinearLayout create = card("Tu entreno de hoy", "Aún no tienes rutina. Te preparamos una con tu perfil.");
		primary(action(create, "Crear mi rutina", !app.routines.busy, () -> app.navigate(Screen.TRAINING)));
	}

	private void food() {
		if (state.nutrition == null)
			return;
		double[] today = state.nutrition.totals(state.nutrition.today());
		BodyProfile.Targets targets = state.nutrition.targets();
		LinearLayout food = card(getString(R.string.nutrition), targets == null ?
			"Escanea tus alimentos y controla calorías y macros." :
			"Hoy llevas " + Math.round(today[0]) + " de " + Math.round(targets.kcal) + " kcal.");
		action(food, targets == null ? "Empezar" : "Añadir comida", () -> app.navigate(Screen.NUTRITION));
	}

	static String summary(com.energym.app.domain.ProgressionEngine.Plan plan) {
		StringBuilder text = new StringBuilder("Tu última sesión permite avanzar:");
		for (int i = 0; i < Math.min(3, plan.changes.size()); i++)
			text.append("\n• ").append(plan.changes.get(i));
		if (plan.changes.size() > 3)
			text.append("\n• y ").append(plan.changes.size() - 3).append(" cambios más");
		return text.toString();
	}

	private static String greeting() {
		int hour = LocalTime.now().getHour();
		return hour < 6 ? "Buenas noches" : hour < 14 ? "Buenos días" : hour < 21 ? "Buenas tardes" : "Buenas noches";
	}
}
