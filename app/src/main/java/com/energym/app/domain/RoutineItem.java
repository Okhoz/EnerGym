package com.energym.app.domain;
import java.util.*;
public final class RoutineItem {
    private final Exercise exercise;
    private final Map<String, String> prescription;
    private final int estimatedMinutes;
    private final String justification;

    public RoutineItem(Exercise exercise, Map<String,String> prescription, int estimatedMinutes, String justification) {
        this.exercise = Objects.requireNonNull(exercise, "exercise");
        Objects.requireNonNull(prescription, "prescription");
        if (prescription.isEmpty() || prescription.containsKey(null) || prescription.containsValue(null))
            throw new IllegalArgumentException("Prescripción vacía o nula");
        if (estimatedMinutes < 1) throw new IllegalArgumentException("Duración no positiva");
        this.prescription = Collections.unmodifiableMap(new LinkedHashMap<>(prescription));
        this.estimatedMinutes = estimatedMinutes;
        this.justification = TrainingProfile.required(justification, "justification");
    }
    public Exercise getExercise() { return exercise; }
    /** Canonical keys are documented by RoutinePlanner; values contain numbers, not unit text. */
    public Map<String,String> getPrescription() { return prescription; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public String getJustification() { return justification; }
}
