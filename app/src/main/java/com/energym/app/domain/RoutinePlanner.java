package com.energym.app.domain;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.util.*;

public final class RoutinePlanner {
	private final CompatibilityEngine engine = new CompatibilityEngine();

	/**
	 * Propuesta local por reglas; sus ejemplos no tienen evaluación individual ni revisión profesional.
	 */
	public Routine create(TrainingProfile profile, List<Exercise> catalog) {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(catalog, "catalog");
		List<Exercise> compatible = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (Exercise exercise : catalog) {
			if (engine.isCompatible(exercise, profile) && ids.add(exercise.getId()))
				compatible.add(exercise);
		}
		if (profile.getCategory() == TrainingProfile.Category.HOME_EQUIPMENT ||
			profile.getCategory() == TrainingProfile.Category.GYM) {
			compatible.sort(
				Comparator.comparingInt((Exercise exercise) -> exercise.getRequiredResources().size()).reversed());
		}
		// Cada ejemplo de fuerza o movilidad reserva cinco minutos, con transiciones y descansos breves.
		// El ejemplo de resistencia usa un bloque continuo y reserva tres minutos para transiciones.
		List<RoutineItem> items = new ArrayList<>();
		int remaining = profile.getDurationMinutes();
		for (Exercise exercise : compatible) {
			if (items.size() == 5 || remaining < 5)
				break;
			int minutes = exercise.getMetricType() == MetricType.ENDURANCE ? Math.max(2, remaining - 3) : 5;
			items.add(new RoutineItem(exercise, prescription(exercise.getMetricType(), minutes), minutes,
				"Coincide con el objetivo, entorno, deporte y recursos declarados. Ejemplo sin revisión profesional."));
			remaining -= minutes;
			if (exercise.getMetricType() == MetricType.ENDURANCE)
				break;
		}
		String explanation = items.isEmpty() ?
			Txt.t(R.string.rp_no_exercises) : Txt.t(R.string.rp_local_rules);
		return new Routine(UUID.randomUUID().toString(), 0, Txt.t(R.string.rp_title), profile, items, explanation,
			new RoutineMetadata(RoutineMetadata.Origin.LOCAL_RULES, true));
	}

	/**
	 * Propuesta local según lo que pide la persona (zonas, intensidad, número de ejercicios): equilibrada, con
	 * calentamiento y distinta en cada petición gracias a la semilla. Sin petición concreta, cuerpo completo variado.
	 */
	public Routine create(TrainingProfile profile, List<Exercise> catalog, RoutineRequest request, long seed) {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(catalog, "catalog");
		List<Exercise> compatible = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (Exercise exercise : catalog)
			if (engine.isCompatible(exercise, profile) && ids.add(exercise.getId()))
				compatible.add(exercise);
		int max = request.count > 0 ? request.count : request.intensity == RoutineRequest.Intensity.EASY ? 4 : 5;
		List<RoutineItem> items = new ArrayList<>();
		int remaining = profile.getDurationMinutes();
		for (Exercise exercise : request.compose(compatible, seed, max)) {
			if (remaining < 3)
				break;
			boolean endurance = exercise.getMetricType() == MetricType.ENDURANCE;
			int minutes = endurance ? Math.max(2, remaining - 3) : exercise.getMetricType() == MetricType.MOBILITY ? 3 : 5;
			if (!endurance && minutes > remaining)
				break;
			Map<String, String> values = prescription(exercise.getMetricType(), minutes);
			if (exercise.getMetricType() == MetricType.STRENGTH && request.intensity != RoutineRequest.Intensity.NORMAL) {
				boolean hard = request.intensity == RoutineRequest.Intensity.HARD;
				values.put("sets", hard ? "3" : "2");
				values.put("repetitions", hard ? "12" : "8");
				values.put("restSeconds", hard ? "45" : "75");
			}
			Set<RoutineRequest.Area> worked = RoutineRequest.areas(exercise);
			List<String> labels = new ArrayList<>();
			for (RoutineRequest.Area a : worked)
				labels.add(a.label);
			items.add(new RoutineItem(exercise, values, minutes, (labels.isEmpty() ? "Encaja con tu perfil" :
				"Trabaja " + String.join(", ", labels)) + (request.areas.isEmpty() ? "." : ", como pediste.") +
				" Ejemplo sin revisión profesional."));
			remaining -= minutes;
			if (endurance)
				break;
		}
		String title = request.title();
		String explanation = items.isEmpty() ?
			Txt.t(R.string.rp_no_exercises_for, request.areas.isEmpty() ? Txt.t(R.string.rp_that_type) :
				request.title().toLowerCase(Txt.locale())) :
			Txt.t(R.string.rp_local_rules_request);
		return new Routine(UUID.randomUUID().toString(), 0, title.isEmpty() ? Txt.t(R.string.df_cuerpo_completo) : title, profile,
			items, explanation, new RoutineMetadata(RoutineMetadata.Origin.LOCAL_RULES, true));
	}

	private Map<String, String> prescription(MetricType type, int minutes) {
		Map<String, String> values = new LinkedHashMap<>();
		switch (type) {
			case STRENGTH:
				values.put("sets", "2");
				values.put("repetitions", "8");
				values.put("loadKg", "0");
				values.put("restSeconds", "60");
				break;
			case ISOMETRIC:
				values.put("sets", "2");
				values.put("durationSeconds", "20");
				values.put("restSeconds", "60");
				break;
			case ENDURANCE:
				values.put("timeMinutes", Integer.toString(minutes));
				values.put("intervals", "1");
				break;
			case MOBILITY:
				values.put("durationSeconds", "30");
				values.put("repetitions", "4");
				values.put("side", "ambos");
				break;
			case SPORT_PRACTICE:
				values.put("blocks", "2");
				values.put("durationMinutes", Integer.toString(minutes));
				break;
			default:
				throw new IllegalArgumentException("Métrica desconocida");
		}
		return values;
	}
}

