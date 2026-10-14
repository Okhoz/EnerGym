package com.energym.app.domain;
import java.util.*;
/** MODEL inmutable: snapshot de rutina y valores realizados; no contiene Android ni coordinación de pantalla. */
public final class Session {
    public enum Status { DRAFT, FINISHED }
    private final Routine routine;
    private final String syncId;
    private final Map<String,Map<String,String>> results;
    private final long startedAt, completedAt;
    private final Status status;
    /** Inicia un borrador con UUID estable. El repositorio comprueba que la rutina pertenece a la cuenta. */
    public Session(Routine routine) {
        this(UUID.randomUUID().toString(), routine, Collections.emptyMap(), System.currentTimeMillis(), 0, Status.DRAFT);
    }
    /** Reconstrucción de un snapshot: copia mapas anidados y valida fechas e IDs de ejercicios. */
    public Session(String syncId, Routine routine, Map<String,Map<String,String>> results,
            long startedAt, long completedAt, Status status) {
        this.syncId = TrainingProfile.required(syncId, "syncId");
        this.routine = Objects.requireNonNull(routine, "routine");
        this.status = Objects.requireNonNull(status, "status");
        if (startedAt <= 0 || (status == Status.DRAFT && completedAt != 0)
                || (status == Status.FINISHED && completedAt < startedAt)) throw new IllegalArgumentException("Fechas de sesión inválidas");
        this.startedAt = startedAt; this.completedAt = completedAt;
        Map<String,Map<String,String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String,Map<String,String>> entry : Objects.requireNonNull(results, "results").entrySet()) {
            String id = TrainingProfile.required(entry.getKey(), "exerciseId");
            boolean exists = false;
            for (RoutineItem item : routine.getItems()) if (item.getExercise().getId().equals(id)) exists = true;
            if (!exists) throw new IllegalArgumentException("Ejercicio fuera del snapshot");
            Map<String,String> values = new LinkedHashMap<>();
            for (Map.Entry<String,String> value : Objects.requireNonNull(entry.getValue(), "result").entrySet()) {
                values.put(TrainingProfile.required(value.getKey(), "metric"), Objects.requireNonNull(value.getValue(), "value"));
            }
            copy.put(id, Collections.unmodifiableMap(values));
        }
        this.results = Collections.unmodifiableMap(copy);
    }
    /** Copia editable del borrador; conserva identidad y snapshot original para recuperarlo después. */
    public Session withResult(String exerciseId, Map<String,String> result) {
        if (status != Status.DRAFT) throw new IllegalStateException("Sesión finalizada");
        Map<String,Map<String,String>> copy = new LinkedHashMap<>(results); copy.put(exerciseId, result);
        return new Session(syncId, routine, copy, startedAt, 0, status);
    }
    /** Finalización monotónica e idempotente: no cambia el primer instante de finalización. */
    public Session finishedAt(long time) {
        return status == Status.FINISHED ? this : new Session(syncId, routine, results, startedAt, Math.max(startedAt, time), Status.FINISHED);
    }
    /** Utilizado para remapear el ID local después de importar, conservando el contenido histórico. */
    public Session withRoutineSnapshot(Routine snapshot) {
        return new Session(syncId, snapshot, results, startedAt, completedAt, status);
    }
    public String getSyncId() { return syncId; }
    public Routine getRoutine() { return routine; }
    public Map<String,Map<String,String>> getResults() { return results; }
    public Status getStatus() { return status; }
    public long getStartedAt() { return startedAt; }
    public long getCompletedAt() { return completedAt; }
}
