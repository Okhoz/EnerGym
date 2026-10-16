package com.energym.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;

import com.energym.app.domain.*;

import java.util.*;

import org.json.*;

/**
 * MODEL: almacenamiento SQLite y frontera de sincronización; no conoce vistas ni Firebase.
 */
public final class EnerGymDatabase extends SQLiteOpenHelper {
	/**
	 * Cuenta local explícita. Nunca migrar estos registros automáticamente a una cuenta Google.
	 */
	public static final String DEMO_OWNER_UID = "demo:local";
	private final String ownerUid;
	private final InitialRestoreGate initialRestore = new InitialRestoreGate();

	/**
	 * Cada operación filtra la identidad fijada al crear la instancia, incluso usando IDs locales.
	 */
	public EnerGymDatabase(Context context, String ownerUid) {
		super(context.getApplicationContext(), "energym.db", null, 2);
		if (ownerUid == null || ownerUid.trim().isEmpty() || !ownerUid.equals(ownerUid.trim()))
			throw new IllegalArgumentException("Identidad de cuenta vacía o inválida");
		this.ownerUid = ownerUid;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE records (local_id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uid TEXT NOT NULL, " +
			"kind TEXT NOT NULL, sync_id TEXT NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL CHECK(revision>0), " +
			"updated_at INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','CLEAN')), " +
			"UNIQUE(owner_uid,kind,sync_id))");
		db.execSQL("CREATE INDEX records_owner_outbox ON records(owner_uid,state)");
		createRevisionClock(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 1 && newVersion == 2) {
			createRevisionClock(db);
			return;
		}
		throw new IllegalStateException("Migración SQLite no implementada: " + oldVersion + " → " + newVersion);
	}

	private void createRevisionClock(SQLiteDatabase db) {
		// No contiene datos personales: sobrevive al borrado local para no reutilizar revisiones en subidas en vuelo.
		db.execSQL(
			"CREATE TABLE revision_clock (singleton INTEGER PRIMARY KEY CHECK(singleton=1), value INTEGER NOT NULL)");
		db.execSQL("INSERT INTO revision_clock(singleton,value) SELECT 1, COALESCE(MAX(revision),0) FROM records");
	}

	public String getOwnerUid() {
		return ownerUid;
	}

	/** Enable before exposing a new authenticated scope. This method performs no disk I/O. */
	public void requireInitialRestore() {
		if (!DEMO_OWNER_UID.equals(ownerUid))
			initialRestore.requireRestore();
	}

	/** Called on the serial database worker before data actions, never on the UI thread. */
	public void inspectInitialCache() {
		if (!initialRestore.needsCacheInspection())
			return;
		try (Cursor cursor = getReadableDatabase().query("records", new String[]{"local_id"}, "owner_uid=?",
			new String[]{ownerUid}, null, null, null, "1")) {
			initialRestore.cacheInspected(cursor.moveToFirst());
		}
	}

	public boolean initialRestorePending() {
		return initialRestore.pending();
	}

	/** Only the SERVER sync adapter calls this after committing its entire validated batch. */
	public void initialServerRestoreCompleted() {
		initialRestore.serverRestored();
	}

	/**
	 * Guarda todos los campos del perfil, incluidos recursos explícitos y consentimiento.
	 */
	public void saveProfile(TrainingProfile profile) {
		transaction(() -> writeLocal(SyncRecord.Kind.PROFILE, "profile", SnapshotCodec.encodeProfile(profile)));
	}

	public TrainingProfile loadProfile() {
		Row row = find(SyncRecord.Kind.PROFILE, "profile");
		return row == null ? null : SnapshotCodec.decodeProfile(row.record.getPayload());
	}

	/**
	 * Borra exclusivamente los datos locales de esta cuenta. No solicita borrado remoto.
	 */
	public void clearLocalData() {
		transaction(() -> {
			getWritableDatabase().delete("records", "owner_uid=?", new String[]{ownerUid});
			return null;
		});
	}

	/**
	 * Copia inmutable de la cola; cada escritura local cambia revisión y fecha dentro de su transacción.
	 */
	public List<SyncRecord> pendingSyncRecords() {
		List<SyncRecord> result = new ArrayList<>();
		for (Row row : rows(null, "PENDING"))
			result.add(row.record);
		return Collections.unmodifiableList(result);
	}

	/**
	 * CAS: solo confirma la revisión enviada; una edición durante la subida sigue pendiente.
	 */
	public boolean acknowledge(SyncRecord.Kind kind, String syncId, long revision) {
		ContentValues values = new ContentValues();
		values.put("state", SyncRecord.State.CLEAN.name());
		return getWritableDatabase().update("records", values,
			"owner_uid=? AND kind=? AND sync_id=? AND revision=? AND state='PENDING'",
			new String[]{ownerUid, kind.name(), syncId, String.valueOf(revision)}) == 1;
	}

