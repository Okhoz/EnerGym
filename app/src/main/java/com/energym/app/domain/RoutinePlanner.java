package com.energym.app.domain;
import java.util.*;
public final class RoutinePlanner {
    private final CompatibilityEngine engine = new CompatibilityEngine();

    /** Local deterministic example plan, not an individually evaluated or professional program. */
    public Routine create(TrainingProfile profile, List<Exercise> catalog) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(catalog, "catalog");
        List<Exercise> compatible = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Exercise exercise : catalog) {
            if (engine.isCompatible(exercise, profile) && ids.add(exercise.getId())) compatible.add(exercise);
        }
        if (profile.getCategory() == TrainingProfile.Category.HOME_EQUIPMENT
                || profile.getCategory() == TrainingProfile.Category.GYM) {
            compatible.sort(Comparator.comparingInt((Exercise exercise) -> exercise.getRequiredResources().size()).reversed());
        }
        // Five minutes per strength/mobility example includes transitions and short rests.
        // An endurance example gets one continuous block, leaving three minutes for transitions.
        List<RoutineItem> items = new ArrayList<>();
        int remaining = profile.getDurationMinutes();
        for (Exercise exercise : compatible) {
            if (items.size() == 5 || remaining < 5) break;
            int minutes = exercise.getMetricType() == MetricType.ENDURANCE ? Math.max(2, remaining - 3) : 5;
            items.add(new RoutineItem(exercise, prescription(exercise.getMetricType(), minutes), minutes,
                    "Coincide con el objetivo, entorno, deporte y recursos declarados. Ejemplo sin revisión profesional."));
            remaining -= minutes;
            if (exercise.getMetricType() == MetricType.ENDURANCE) break;
        }
        String explanation = items.isEmpty()
                ? "No hay ejercicios compatibles con este objetivo, entorno, recursos y cobertura. Ajusta el perfil; "
                    + "el contenido especializado puede estar pendiente de validación."
                : "Propuesta local por reglas. Ejemplos sin revisión profesional; adapta la intensidad a tu situación. "
                    + "No utiliza modelos de IA ni requiere conexión. La duración estimada incluye descansos y transiciones.";
        return new Routine(UUID.randomUUID().toString(), 0, "Propuesta de entrenamiento", profile, items, explanation);
    }

    private Map<String, String> prescription(MetricType type, int minutes) {
        Map<String, String> values = new LinkedHashMap<>();
        switch (type) {
            case STRENGTH:
                values.put("sets", "2"); values.put("repetitions", "8");
                values.put("loadKg", "0"); values.put("restSeconds", "60"); break;
            case ISOMETRIC:
                values.put("sets", "2"); values.put("durationSeconds", "20");
                values.put("restSeconds", "60"); break;
            case ENDURANCE:
                values.put("timeMinutes", Integer.toString(minutes)); values.put("intervals", "1"); break;
            case MOBILITY:
                values.put("durationSeconds", "30"); values.put("repetitions", "4");
                values.put("side", "ambos"); break;
            case SPORT_PRACTICE:
                values.put("blocks", "2"); values.put("durationMinutes", Integer.toString(minutes)); break;
            default: throw new IllegalArgumentException("Métrica desconocida");
        }
        return values;
    }
}

