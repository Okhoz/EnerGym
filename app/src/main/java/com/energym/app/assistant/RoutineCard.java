package com.energym.app.assistant;

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
			.append(routine.getEstimatedMinutes()).append(" min · ").append(routine.getItems().size())
			.append(routine.getItems().size() == 1 ? " ejercicio" : " ejercicios").append('\n');
		for (RoutineItem item : routine.getItems())
			s.append('\n').append(icon(item.getExercise().getMetricType())).append(' ')
				.append(item.getExercise().getName()).append("\n      ")
				.append(dose(item.getExercise().getMetricType(), item.getPrescription())).append('\n');
		return s.append("\n✅ «Aceptar» la guarda en Entreno · «Rechazar» para pedir otra.\n")
			.append("ℹ️ Ejemplo sin revisión profesional.").toString();
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
	static String dose(MetricType type, Map<String, String> p) {
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
					s.append(" · lado ").append(p.get("side"));
				break;
			case ENDURANCE:
				s.append(p.get("timeMinutes")).append(" min");
				if (positive(p.get("distanceKm")))
					s.append(" · ").append(p.get("distanceKm")).append(" km");
				if (p.containsKey("intervals") && !"1".equals(p.get("intervals")))
					s.append(" en ").append(p.get("intervals")).append(" bloques");
				break;
			default:
				s.append(p.get("blocks")).append(" bloques × ").append(p.get("durationMinutes")).append(" min");
		}
		if (positive(p.get("restSeconds")))
			s.append(" · descanso ").append(p.get("restSeconds")).append(" s");
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