	/**
	 * Snapshot versionado de una cuenta. Los IDs remotos son syncId; local_id nunca se exporta.
	 */
	public String exportSnapshot() {
		return transaction(() -> {
			try {
				JSONArray records = new JSONArray();
				for (Row row : rows(null, null)) {
					SyncRecord r = row.record;
					records.put(new JSONObject().put("ownerUid", ownerUid).put("kind", r.getKind().name())
						.put("syncId", r.getSyncId()).put("revision", r.getRevision())
						.put("updatedAt", r.getUpdatedAt()).put("payload", new JSONObject(r.getPayload())));
				}
				return new JSONObject().put("schemaVersion", 1).put("ownerUid", ownerUid).put("records", records)
					.toString();
			} catch (JSONException e) {
				throw new IllegalArgumentException("Snapshot inválido", e);
			}
		});
	}

	/**
	 * Reinstalación: valida todo antes de escribir y conserva siempre las modificaciones locales pendientes.
	 */
	public void importSnapshot(String snapshot) {
		try {
			JSONObject root = new JSONObject(snapshot);
			if (root.getInt("schemaVersion") != 1 || !ownerUid.equals(root.getString("ownerUid")))
				throw new IllegalArgumentException("Versión o cuenta del snapshot incorrecta");
			List<SyncRecord> records = new ArrayList<>();
			JSONArray entries = root.getJSONArray("records");
			for (int i = 0; i < entries.length(); i++) {
				JSONObject o = entries.getJSONObject(i);
				records.add(new SyncRecord(SyncRecord.Kind.valueOf(o.getString("kind")), o.getString("syncId"),
					o.getString("ownerUid"), o.getJSONObject("payload").toString(), o.getLong("revision"),
					o.getLong("updatedAt"), SyncRecord.State.CLEAN));
			}
			importSyncRecords(records);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Snapshot inválido", e);
		}
	}

	/**
	 * Importación para adaptadores remotos: nunca confía en la identidad o estado recibido. Operación atómica.
	 */
	public void importSyncRecords(List<SyncRecord> records) {
		List<SyncRecord> copy = new ArrayList<>(Objects.requireNonNull(records, "records"));
		Set<String> keys = new HashSet<>();
		for (SyncRecord record : copy) {
			validate(record);
			if (!keys.add(record.getKind().name() + ":" + record.getSyncId()))
				throw new IllegalArgumentException("Registro remoto duplicado");
		}
		transaction(() -> {
			// Resuelve el lote completo antes de escribir: una sesión debe referirse al snapshot realmente aceptado.
			for (SyncRecord remote : planImports(copy)) {
				Row local = find(remote.getKind(), remote.getSyncId());
				if (skipImport(local, remote))
					continue;
				if (local != null) {
					if (remote.getKind() == SyncRecord.Kind.SESSION) {
						Session previous = SnapshotCodec.decodeSession(local.record.getPayload());
						Session incoming = SnapshotCodec.decodeSession(remote.getPayload());
						if (previous.getStartedAt() != incoming.getStartedAt() ||
							!SnapshotCodec.encodeRoutine(previous.getRoutine().withVersionId(0))
								.equals(SnapshotCodec.encodeRoutine(incoming.getRoutine().withVersionId(0))))
							throw new IllegalArgumentException("Identidad remota de sesión modificada");
					}
				}
				ContentValues values =
					values(remote.getKind(), remote.getSyncId(), remote.getPayload(), remote.getRevision(),
						remote.getUpdatedAt(), SyncRecord.State.CLEAN);
				if (local == null)
					getWritableDatabase().insertOrThrow("records", null, values);
				else
					getWritableDatabase().update("records", values, "local_id=? AND owner_uid=?",
						new String[]{String.valueOf(local.id), ownerUid});
				getWritableDatabase().execSQL("UPDATE revision_clock SET value=MAX(value,?) WHERE singleton=1",
					new Object[]{remote.getRevision()});
			}
			return null;
		});
	}

