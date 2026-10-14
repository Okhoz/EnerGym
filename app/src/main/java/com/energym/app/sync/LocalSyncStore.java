package com.energym.app.sync;

import com.energym.app.data.SyncRecord;

import java.util.List;

/**
 * Frontera MODEL local: el adaptador SQLite conserva importación atómica y confirmación CAS exacta.
 */
public interface LocalSyncStore extends AutoCloseable {
	String ownerUid();

	List<SyncRecord> pending();

	void importRecords(List<SyncRecord> records);

	boolean acknowledge(SyncRecord record);

	void close();
}
