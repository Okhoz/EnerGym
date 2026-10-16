package com.energym.app.data;

import com.energym.app.domain.*;
import com.energym.app.assistant.ChatProtocol;

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
				if (!SnapshotCodec.encodeRoutine(SnapshotCodec.decodeRoutine(old.record.getPayload()).withVersionId(0))
					.equals(payload))
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

	/** Aceptación manual independiente: conserva el perfil general y crea una versión inmutable. */
	public long saveManual(Routine routine) {
		Objects.requireNonNull(routine, "routine");
		if (routine.getMetadata() == null || routine.getMetadata().getOrigin() != RoutineMetadata.Origin.MANUAL)
			throw new IllegalArgumentException("Se requiere una rutina manual explícita");
		if (!routine.isValidFor(routine.getProfile()))
			throw new IllegalArgumentException("Rutina manual incompatible, vacía o fuera del tiempo disponible");
		for (RoutineItem item : routine.getItems())
			ChatProtocol.validatePrescription(item.getExercise(), item.getPrescription(), item.getEstimatedMinutes());
		return database.transaction(() -> {
			EnerGymDatabase.Row old = database.find(SyncRecord.Kind.ROUTINE, routine.getProposalId());
			String payload = SnapshotCodec.encodeRoutine(routine.withVersionId(0));
			if (old != null) {
				if (!SnapshotCodec.encodeRoutine(SnapshotCodec.decodeRoutine(old.record.getPayload()).withVersionId(0))
					.equals(payload))
					throw new IllegalArgumentException("Una versión aceptada no se puede modificar");
				return old.id;
			}
			return database.writeLocal(SyncRecord.Kind.ROUTINE, routine.getProposalId(), payload);
		});
	}

	/** Reemplaza la ficha visible; conserva la versión anterior en los snapshots de las sesiones. */
	public Routine replaceManual(String originalId, Routine replacement) {
		return database.transaction(() -> {
			if (originalId.equals(replacement.getProposalId()))
				throw new IllegalArgumentException("La edición necesita una nueva versión");
			Routine original = bySyncId(originalId);
			if (original == null)
				throw new IllegalArgumentException("La rutina no pertenece a esta cuenta");
			TrainingArchiveRepository archived = new TrainingArchiveRepository(database);
			if (archived.contains(SyncRecord.Kind.ROUTINE, originalId) && bySyncId(replacement.getProposalId()) == null)
				throw new IllegalArgumentException("La rutina ya está eliminada o fue editada en otro dispositivo");
			long id = saveManual(replacement);
			archived.archive(SyncRecord.Kind.ROUTINE, originalId);
			return load(id);
		});
	}

	/**
	 * Progresión automática: guarda la nueva versión y archiva la anterior en una transacción. Las sesiones ya
	 * registradas conservan su copia de la versión con la que se hicieron.
	 */
	public Routine replaceWithProgression(String originalId, Routine replacement) {
		return database.transaction(() -> {
			if (originalId.equals(replacement.getProposalId()) || bySyncId(originalId) == null)
				throw new IllegalArgumentException("La rutina ya no está disponible");
			if (new TrainingArchiveRepository(database).contains(SyncRecord.Kind.ROUTINE, originalId))
				throw new IllegalArgumentException("La rutina ya subió de nivel en otro dispositivo");
			boolean manual = replacement.getMetadata() != null &&
				replacement.getMetadata().getOrigin() == RoutineMetadata.Origin.MANUAL;
			long id = manual ? saveManual(replacement) : saveAccepted(replacement);
			new TrainingArchiveRepository(database).archive(SyncRecord.Kind.ROUTINE, originalId);
			return load(id);
		});
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
		List<EnerGymDatabase.Row> rows = database.rows(SyncRecord.Kind.ROUTINE, null);
		// Una rutina nunca se edita: updatedAt es su aceptación durable, no el instante de sincronización.
		// El empate entre dispositivos se resuelve por UUID ascendente, nunca por el ID SQLite regenerado.
		rows.sort(Comparator.<EnerGymDatabase.Row>comparingLong(row -> row.record.getUpdatedAt()).reversed()
			.thenComparing(row -> row.record.getSyncId()));
		Set<String> archived = new TrainingArchiveRepository(database).targets(SyncRecord.Kind.ROUTINE);
		for (EnerGymDatabase.Row row : rows)
			if (!archived.contains(row.record.getSyncId()))
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
