package com.energym.app.domain;

import java.util.*;

/**
 * MODELO del entreno libre al estilo FitNotes: sin rutina previa, se añaden ejercicios sobre la marcha. Por la
 * integridad del respaldo cada ejercicio añadido crea una versión nueva (manual) de la rutina del día; la sesión
 * se pasa a esa versión conservando lo apuntado.
 */
public final class FreeWorkout {
	public static final String PREFIX = "Entreno libre";

	private FreeWorkout() {
	}

	public static boolean is(Routine routine) {
		return routine != null && routine.getTitle().startsWith(PREFIX);
	}

	/** Perfil del día: el de la persona con margen de tiempo para ir añadiendo ejercicios. */
	public static TrainingProfile profile(TrainingProfile base) {
		return base.toBuilder().durationMinutes(240).build();
	}

	/** Ejercicios que se pueden añadir hoy: compatibles con tu lugar, material y objetivo, y aún no añadidos. */
	public static List<Exercise> candidates(TrainingProfile base, List<Exercise> catalog, Routine current) {
		TrainingProfile p = profile(base);
		Set<String> used = new HashSet<>();
		if (current != null)
			for (RoutineItem i : current.getItems())
				used.add(i.getExercise().getId());
		CompatibilityEngine engine = new CompatibilityEngine();
		List<Exercise> result = new ArrayList<>();
		for (Exercise e : catalog)
			if (engine.isCompatible(e, p) && !used.contains(e.getId()))
				result.add(e);
		return result;
	}

	/** Rutina del día con un ejercicio más (o la primera si {@code current} es null). */
	public static Routine with(TrainingProfile base, Routine current, Exercise added, String today) {
		TrainingProfile p = current == null ? profile(base) : current.getProfile();
		List<RoutineItem> items = new ArrayList<>(current == null ? Collections.emptyList() : current.getItems());
		boolean endurance = added.getMetricType() == MetricType.ENDURANCE;
		items.add(new RoutineItem(added, prescription(added.getMetricType()), endurance ? 20 : 5,
			"Añadido por ti en el entreno libre."));
		String title = current == null ? PREFIX + " · " + today : current.getTitle();
		return new Routine(UUID.randomUUID().toString(), 0, title, p, items,
			"Entreno libre: añades los ejercicios sobre la marcha y apuntas cada serie.",
			new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
	}

	static Map<String, String> prescription(MetricType type) {
		Map<String, String> v = new LinkedHashMap<>();
		switch (type) {
			case STRENGTH:
				v.put("sets", "3");
				v.put("repetitions", "10");
				v.put("loadKg", "0");
				v.put("restSeconds", "90");
				break;
			case ISOMETRIC:
				v.put("sets", "3");
				v.put("durationSeconds", "30");
				v.put("restSeconds", "60");
				break;
			case ENDURANCE:
				v.put("timeMinutes", "20");
				v.put("intervals", "1");
				break;
			case MOBILITY:
				v.put("durationSeconds", "30");
				v.put("repetitions", "8");
				v.put("side", "ambos");
				break;
			default:
				v.put("blocks", "2");
				v.put("durationMinutes", "4");
				break;
		}
		return v;
	}
}
