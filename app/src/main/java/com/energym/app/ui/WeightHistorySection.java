package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.view.View;
import android.widget.LinearLayout;

import com.energym.app.R;
import com.energym.app.nutrition.BodyProfile;
import com.energym.app.nutrition.NutritionController;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Vista de sólo lectura del peso confirmado; paginar no modifica ni reconstruye el formulario. */
final class WeightHistorySection {
	private static final int PAGE_SIZE = 10;
	private static DateTimeFormatter date() {
		return DateTimeFormatter.ofPattern(Txt.t(R.string.pattern_day_month_year), Txt.locale());
	}

	private WeightHistorySection() {}

	static void render(ScreenFragment screen, NutritionController nutrition) {
		if (nutrition == null || nutrition.owner() == null) return;
		long scope = nutrition.scopeRevision();
		List<Map.Entry<LocalDate, Double>> history = new ArrayList<>(nutrition.weightHistory().entrySet());
		LinearLayout section = screen.card(Txt.t(R.string.wh_title), Txt.t(R.string.wh_body));
		section.setTag("health_weight_history");

		BodyProfile current = nutrition.effectiveBody();
		BodyProfile.Targets targets = nutrition.targets();
		LocalDate weightDay = nutrition.targetWeightDay();
		if (current != null) {
			screen.text(section, Txt.t(targets == null ? R.string.wh_saved : R.string.wh_used,
				NutritionController.format(current.weightKg),
				weightDay == null ? Txt.t(R.string.wh_profile_data) : weightDay.format(date())), R.style.EnerGym_Body, 4)
				.setTag("health_weight_reference");
			if (targets != null)
				screen.text(section, Txt.t(R.string.wh_targets, Math.round(targets.kcal), Math.round(targets.protein),
					Math.round(targets.carbs), Math.round(targets.fat)), R.style.EnerGym_FieldCaption, 8)
					.setTag("health_weight_targets");
		}
		if (history.isEmpty()) {
			screen.text(section, Txt.t(R.string.wh_empty), R.style.EnerGym_Body, 12)
				.setTag("health_weight_empty");
			return;
		}

		LinearLayout records = new LinearLayout(screen.requireContext());
		records.setOrientation(LinearLayout.VERTICAL);
		records.setTag("health_weight_records");
		records.setVisibility(View.GONE);
		// El historial se despliega a petición para dejar el formulario a mano.
		MaterialButton[] toggle = {null};
		toggle[0] = screen.action(section, Txt.t(R.string.wh_show, history.size()), true, () -> {
			if (scope != nutrition.scopeRevision()) return;
			boolean expanded = records.getVisibility() == View.VISIBLE;
			records.setVisibility(expanded ? View.GONE : View.VISIBLE);
			toggle[0].setText(expanded ? Txt.t(R.string.wh_show, history.size()) : Txt.t(R.string.wh_hide));
		});
		toggle[0].setTag("health_weight_toggle");
		screen.add(section, records, 12);
		int[] page = {0};
		Runnable[] refresh = {() -> {}};
		refresh[0] = () -> {
			if (scope != nutrition.scopeRevision()) return;
			records.removeAllViews();
			int first = page[0] * PAGE_SIZE;
			int end = Math.min(first + PAGE_SIZE, history.size());
			screen.text(records, Txt.t(R.string.wh_range, first + 1, end, history.size()),
				R.style.EnerGym_FieldCaption, 0);
			for (int index = first; index < end; index++) {
				Map.Entry<LocalDate, Double> entry = history.get(index);
				screen.text(records, entry.getKey().format(date()) + " · " + NutritionController.format(entry.getValue()) + " kg",
					R.style.EnerGym_Body, 8).setTag("health_weight_" + entry.getKey());
			}
			if (history.size() > PAGE_SIZE) {
				LinearLayout navigation = screen.columns(records, 12);
				screen.compact(navigation, Txt.t(R.string.wh_newer), first > 0, () -> {
					if (page[0] > 0) {
						page[0]--;
						refresh[0].run();
					}
				}).setTag("health_weight_newer");
				screen.compact(navigation, Txt.t(R.string.wh_older), end < history.size(), () -> {
					if ((page[0] + 1) * PAGE_SIZE < history.size()) {
						page[0]++;
						refresh[0].run();
					}
				}).setTag("health_weight_older");
			}
		};
		refresh[0].run();
	}
}
