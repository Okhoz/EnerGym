package com.energym.app.data;

import com.energym.app.domain.*;

import java.util.*;

/**
 * MODEL: acepta versiones inmutables; una propuesta rechazada nunca llega a este repositorio.
 */
public final class RoutineRepository {
	private final EnerGymDatabase database;

	public RoutineRepository(EnerGymDatabase database) {
		this.database = Objects.requireNonNull(database, "database");
	}

	/**
	 * Devuelve el ID local; repetir la misma aceptación no crea otra versión ni cambia su snapshot.
	 */
	public long saveAccepted(Routine routine) {
		Objects.requireNonNull(routine, "routine");
		return database.transaction(() -> {
			EnerGymDatabase.Row old = database.find(SyncRecord.Kind.ROUTINE, routine.getProposalId());
			String payload = SnapshotCodec.encodeRoutine(routine.withVersionId(0));
			if (old != null) {
				if (!SnapshotCodec.encodeRoutine(SnapshotCodec.decodeRoutine(old.record.getPayload()).withVersionId(0)).equals(payload))
					throw new IllegalArgumentException("Una versión aceptada no se puede modificar");
				return old.id;
			}
			TrainingProfile current = database.loadProfile();
			if (!routine.isValidFor(current == null ? routine.getProfile() : current))
				throw new IllegalArgumentException("Propuesta inválida o desactualizada");
			return database.writeLocal(SyncRecord.Kind.ROUTINE, routine.getProposalId(), payload);
		});
	}

	public Routine load(long id) {
		EnerGymDatabase.Row row = database.find(SyncRecord.Kind.ROUTINE, id);
		return row == null ? null : decode(row);
	}

	/**
	 * Compara recursos/consentimiento dentro de SQLite; ningún fallo deja el perfil parcialmente guardado.
	 */
	public Routine acceptProposal(Routine routine, TrainingProfile expected) {
		return database.transaction(() -> {
			if (!Objects.equals(database.loadProfile(), expected) || !routine.isValidFor(routine.getProfile()))
				throw new IllegalArgumentException("El perfil cambió. Revisa una nueva propuesta.");
			database.saveProfile(routine.getProfile());
			return load(saveAccepted(routine));
		});
	}

	public List<Routine> versions() {
		List<Routine> result = new ArrayList<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.ROUTINE, null))
			result.add(decode(row));
		return Collections.unmodifiableList(result);
	}

	Routine bySyncId(String id) {
		EnerGymDatabase.Row row = database.find(SyncRecord.Kind.ROUTINE, id);
		return row == null ? null : decode(row);
	}

	private Routine decode(EnerGymDatabase.Row row) {
		return SnapshotCodec.decodeRoutine(row.record.getPayload()).withVersionId(row.id);
	}
}
