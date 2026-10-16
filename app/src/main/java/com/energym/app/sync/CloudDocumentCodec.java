package com.energym.app.sync;

import com.energym.app.data.*;
import com.energym.app.domain.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Frontera Firestore: snapshots canónicos sin IDs locales, tamaño acotado e identidad histórica estable.
 */
public final class CloudDocumentCodec {
	public static final int MAX_PAYLOAD_BYTES = 600_000;

	private CloudDocumentCodec() {
	}

	public static String collection(SyncRecord.Kind kind) {
		switch (kind) {
			case PROFILE:
				return "profile";
			case ROUTINE:
				return "routines";
			case BODY:
				return "body";
			case FOOD:
				return "food";
			case WEIGHT:
				return "weights";
			case GOAL:
				return "goals";
			case TRAINING_ARCHIVE:
				return "trainingArchive";
			case EXTRA:
				return "extras";
			default:
				return "sessions";
		}
	}

	/**
	 * Perfil y perfil físico son un único documento "current" por cuenta.
	 */
	public static boolean singleton(SyncRecord.Kind kind) {
		return kind == SyncRecord.Kind.PROFILE || kind == SyncRecord.Kind.BODY;
	}

	public static String documentId(SyncRecord.Kind kind, String syncId) {
		validateId(syncId);
		return singleton(kind) ? "current" : syncId;
	}

	private static void validateId(String id) {
		if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}"))
			throw new IllegalArgumentException("Identidad remota inválida");
	}

	/**
	 * Se valida el contenido con el codec Task3 antes de cualquier red; no se confía en metadatos entrantes.
	 */
	public static Map<String, Object> encode(SyncRecord record, long cloudVersion) {
		validateId(record.getSyncId());
		String payload, identity = "";
		boolean terminal = false;
		switch (record.getKind()) {
			case PROFILE:
				if (!"profile".equals(record.getSyncId()))
					throw new IllegalArgumentException("Identidad de perfil inválida");
				payload = SnapshotCodec.encodeProfile(SnapshotCodec.decodeProfile(record.getPayload()));
				break;
			case ROUTINE:
				com.energym.app.domain.Routine routine =
					SnapshotCodec.decodeRoutine(record.getPayload()).withVersionId(0);
				if (!routine.getProposalId().equals(record.getSyncId()))
					throw new IllegalArgumentException("Identidad de rutina incorrecta");
				payload = SnapshotCodec.encodeRoutine(routine);
				identity = hash(payload);
				terminal = true;
				break;
			case TRAINING_ARCHIVE:
				payload = TrainingArchiveRepository.canonical(record.getSyncId(), record.getPayload());
				identity = hash(payload);
				terminal = true;
				break;
			case BODY:
				if (!NutritionRepository.BODY_ID.equals(record.getSyncId()))
					throw new IllegalArgumentException("Identidad de perfil físico inválida");
				payload = NutritionRepository.encodeBody(NutritionRepository.decodeBody(record.getPayload()));
				break;
			case WEIGHT:
				payload = NutritionRepository.canonicalWeight(record.getSyncId(), record.getPayload());
				break;
			case GOAL:
				payload = GoalRepository.canonical(record.getSyncId(), record.getPayload());
				identity = hash(record.getSyncId() + ":" + GoalRepository.createdAt(payload));
				terminal = GoalRepository.deleted(payload);
				break;
			case EXTRA:
				// Medidas, comidas guardadas, ejercicios propios y superseries: editables, gana el último.
				payload = ExtrasRepository.canonical(record.getSyncId(), record.getPayload());
				break;
			case FOOD:
				// Identidad estable entre alta y lápida; la lápida es definitiva.
				payload = NutritionRepository.canonicalFood(record.getSyncId(), record.getPayload());
				identity = hash(record.getSyncId() + ":" + NutritionRepository.foodCreatedAt(payload));
				terminal = NutritionRepository.deleted(payload);
				break;
			default:
				Session session = SnapshotCodec.decodeSession(record.getPayload());
				if (!session.getSyncId().equals(record.getSyncId()))
					throw new IllegalArgumentException("Identidad de sesión incorrecta");
				session = session.withRoutineSnapshot(session.getRoutine().withVersionId(0));
				payload = SnapshotCodec.encodeSession(session);
				identity = hash(session.getStartedAt() + ":" + SnapshotCodec.encodeRoutine(session.getRoutine()));
				terminal = session.getStatus() == Session.Status.FINISHED;
		}
		if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES)
			throw new IllegalArgumentException("Snapshot supera 600 KB; conserva la copia local");
		Map<String, Object> doc = new HashMap<>();
		doc.put("schemaVersion", 1L);
		doc.put("ownerUid", record.getOwnerUid());
		doc.put("kind", record.getKind().name());
		doc.put("syncId", record.getSyncId());
		doc.put("payload", payload);
		doc.put("revision", record.getRevision());
		doc.put("updatedAt", record.getUpdatedAt());
		doc.put("cloudVersion", cloudVersion);
		doc.put("identity", identity);
		doc.put("terminal", terminal);
		return doc;
	}

	/**
	 * La cuenta y ruta deben coincidir; documentos corruptos abortan restauración, nunca se importan parcialmente.
	 */
	public static CloudSyncGateway.RemoteRecord decode(String owner, SyncRecord.Kind kind, String documentId,
		Map<String, Object> doc) {
		if (doc == null || doc.size() != 10 || !Long.valueOf(1).equals(doc.get("schemaVersion")) ||
			!owner.equals(doc.get("ownerUid")) || !kind.name().equals(doc.get("kind")))
			throw new IllegalArgumentException("Esquema/cuenta remota inválidos");
		String syncId = (String) doc.get("syncId");
		if (!documentId(kind, syncId).equals(documentId))
			throw new IllegalArgumentException("Ruta remota incorrecta");
		SyncRecord record = new SyncRecord(kind, syncId, owner, (String) doc.get("payload"), number(doc, "revision"),
			number(doc, "updatedAt"), SyncRecord.State.CLEAN);
		long version = number(doc, "cloudVersion");
		Map<String, Object> canonical = encode(record, version);
		if (!canonical.get("identity").equals(doc.get("identity")) ||
			!canonical.get("terminal").equals(doc.get("terminal")))
			throw new IllegalArgumentException("Identidad histórica remota inválida");
		return new CloudSyncGateway.RemoteRecord(
			new SyncRecord(kind, syncId, owner, (String) canonical.get("payload"), record.getRevision(),
				record.getUpdatedAt(), SyncRecord.State.CLEAN), version);
	}

	private static long number(Map<String, Object> doc, String field) {
		Object value = doc.get(field);
		if (!(value instanceof Long) || ((Long) value) < 1)
			throw new IllegalArgumentException("Campo remoto inválido: " + field);
		return (Long) value;
	}

	private static String hash(String text) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder();
			for (byte b : bytes)
				result.append(String.format(Locale.ROOT, "%02x", b & 255));
			return result.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
