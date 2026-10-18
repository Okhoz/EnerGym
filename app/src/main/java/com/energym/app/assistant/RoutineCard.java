package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.domain.*;

import java.util.Map;

/**
 * Resumen de una propuesta para el chat: una línea por ejercicio («3 × 10 · descanso 60 s») con un emoticono
 * según el tipo, en vez de la lista completa de campos de la prescripción.
 */
public final class RoutineCard {
	private RoutineCard() {
	}

	public static String text(Routine routine) {
		StringBuilder s = new StringBuilder("📋 ").append(routine.getTitle()).append("\n⏱️ ")
			.append(routine.getEstimatedMinutes()).append(" min · ")
			.append(Txt.n(R.plurals.card_exercises, routine.getItems().size())).append('\n');
		for (RoutineItem item : routine.getItems())
			s.append('\n').append(icon(item.getExercise().getMetricType())).append(' ')
				.append(item.getExercise().getName()).append("\n      ")
				.append(dose(item.getExercise().getMetricType(), item.getPrescription())).append('\n');
		return s.append('\n').append(Txt.t(R.string.card_accept_reject)).append('\n')
			.append(Txt.t(R.string.card_not_reviewed)).toString();
	}

	static String icon(MetricType type) {
		switch (type) {
			case STRENGTH:
				return "💪";
			case ISOMETRIC:
				return "🧱";
			case MOBILITY:
				return "🧘";
			case ENDURANCE:
				return "🏃";
			default:
				return "⚽";
		}
	}

	/**
	 * Dosis en una línea legible; los campos que falten simplemente no se muestran.
	 */
	public static String dose(MetricType type, Map<String, String> p) {
		StringBuilder s = new StringBuilder();
		switch (type) {
			case STRENGTH:
				s.append(p.get("sets")).append(" × ").append(p.get("repetitions"));
				if (positive(p.get("loadKg")))
					s.append(" · ").append(p.get("loadKg")).append(" kg");
				break;
			case ISOMETRIC:
				s.append(p.get("sets")).append(" × ").append(p.get("durationSeconds")).append(" s");
				break;
			case MOBILITY:
				s.append(p.get("repetitions")).append(" × ").append(p.get("durationSeconds")).append(" s");
				if (p.containsKey("side") && !"ambos".equals(p.get("side")))
					s.append(" · ").append(Txt.t(R.string.card_side, MetricValidator.choiceLabel(p.get("side"))));
				break;
			case ENDURANCE:
				s.append(p.get("timeMinutes")).append(" min");
				if (positive(p.get("distanceKm")))
					s.append(" · ").append(p.get("distanceKm")).append(" km");
				if (p.containsKey("intervals") && !"1".equals(p.get("intervals")))
					s.append(' ').append(Txt.t(R.string.card_in_blocks, p.get("intervals")));
				break;
			default:
				s.append(Txt.t(R.string.card_blocks, p.get("blocks"), p.get("durationMinutes")));
		}
		if (positive(p.get("restSeconds")))
			s.append(" · ").append(Txt.t(R.string.ses_rest_seconds, p.get("restSeconds")));
		return s.toString();
	}

	private static boolean positive(String value) {
		try {
			return value != null && Double.parseDouble(value.replace(',', '.')) > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