	/**
	 * Rutinas locales definitivas tienen prioridad; las referencias del lote son independientes de su orden.
	 */
	private List<SyncRecord> planImports(List<SyncRecord> incoming) {
		Map<String, Routine> effective = new HashMap<>();
		for (Row local : rows(SyncRecord.Kind.ROUTINE, null)) {
			Routine routine = SnapshotCodec.decodeRoutine(local.record.getPayload());
			effective.put(routine.getProposalId(), routine);
		}
		for (SyncRecord remote : incoming)
			if (remote.getKind() == SyncRecord.Kind.ROUTINE) {
				Routine routine = SnapshotCodec.decodeRoutine(remote.getPayload());
				effective.putIfAbsent(routine.getProposalId(), routine);
			}
		List<SyncRecord> result = new ArrayList<>(incoming);
		for (SyncRecord remote : incoming)
			if (remote.getKind() == SyncRecord.Kind.SESSION) {
				if (skipImport(find(remote.getKind(), remote.getSyncId()), remote))
					continue;
				Routine snapshot = SnapshotCodec.decodeSession(remote.getPayload()).getRoutine();
				Routine accepted = effective.get(snapshot.getProposalId());
				if (accepted != null && !SnapshotCodec.encodeRoutine(accepted.withVersionId(0))
					.equals(SnapshotCodec.encodeRoutine(snapshot.withVersionId(0))))
					throw new IllegalArgumentException("La sesión remota no coincide con su versión aceptada");
				if (accepted == null) {
					// Restauración autónoma: materializa el snapshot completo como CLEAN para que el borrador pueda editarse.
					effective.put(snapshot.getProposalId(), snapshot);
					result.add(new SyncRecord(SyncRecord.Kind.ROUTINE, snapshot.getProposalId(), ownerUid,
						SnapshotCodec.encodeRoutine(snapshot.withVersionId(0)), remote.getRevision(),
						remote.getUpdatedAt(), SyncRecord.State.CLEAN));
				}
			}
		result.sort(Comparator.comparingInt(record -> record.getKind() == SyncRecord.Kind.ROUTINE ? 0 : 1));
		return result;
	}

	private boolean skipImport(Row local, SyncRecord remote) {
		// Borrar es definitivo también entre dispositivos: una edición pendiente no retiene un alimento borrado.
		if (local != null && remote.getKind() == SyncRecord.Kind.FOOD &&
			NutritionRepository.deleted(remote.getPayload()) && !NutritionRepository.deleted(local.record.getPayload()))
			return false;
		// Un registro que perderá frente al pendiente/finalizado local no introduce nuevas dependencias de restauración.
		return local != null && (local.record.getState() == SyncRecord.State.PENDING ||
			remote.getUpdatedAt() <= local.record.getUpdatedAt() || remote.getKind() == SyncRecord.Kind.ROUTINE || remote.getKind() == SyncRecord.Kind.TRAINING_ARCHIVE ||
			(remote.getKind() == SyncRecord.Kind.SESSION &&
				SnapshotCodec.decodeSession(local.record.getPayload()).getStatus() == Session.Status.FINISHED) ||
			// Un alimento borrado localmente es definitivo: una copia remota anterior no lo resucita.
			(remote.getKind() == SyncRecord.Kind.FOOD && NutritionRepository.deleted(local.record.getPayload())) ||
			(remote.getKind() == SyncRecord.Kind.GOAL && GoalRepository.deleted(local.record.getPayload())));
	}

	private void validate(SyncRecord r) {
		Objects.requireNonNull(r, "record");
		if (!ownerUid.equals(r.getOwnerUid()))
			throw new IllegalArgumentException("Registro de otra cuenta");
		switch (r.getKind()) {
			case TRAINING_ARCHIVE:
				TrainingArchiveRepository.canonical(r.getSyncId(), r.getPayload());
				break;
			case PROFILE:
				if (!"profile".equals(r.getSyncId()))
					throw new IllegalArgumentException("ID de perfil inválido");
				SnapshotCodec.decodeProfile(r.getPayload());
				break;
			case ROUTINE:
				Routine routine = SnapshotCodec.decodeRoutine(r.getPayload());
				if (!r.getSyncId().equals(routine.getProposalId()) || !routine.isValidFor(routine.getProfile()))
					throw new IllegalArgumentException("Snapshot de rutina inválido");
				break;
			case BODY:
				if (!NutritionRepository.BODY_ID.equals(r.getSyncId()))
					throw new IllegalArgumentException("ID de perfil físico inválido");
				NutritionRepository.decodeBody(r.getPayload());
				break;
			case FOOD:
				NutritionRepository.canonicalFood(r.getSyncId(), r.getPayload());
				break;
			case WEIGHT:
				NutritionRepository.canonicalWeight(r.getSyncId(), r.getPayload());
				break;
			case GOAL:
				GoalRepository.canonical(r.getSyncId(), r.getPayload());
				break;
			case EXTRA:
				ExtrasRepository.canonical(r.getSyncId(), r.getPayload());
				break;
			case SESSION:
				Session session = SnapshotCodec.decodeSession(r.getPayload());
				// versionId=0 es el marcador portátil de nube, nunca una prueba de aceptación local.
				// planImports resuelve/materializa la versión aceptada por proposalId y comprueba su snapshot;
				// SessionRepository exige el ID SQLite real cuando el borrador restaurado se vuelve a guardar.
				if (!r.getSyncId().equals(session.getSyncId()) ||
					!session.getRoutine().isValidFor(session.getRoutine().getProfile()))
					throw new IllegalArgumentException("Snapshot de sesión inválido");
				break;
		}
	}

