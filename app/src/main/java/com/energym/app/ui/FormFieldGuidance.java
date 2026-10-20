package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.i18n.Txt;
import java.util.Locale;

/** Texto de ayuda de la vista: los ejemplos nunca forman parte del valor ni de la validación. */
public final class FormFieldGuidance {
	private FormFieldGuidance() {
	}

	/** Ejemplos sin unidades para los campos numéricos; la etiqueta conserva la unidad. */
	public static String placeholder(String key, String label, boolean number, boolean integer) {
		String name = key.startsWith("manual_") ? key.substring("manual_".length()) : key;
		if (name.startsWith("detail."))
			name = name.substring("detail.".length());
		if (name.endsWith("_search") || name.equals("search"))
			return example(Txt.t(R.string.ff_ex_sentadilla_o_remo));
		switch (name) {
			case "age": return example("30");
			case "heightCm": return example("175");
			case "weightKg":
			case "weight": return example("75.5");
			case "daysPerWeek": return example("3");
			case "duration":
			case "durationMinutes": return example("45");
			case "durationHours":
			case "manual_duration": return example("0:45");
			case "estimatedMinutes": return example("10");
			case "level": return example(Txt.t(R.string.ff_ex_principiante));
			case "experience": return example(Txt.t(R.string.ff_ex_6_meses_de_entrenamiento_de_fuerza));
			case "space": return example(Txt.t(R.string.ff_ex_una_habitacion_despejada));
			case "preferences": return example(Txt.t(R.string.ff_ex_prefiero_ejercicios_con_mancuernas));
			case "limitations": return example(Txt.t(R.string.ff_ex_evitar_saltos));
			case "priority": return example(Txt.t(R.string.ff_ex_espalda_o_sentadilla));
			case "maxPushups": return example("12");
			case "maxPullups": return example("3");
			case "weeklyKm": return example("20");
			case "bestTime": return example(Txt.t(R.string.ff_ex_5_km_en_28_min));
			case "ftp": return example("200");
			case "sessionMeters": return example("1000");
			case "matchesPerWeek": return example("2");
			case "elevation": return example("500");
			case "grade": return example("6a");
			case "sportName": return example(Txt.t(R.string.ff_ex_badminton));
			case "title": return example(Txt.t(R.string.ff_ex_mi_rutina_de_fuerza));
			case "sets":
			case "blocks": return example("3");
			case "repetitions": return example("10");
			case "loadKg": return example("20");
			case "restSeconds": return example("60");
			case "durationSeconds": return example("30");
			case "timeMinutes": return example("20");
			case "intervals": return example("4");
			case "distanceKm": return example("5");
			case "effort": return example("7");
			case "amount": return example("150");
			case "name": return example(Txt.t(R.string.ff_ex_yogur_natural));
			case "kcal100": return example("65");
			case "protein100": return example("4.5");
			case "carbs100": return example("5");
			case "fat100": return example("3");
			case "allergies": return example(Txt.t(R.string.ff_ex_cacahuetes_lactosa));
			case "dislikes": return example(Txt.t(R.string.ff_ex_champinones_coliflor));
			case "budget": return example("250");
			case "barcode": return example("8412345678905");
			case "assistant_endpoint": return example(Txt.t(R.string.ff_ex_https_ia_tudominio_es));
			case "assistant_pairing": return Txt.t(R.string.ff_paste_pairing);
			case "goal_target": return goalExample(label);
			case "goal_name": return example(Txt.t(R.string.ff_ex_goal_name));
			case "goal_unit": return example(Txt.t(R.string.ff_ex_goal_unit));
			case "goal_start": return example("0");
			case "goal_custom_target": return example("12");
			case "goal_progress": return example("3");
			default: return number ? (integer ? example("3") : example("10.5")) : Txt.t(R.string.ff_type_answer);
		}
	}

	private static String example(String value) {
		// Los decimales con la coma o el punto de cada idioma («75,5» / «75.5»); el campo acepta los dos.
		return Txt.t(R.string.ff_example, value.matches("\\d+\\.\\d+") ? Txt.decimal(value) : value);
	}

	private static String goalExample(String label) {
		String text = label.toLowerCase(Locale.ROOT);
		if (text.contains("sesiones")) return example("3");
		if (text.contains("min")) return example("150");
		if (text.contains("km")) return example("20");
		if (text.contains("kg") && (text.contains("semana") || text.contains("week"))) return example("5000");
		return example("70");
	}

