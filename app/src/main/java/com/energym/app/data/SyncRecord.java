package com.energym.app.data;

import java.util.Objects;

/**
 * Registro inmutable para adaptadores de nube: identidad por cuenta y revisión exacta del contenido enviado.
 */
public final class SyncRecord {
	public enum Kind {PROFILE, ROUTINE, SESSION}

	public enum State {PENDING, CLEAN}

	private final Kind kind;
	private final String syncId, ownerUid, payload;
	private final long revision, updatedAt;
	private final State state;

	/**
	 * Los payloads JSON completos los valida la base al importar; nunca se aceptan cuentas cruzadas.
	 */
	public SyncRecord(Kind kind, String syncId, String ownerUid, String payload, long revision, long updatedAt, State state) {
		this.kind = Objects.requireNonNull(kind, "kind");
		this.state = Objects.requireNonNull(state, "state");
		if (syncId == null || syncId.trim().isEmpty() || ownerUid == null || ownerUid.trim().isEmpty() || revision <= 0 || updatedAt <= 0)
			throw new IllegalArgumentException("Registro de sincronización inválido");
		this.syncId = syncId;
		this.ownerUid = ownerUid;
		this.payload = Objects.requireNonNull(payload, "payload");
		this.revision = revision;
		this.updatedAt = updatedAt;
	}

	public Kind getKind() {
		return kind;
	}

	public String getSyncId() {
		return syncId;
	}

	public String getOwnerUid() {
		return ownerUid;
	}

	public String getPayload() {
		return payload;
	}

	public long getRevision() {
		return revision;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public State getState() {
		return state;
	}
}