	interface Work<T> {
		T run();
	}

	/**
	 * La actualización del contenido y de su cola de salida comparte la misma transacción SQLite.
	 */
	<T> T transaction(Work<T> work) {
		SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			T result = work.run();
			db.setTransactionSuccessful();
			return result;
		} finally {
			db.endTransaction();
		}
	}

	long writeLocal(SyncRecord.Kind kind, String syncId, String payload) {
		initialRestore.requireWritable();
		Row old = find(kind, syncId);
		if (old != null && old.record.getPayload().equals(payload))
			return old.id;
		long sequence;
		try (Cursor c = getWritableDatabase().rawQuery("SELECT value FROM revision_clock WHERE singleton=1", null)) {
			c.moveToFirst();
			sequence = c.getLong(0);
		}
		long revision = Math.addExact(Math.max(sequence, old == null ? 0 : old.record.getRevision()), 1);
		getWritableDatabase().execSQL("UPDATE revision_clock SET value=? WHERE singleton=1", new Object[]{revision});
		long updated = old == null ? System.currentTimeMillis() :
			Math.max(System.currentTimeMillis(), Math.addExact(old.record.getUpdatedAt(), 1));
		// La aceptación nueva queda después de las anteriores incluso si el reloj retrocede o empata.
		if (kind == SyncRecord.Kind.ROUTINE && old == null)
			for (Row accepted : rows(SyncRecord.Kind.ROUTINE, null))
				updated = Math.max(updated, Math.addExact(accepted.record.getUpdatedAt(), 1));
		ContentValues values = values(kind, syncId, payload, revision, updated, SyncRecord.State.PENDING);
		if (old == null)
			return getWritableDatabase().insertOrThrow("records", null, values);
		getWritableDatabase().update("records", values, "local_id=? AND owner_uid=?",
			new String[]{String.valueOf(old.id), ownerUid});
		return old.id;
	}

	private ContentValues values(SyncRecord.Kind kind, String syncId, String payload, long revision, long updated,
		SyncRecord.State state) {
		ContentValues v = new ContentValues();
		v.put("owner_uid", ownerUid);
		v.put("kind", kind.name());
		v.put("sync_id", syncId);
		v.put("payload", payload);
		v.put("revision", revision);
		v.put("updated_at", updated);
		v.put("state", state.name());
		return v;
	}

	Row find(SyncRecord.Kind kind, String syncId) {
		return find("kind=? AND sync_id=?", new String[]{kind.name(), syncId});
	}

	Row find(SyncRecord.Kind kind, long id) {
		return find("kind=? AND local_id=?", new String[]{kind.name(), String.valueOf(id)});
	}

	private Row find(String where, String[] args) {
		String[] scoped = new String[args.length + 1];
		scoped[0] = ownerUid;
		System.arraycopy(args, 0, scoped, 1, args.length);
		try (Cursor c = getReadableDatabase().query("records", null, "owner_uid=? AND " + where, scoped, null, null,
			null)) {
			return c.moveToFirst() ? row(c) : null;
		}
	}

	List<Row> rows(SyncRecord.Kind kind, String state) {
		String where = "owner_uid=?";
		List<String> args = new ArrayList<>();
		args.add(ownerUid);
		if (kind != null) {
			where += " AND kind=?";
			args.add(kind.name());
		}
		if (state != null) {
			where += " AND state=?";
			args.add(state);
		}
		List<Row> result = new ArrayList<>();
		try (Cursor c = getReadableDatabase().query("records", null, where, args.toArray(new String[0]), null, null,
			"local_id DESC")) {
			while (c.moveToNext())
				result.add(row(c));
		}
		return result;
	}

	private Row row(Cursor c) {
		return new Row(c.getLong(c.getColumnIndexOrThrow("local_id")),
			new SyncRecord(SyncRecord.Kind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
				c.getString(c.getColumnIndexOrThrow("sync_id")), c.getString(c.getColumnIndexOrThrow("owner_uid")),
				c.getString(c.getColumnIndexOrThrow("payload")), c.getLong(c.getColumnIndexOrThrow("revision")),
				c.getLong(c.getColumnIndexOrThrow("updated_at")),
				SyncRecord.State.valueOf(c.getString(c.getColumnIndexOrThrow("state")))));
	}

	static final class Row {
		final long id;
		final SyncRecord record;

		Row(long id, SyncRecord record) {
			this.id = id;
			this.record = record;
		}
	}
}
