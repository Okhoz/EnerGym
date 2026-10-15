package com.energym.app.ui;

import android.widget.LinearLayout;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.nutrition.*;

import java.util.*;

/**
 * VISTA del perfil físico: datos para estimar mantenimiento y objetivos diarios. Validación en el controlador.
 */
public final class BodyFragment extends ScreenFragment {
	public BodyFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		NutritionController n = state.nutrition;
		heading("NUTRICIÓN", "Tus datos físicos",
			"Para calcular tus calorías y macros diarios.");
		if (n == null || app.owner() == null) {
			card("Entra para empezar", "");
			return;
		}
		if (n.bodyDraft.isEmpty())
			n.fillBody();
		Map<String, String> d = n.bodyDraft;
		LinearLayout basics = card("Sobre ti", "");
		choice(basics, "sex", "Sexo biológico (para la fórmula)", Arrays.asList("FEMALE", "MALE"),
			Arrays.asList("Mujer", "Hombre"), d.get("sex"), v -> d.put("sex", v));
		textField(basics, "age", "Edad (años)", d.get("age"), true, true, v -> d.put("age", v));
		textField(basics, "heightCm", "Altura (cm)", d.get("heightCm"), true, false, v -> d.put("heightCm", v));
		textField(basics, "weightKg", "Peso actual (kg)", d.get("weightKg"), true, false, v -> d.put("weightKg", v));

		LinearLayout plan = card("Actividad y objetivo", "");
		choice(plan, "activity", "Actividad habitual",
			Arrays.asList("SEDENTARY", "LIGHT", "MODERATE", "HIGH", "VERY_HIGH"),
			Arrays.asList("Sedentaria (poco movimiento)", "Ligera (1–3 entrenos/semana)",
				"Moderada (3–5 entrenos/semana)", "Alta (6–7 entrenos/semana)", "Muy alta (trabajo físico + entreno)"),
			d.get("activity"), v -> d.put("activity", v));
		choice(plan, "goal", "Objetivo", Arrays.asList("LOSE", "MAINTAIN", "GAIN"),
			Arrays.asList("Perder grasa (déficit ~15 %)", "Mantener peso", "Ganar músculo (superávit ~10 %)"),
			d.get("goal"), v -> d.put("goal", v));
		textField(plan, "allergies", "Alergias o intolerancias (opcional)", d.get("allergies"), false, false,
			v -> d.put("allergies", v));

		card("Cómo se calcula",
			"Metabolismo basal con Mifflin-St Jeor × factor de actividad, ajustado a tu objetivo. Proteína 1,6–2 g/kg, " +
				"grasa mínima 0,8 g/kg y el resto en hidratos. Es una estimación: si tienes una condición médica, " +
				"consulta con un profesional.");
		primary(action(rows, "Guardar mis datos", !n.busy, () -> n.saveBody(() -> app.navigate(Screen.NUTRITION))));
	}
}
