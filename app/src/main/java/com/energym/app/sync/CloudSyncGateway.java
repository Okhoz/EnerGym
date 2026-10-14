package com.energym.app.sync;

import com.energym.app.data.SyncRecord;

import java.util.List;

/**
 * MODEL remoto. Las revisiones locales no ordenan dispositivos; cloudVersion es CAS del servidor.
 */
public interface CloudSyncGateway {
	interface Callback<T> {
		void onSuccess(T value);

		void onFailure(Exception error);
	}

	final class RemoteRecord {
		public final SyncRecord record;
		public final long cloudVersion;

		public RemoteRecord(SyncRecord record, long cloudVersion) {
			if (cloudVersion < 1)
				throw new IllegalArgumentException("Versión remota inválida");
			this.record = java.util.Objects.requireNonNull(record);
			this.cloudVersion = cloudVersion;
		}
	}

	/**
	 * CONFLICT conserva la cola local; no se confirma una escritura rechazada.
	 */
	final class UploadResult {
		public final boolean accepted;
		public final long cloudVersion;

		public UploadResult(boolean accepted, long cloudVersion) {
			this.accepted = accepted;
			this.cloudVersion = cloudVersion;
		}
	}

	void restore(String ownerUid, Callback<List<RemoteRecord>> callback);

	void upload(SyncRecord record, long expectedCloudVersion, Callback<UploadResult> callback);
}
