package com.energym.app.data;

import com.energym.app.domain.*;
import com.energym.app.sync.CloudDocumentCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** JSON real en Android; también ejecutable con el codec JSON real del runner aislado. */
public final class ManualRoutineCodecTest {
	static Routine legacy() {
		TrainingProfile profile = new TrainingProfile.Builder().build();
		Exercise exercise = new Exercise("fixture", "Fixture", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
			"Fixture", Collections.emptyList());
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sets", "2"); values.put("repetitions", "8"); values.put("loadKg", "0"); values.put("restSeconds", "60");
		return new Routine("fixture-base", 0, "Fixture", profile,
			Collections.singletonList(new RoutineItem(exercise, values, 5, "Fixture")), "Fixture");
	}

	static Session session(Routine routine) {
		return new Session("fixture-session", routine, Collections.emptyMap(), 100, 0, Session.Status.DRAFT);
	}

	static String sha(String text) throws Exception {
		StringBuilder hash = new StringBuilder();
		for (byte value : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
			hash.append(String.format(Locale.ROOT, "%02x", value & 255));
		return hash.toString();
	}

	@Test public void legacyRoutineAndEmbeddedSessionKeepTheirExactCanonicalBytes() throws Exception {
		String routine = SnapshotCodec.encodeRoutine(legacy());
		String session = SnapshotCodec.encodeSession(session(legacy()));
		// Captured from original Git e69b1c11d88d612d644d8adc39599a56bba4704d on Android API37.
		// Blob 1c13c8a73452ba8aef3b8524824fe243b2363391; A/B oracle confirmed exact original/new UTF-8 for both.
		assertEquals("35a38fc7a53e221d85a78fa4344c3710b084bcd191a685aa4990a8af31f4c78d", sha(routine));
		assertEquals("f56294f5fb86f8162df6d84ee77e02673bc9c9e7f86d409decf1bfefcc3c279c", sha(session));
		assertEquals(routine, SnapshotCodec.encodeRoutine(SnapshotCodec.decodeRoutine(routine)));
		assertEquals(session, SnapshotCodec.encodeSession(SnapshotCodec.decodeSession(session)));
		assertNull(SnapshotCodec.decodeRoutine(routine).getMetadata());
		assertFalse(new JSONObject(routine).has("metadata"));
	}

	@Test public void originsAndExplicitOffSurviveRoutineSessionAndCloudCanonicalization() {
		for (RoutineMetadata.Origin origin : RoutineMetadata.Origin.values())
			for (boolean enabled : new boolean[]{true, false}) {
				RoutineMetadata metadata = new RoutineMetadata(origin, enabled);
				Routine routine = legacy().withMetadata(metadata);
				String encoded = SnapshotCodec.encodeRoutine(routine);
				assertEquals(metadata, SnapshotCodec.decodeRoutine(encoded).getMetadata());
				assertEquals(enabled, SnapshotCodec.decodeRoutine(encoded).isProgressionEnabled());
				assertEquals(metadata, SnapshotCodec.decodeSession(SnapshotCodec.encodeSession(session(routine)))
					.getRoutine().getMetadata());
				for (SyncRecord record : Arrays.asList(new SyncRecord(SyncRecord.Kind.ROUTINE, routine.getProposalId(),
					"fixture-owner", encoded, 1, 1, SyncRecord.State.CLEAN),
					new SyncRecord(SyncRecord.Kind.SESSION, "fixture-session", "fixture-owner",
						SnapshotCodec.encodeSession(session(routine)), 1, 1, SyncRecord.State.CLEAN))) {
					Map<String, Object> doc = CloudDocumentCodec.encode(record, 1);
					assertEquals(10, doc.size());
					assertEquals(record.getPayload(), CloudDocumentCodec.decode("fixture-owner", record.getKind(),
						record.getSyncId(), doc).record.getPayload());
				}
			}
	}

	@Test public void malformedMetadataCannotBeSilentlyDroppedOrCoerced() throws Exception {
		for (Object metadata : Arrays.asList(JSONObject.NULL,
			new JSONObject().put("origin", "UNKNOWN").put("progressionEnabled", true),
			new JSONObject().put("origin", "MANUAL").put("progressionEnabled", "true"),
			new JSONObject().put("origin", "MANUAL"),
			new JSONObject().put("origin", "MANUAL").put("progressionEnabled", true).put("unexpected", 1))) {
			String invalid = new JSONObject(SnapshotCodec.encodeRoutine(legacy())).put("metadata", metadata).toString();
			assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.decodeRoutine(invalid));
		}
	}
}