	/** La condición opcional permanece visible aunque desaparezca el ejemplo al escribir. */
	public static String profileLabel(String key, String label) {
		if (key.equals("detail.grade")) label = Txt.t(R.string.ff_grade);
		if (key.equals("detail.ftp")) label = Txt.t(R.string.ff_ftp);
		boolean optional = key.startsWith("detail.") || key.equals("experience") ||
			key.equals("space") || key.equals("preferences") || key.equals("limitations");
		String word = Txt.t(R.string.ff_optional);
		return optional && !label.toLowerCase(Locale.ROOT).contains(word) ? label + " (" + word + ")" : label;
	}

	/** Ayudas solo para el perfil; las métricas de sesión comparten claves, pero no sus límites. */
	public static String profileHelper(String key) {
		switch (key) {
			case "sportId": return Txt.t(R.string.ff_help_sportid);
			case "category": return Txt.t(R.string.ff_help_category);
			case "focus": return Txt.t(R.string.ff_help_focus);
			case "environment": return Txt.t(R.string.ff_help_environment);
			case "goal": return Txt.t(R.string.ff_help_goal);
			case "daysPerWeek": return Txt.t(R.string.ff_help_daysperweek);
			case "durationMinutes": return Txt.t(R.string.ff_help_durationminutes);
			case "durationHours": return Txt.t(R.string.ff_help_durationhours);
			case "level": return Txt.t(R.string.ff_help_level);
			case "experience": return Txt.t(R.string.ff_help_experience);
			case "space": return Txt.t(R.string.ff_help_space);
			case "preferences": return Txt.t(R.string.ff_help_preferences);
			case "limitations": return Txt.t(R.string.ff_help_limitations);
			case "historyConsent": return Txt.t(R.string.ff_help_historyconsent);
			case "detail.split": return Txt.t(R.string.ff_help_d_split);
			case "detail.priority": return Txt.t(R.string.ff_help_d_priority);
			case "detail.skill": return Txt.t(R.string.ff_help_d_skill);
			case "detail.maxPushups":
			case "detail.maxPullups": return Txt.t(R.string.ff_help_d_maxpullups);
			case "detail.format": return Txt.t(R.string.ff_help_d_format);
			case "detail.olympic": return Txt.t(R.string.ff_help_d_olympic);
			case "detail.raceGoal": return Txt.t(R.string.ff_help_d_racegoal);
			case "detail.weeklyKm": return Txt.t(R.string.ff_help_d_weeklykm);
			case "detail.bestTime": return Txt.t(R.string.ff_help_d_besttime);
			case "detail.bike": return Txt.t(R.string.ff_help_d_bike);
			case "detail.ftp": return Txt.t(R.string.ff_help_d_ftp);
			case "detail.stroke": return Txt.t(R.string.ff_help_d_stroke);
			case "detail.pool": return Txt.t(R.string.ff_help_d_pool);
			case "detail.sessionMeters": return Txt.t(R.string.ff_help_d_sessionmeters);
			case "detail.position": return Txt.t(R.string.ff_help_d_position);
			case "detail.matchesPerWeek": return Txt.t(R.string.ff_help_d_matchesperweek);
			case "detail.terrain": return Txt.t(R.string.ff_help_d_terrain);
			case "detail.elevation": return Txt.t(R.string.ff_help_d_elevation);
			case "detail.pack": return Txt.t(R.string.ff_help_d_pack);
			case "detail.style": return Txt.t(R.string.ff_help_d_style);
			case "detail.grade": return Txt.t(R.string.ff_help_d_grade);
			case "detail.competes": return Txt.t(R.string.ff_help_d_competes);
			case "detail.area": return Txt.t(R.string.ff_help_d_area);
			case "detail.modality": return Txt.t(R.string.ff_help_d_modality);
			case "detail.side": return Txt.t(R.string.ff_help_d_side);
			case "detail.hand": return Txt.t(R.string.ff_help_d_hand);
			case "detail.backhand": return Txt.t(R.string.ff_help_d_backhand);
			case "detail.surface": return Txt.t(R.string.ff_help_d_surface);
			case "detail.sportName": return Txt.t(R.string.ff_help_d_sportname);
			default: return key.startsWith("detail.") ? Txt.t(R.string.ff_help_detail) : "";
		}
	}
}
