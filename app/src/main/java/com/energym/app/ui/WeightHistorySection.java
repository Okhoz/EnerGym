package com.energym.app.ui;

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
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "ES"));

	private WeightHistorySection() {}

	static void render(ScreenFragment screen, NutritionController nutrition) {
		if (nutrition == null || nutrition.owner() == null) return;
		long scope = nutrition.scopeRevision();
		List<Map.Entry<LocalDate, Double>> history = new ArrayList<>(nutrition.weightHistory().entrySet());
		LinearLayout section = screen.card("Historial de peso",
			"Al guardar un peso en tus datos o anotarlo en Nutrición, aparecerá aquí. Si has completado tus datos, se actualizarán tus objetivos. " +
				"Se guarda un registro por día; otra anotación del mismo día actualiza esa entrada.");
		section.setTag("health_weight_history");

		BodyProfile current = nutrition.effectiveBody();
		BodyProfile.Targets targets = nutrition.targets();
		LocalDate weightDay = nutrition.targetWeightDay();
		if (current != null) {
			screen.text(section, (targets == null ? "Peso guardado: " : "Peso usado para tus objetivos: ") +
				NutritionController.format(current.weightKg) + " kg · " +
				(weightDay == null ? "datos del perfil" : weightDay.format(DATE)), R.style.EnerGym_Body, 4)
				.setTag("health_weight_reference");
			if (targets != null)
				screen.text(section, "Objetivos actuales: " + Math.round(targets.kcal) + " kcal/día\n" +
					"Proteínas " + Math.round(targets.protein) + " g · Hidratos " + Math.round(targets.carbs) +
					" g · Grasas " + Math.round(targets.fat) + " g", R.style.EnerGym_FieldCaption, 8)
					.setTag("health_weight_targets");
		}
		if (history.isEmpty()) {
			screen.text(section, "Todavía no hay pesos registrados.", R.style.EnerGym_Body, 12)
				.setTag("health_weight_empty");
			return;
		}

		LinearLayout records = new LinearLayout(screen.requireContext());
		records.setOrientation(LinearLayout.VERTICAL);
		records.setTag("health_weight_records");
		records.setVisibility(View.GONE);
		// El historial se despliega a petición para dejar el formulario a mano.
		MaterialButton[] toggle = {null};
		toggle[0] = screen.action(section, "Ver historial (" + history.size() + ")", true, () -> {
			if (scope != nutrition.scopeRevision()) return;
			boolean expanded = records.getVisibility() == View.VISIBLE;
			records.setVisibility(expanded ? View.GONE : View.VISIBLE);
			toggle[0].setText(expanded ? "Ver historial (" + history.size() + ")" : "Ocultar historial");
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
			screen.text(records, "Registros " + (first + 1) + "–" + end + " de " + history.size(),
				R.style.EnerGym_FieldCaption, 0);
			for (int index = first; index < end; index++) {
				Map.Entry<LocalDate, Double> entry = history.get(index);
				screen.text(records, entry.getKey().format(DATE) + " · " + NutritionController.format(entry.getValue()) + " kg",
					R.style.EnerGym_Body, 8).setTag("health_weight_" + entry.getKey());
			}
			if (history.size() > PAGE_SIZE) {
				LinearLayout navigation = screen.columns(records, 12);
				screen.compact(navigation, "Más recientes", first > 0, () -> {
					if (page[0] > 0) {
						page[0]--;
						refresh[0].run();
					}
				}).setTag("health_weight_newer");
				screen.compact(navigation, "Más antiguos", end < history.size(), () -> {
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
