package com.energym.app.data;
import com.energym.app.domain.*;
import java.util.*;

/** MODEL: borradores duraderos y finalización idempotente, sin depender del catálogo vigente. */
public final class SessionRepository {
    private final EnerGymDatabase database;
    private final RoutineRepository routines;
    public SessionRepository(EnerGymDatabase database) { this.database=Objects.requireNonNull(database, "database"); routines = new RoutineRepository(database); }
    /** Guarda valores realizados. Un borrador antiguo nunca resucita una sesión finalizada. */
    public long saveDraft(Session session) {
        Objects.requireNonNull(session, "session");
        if (session.getStatus() != Session.Status.DRAFT) throw new IllegalArgumentException("Se requiere borrador");
        return database.transaction(() -> {
            EnerGymDatabase.Row old = database.find(SyncRecord.Kind.SESSION, session.getSyncId());
            if (old != null && SnapshotCodec.decodeSession(old.record.getPayload()).getStatus() == Session.Status.FINISHED) return old.id;
            Routine accepted = routines.load(session.getRoutine().getVersionId());
            if (accepted == null || !SnapshotCodec.encodeRoutine(accepted).equals(SnapshotCodec.encodeRoutine(session.getRoutine())))
                throw new IllegalArgumentException("Rutina no aceptada por esta cuenta o snapshot modificado");
            if (old != null) {
                Session previous = decode(old);
                if (previous.getStartedAt() != session.getStartedAt() || !SnapshotCodec.encodeRoutine(previous.getRoutine()).equals(SnapshotCodec.encodeRoutine(session.getRoutine())))
                    throw new IllegalArgumentException("Identidad de sesión reutilizada con otro snapshot");
            }
            return database.writeLocal(SyncRecord.Kind.SESSION, session.getSyncId(), SnapshotCodec.encodeSession(session));
        });
    }
    public Session load(long id) { EnerGymDatabase.Row row = database.find(SyncRecord.Kind.SESSION,id); return row == null ? null : decode(row); }
    /** Consulta de identidad local, siempre filtrada por dueño. Cero indica ausente.
     * Abrir un borrador no debe llamar saveDraft: no genera revisiones ni reemplaza resultados existentes. */
    public long findIdBySyncId(String syncId) { EnerGymDatabase.Row row=database.find(SyncRecord.Kind.SESSION,Objects.requireNonNull(syncId,"syncId"));return row==null?0:row.id; }
    /** Repetir finalizar conserva el instante original y produce una sola entrada de historial. */
    public void finish(long id) {
        database.transaction(() -> {
            EnerGymDatabase.Row row = database.find(SyncRecord.Kind.SESSION,id);
            if (row == null) throw new IllegalArgumentException("Sesión inexistente para esta cuenta");
            Session original = SnapshotCodec.decodeSession(row.record.getPayload());
            if (original.getStatus() != Session.Status.FINISHED) database.writeLocal(SyncRecord.Kind.SESSION, original.getSyncId(), SnapshotCodec.encodeSession(original.finishedAt(System.currentTimeMillis())));
            return null;
        });
    }
    public List<Session> pending() { return list(Session.Status.DRAFT); }
    public List<Session> history() { return list(Session.Status.FINISHED); }
    private List<Session> list(Session.Status status) {
        List<Session> result = new ArrayList<>();
        for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.SESSION,null)) { Session session=decode(row); if(session.getStatus()==status) result.add(session); }
        result.sort((a,b) -> Long.compare(status == Session.Status.FINISHED ? b.getCompletedAt() : b.getStartedAt(), status == Session.Status.FINISHED ? a.getCompletedAt() : a.getStartedAt()));
        return Collections.unmodifiableList(result);
    }
    private Session decode(EnerGymDatabase.Row row) {
        Session session = SnapshotCodec.decodeSession(row.record.getPayload());
        Routine local = routines.bySyncId(session.getRoutine().getProposalId());
        // El ID autoincremental puede cambiar tras reinstalar; solo se remapea, sin sustituir el snapshot histórico.
        return local == null ? session : session.withRoutineSnapshot(session.getRoutine().withVersionId(local.getVersionId()));
    }
}
