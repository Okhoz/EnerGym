package com.energym.app.sync;

import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.SyncRecord;

import java.util.List;
import java.util.*;

import org.json.*;

/**
 * Adaptador MODEL sin lógica paralela de persistencia: usa transacciones/importación de Task3.
 */
public final class SqliteSyncStore implements LocalSyncStore {
	private final EnerGymDatabase database;

	public SqliteSyncStore(EnerGymDatabase database) {
		this.database = java.util.Objects.requireNonNull(database);
	}

	public String ownerUid() {
		return database.getOwnerUid();
	}

	public List<SyncRecord> pending() {
		return database.pendingSyncRecords();
	}

	/**
	 * El lote procede de SERVER y representa el estado actual. Los relojes entre dispositivos
	 * no deciden qué versión remota es actual: adapta fecha local de CLEAN, preservando PENDING.
	 * Snapshots iguales se omiten para que una restauración repetida sea idempotente.
	 */
	public void importRecords(List<SyncRecord> records) {
		try {
			Map<String, JSONObject> local = new HashMap<>();
			JSONArray rows = new JSONObject(database.exportSnapshot()).getJSONArray("records");
			for (int i = 0; i < rows.length(); i++) {
				JSONObject row = rows.getJSONObject(i);
				local.put(row.getString("kind") + ":" + row.getString("syncId"), row);
			}
			Set<String> pending = new HashSet<>();
			for (SyncRecord r : database.pendingSyncRecords())
				pending.add(key(r));
			List<SyncRecord> adjusted = new ArrayList<>();
			for (SyncRecord remote : records) {
				if (!database.getOwnerUid().equals(remote.getOwnerUid()))
					throw new IllegalArgumentException("Cuenta remota incorrecta");
				JSONObject previous = local.get(key(remote));
				long time = remote.getUpdatedAt();
				if (previous != null && !pending.contains(key(remote))) {
					SyncRecord old = new SyncRecord(remote.getKind(), remote.getSyncId(), remote.getOwnerUid(),
						previous.getJSONObject("payload").toString(), previous.getLong("revision"),
						previous.getLong("updatedAt"), SyncRecord.State.CLEAN);
					if (CloudDocumentCodec.encode(old, 1).get("payload")
						.equals(CloudDocumentCodec.encode(remote, 1).get("payload")))
						continue;
					time = Math.max(time, Math.addExact(old.getUpdatedAt(), 1));
				}
				adjusted.add(
					new SyncRecord(remote.getKind(), remote.getSyncId(), remote.getOwnerUid(), remote.getPayload(),
						remote.getRevision(), time, SyncRecord.State.CLEAN));
			}
			database.importSyncRecords(adjusted);
			// Includes a successful empty response for a genuinely new account; invalid/failed imports stay guarded.
			database.initialServerRestoreCompleted();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Snapshot local inválido", e);
		}
	}

	private static String key(SyncRecord r) {
		return r.getKind().name() + ":" + r.getSyncId();
	}

	public boolean acknowledge(SyncRecord record) {
		return database.acknowledge(record.getKind(), record.getSyncId(), record.getRevision());
	}

	public void close() {
		database.close();
	}
}
