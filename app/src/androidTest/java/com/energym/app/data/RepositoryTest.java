package com.energym.app.data;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import java.util.*;

import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static com.energym.app.domain.TrainingProfile.*;

@RunWith(AndroidJUnit4.class)
public class RepositoryTest {
	@Test
	public void durableAcceptanceSurvivesSyncRetryTiesAndClockRollback() throws Exception {
		Routine template = proposal();
		List<SyncRecord> records = new ArrayList<>();
		long future = System.currentTimeMillis() + 86400000L;
		for (String id : Arrays.asList("z-tie", "a-tie")) {
			Routine routine =
				new Routine(id, 0, id, template.getProfile(), template.getItems(), template.getExplanation());
			records.add(
				new SyncRecord(SyncRecord.Kind.ROUTINE, id, uid, SnapshotCodec.encodeRoutine(routine), 1, future,
					SyncRecord.State.CLEAN));
		}
		com.energym.app.sync.SqliteSyncStore sync = new com.energym.app.sync.SqliteSyncStore(db);
		for (int order = 0; order < 2; order++) {
			db.clearLocalData();
			sync.importRecords(records);
			sync.importRecords(records);
			assertEquals("a-tie", routines.versions().get(0).getProposalId());
			assertEquals(future, db.find(SyncRecord.Kind.ROUTINE, "a-tie").record.getUpdatedAt());
			Collections.reverse(records);
		}
		Routine next = proposal();
		routines.saveAccepted(next);
		SyncRecord accepted = db.find(SyncRecord.Kind.ROUTINE, next.getProposalId()).record;
		assertEquals(future + 1, accepted.getUpdatedAt());
		routines.saveAccepted(next);
		assertEquals(future + 1, db.find(SyncRecord.Kind.ROUTINE, next.getProposalId()).record.getUpdatedAt());
		SyncRecord roundtrip =
			com.energym.app.sync.CloudDocumentCodec.decode(uid, accepted.getKind(), accepted.getSyncId(),
				com.energym.app.sync.CloudDocumentCodec.encode(accepted, 1)).record;
		assertEquals(future + 1, roundtrip.getUpdatedAt());
		db.acknowledge(accepted.getKind(), accepted.getSyncId(), accepted.getRevision());
		sync.importRecords(Collections.singletonList(roundtrip));
		assertEquals(next.getProposalId(), routines.versions().get(0).getProposalId());
		assertEquals(future + 1, db.find(SyncRecord.Kind.ROUTINE, next.getProposalId()).record.getUpdatedAt());
	}

	@Test
	public void restoredCurrentRoutineUsesAcceptanceTimeNotImportIdsInEitherOrder() throws Exception {
		Routine template = proposal();
		List<SyncRecord> records = new ArrayList<>();
		for (String id : Arrays.asList("a-new", "z-old")) {
			Routine routine =
				new Routine(id, 0, id, template.getProfile(), template.getItems(), template.getExplanation());
			records.add(new SyncRecord(SyncRecord.Kind.ROUTINE, id, uid, SnapshotCodec.encodeRoutine(routine), 1,
				id.equals("a-new") ? 200 : 100, SyncRecord.State.CLEAN));
		}
		for (int order = 0; order < 2; order++) {
			db.clearLocalData();
			db.importSyncRecords(records);
			assertEquals("a-new", routines.versions().get(0).getProposalId());
			assertEquals("a-new", new Session(routines.versions().get(0)).getRoutine().getProposalId());
			assertEquals(2, routines.versions().size());
			Collections.reverse(records);
		}
	}

	private Context context;
	private EnerGymDatabase db;
	private RoutineRepository routines;
	private SessionRepository sessions;
	private String uid;

	@Before
	public void open() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		uid = "test-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, uid);
		routines = new RoutineRepository(db);
		sessions = new SessionRepository(db);
	}

	@After
	public void close() {
		db.clearLocalData();
		db.close();
	}

	private Routine proposal() throws Exception {
		TrainingProfile profile = db.loadProfile();
		return new RoutinePlanner().create(profile == null ? new TrainingProfile.Builder().build() : profile,
			CatalogRepository.load(context).getExercises());
	}

	private Routine accepted() throws Exception {
		long id = routines.saveAccepted(proposal());
		assertTrue("accepted version must persist", id > 0);
		return routines.load(id);
	}

	@Test
	public void previewRejectionDoesNotReplaceAcceptedVersion() throws Exception {
		Routine first = accepted();
		Routine ignoredPreview = proposal();
		assertNotEquals(first.getProposalId(), ignoredPreview.getProposalId());
		assertEquals(first.getProposalId(), routines.load(first.getVersionId()).getProposalId());
		assertEquals(1, routines.versions().size());
	}

	@Test
	public void newVersionKeepsEarlierSessionExerciseAndPrescriptionSnapshot() throws Exception {
		Routine first = accepted();
		Session draft = new Session(first);
		long sessionId = sessions.saveDraft(draft);
		assertTrue(sessionId > 0);
		Routine second = proposal();
		routines.saveAccepted(second);
		Session restored = sessions.load(sessionId);
		assertNotNull(restored);
		assertEquals(first.getProposalId(), restored.getRoutine().getProposalId());
		assertEquals(first.getItems().get(0).getExercise().getDescription(),
			restored.getRoutine().getItems().get(0).getExercise().getDescription());
		assertEquals(first.getItems().get(0).getPrescription(),
			restored.getRoutine().getItems().get(0).getPrescription());
		assertEquals(2, routines.versions().size());
	}

	@Test
	public void reopenRecoversDraftActualValuesAndStableId() throws Exception {
		Session draft =
			new Session(accepted()).withResult("bodyweight-squat", Collections.singletonMap("repetitions", "17"));
		long id = sessions.saveDraft(draft);
		assertTrue(id > 0);
		db.close();
		db = new EnerGymDatabase(context, uid);
		sessions = new SessionRepository(db);
		Session recovered = sessions.load(id);
		assertNotNull(recovered);
		assertEquals(draft.getSyncId(), recovered.getSyncId());
		assertEquals("17", recovered.getResults().get("bodyweight-squat").get("repetitions"));
		assertEquals(1, sessions.pending().size());
		assertTrue(sessions.history().isEmpty());
	}

	@Test
	public void finishIsIdempotentAndStaleDraftCannotResurrectFinishedSession() throws Exception {
		Session stale = new Session(accepted());
		long id = sessions.saveDraft(stale);
		assertTrue(id > 0);
		sessions.finish(id);
		Session first = sessions.load(id);
		assertNotNull(first);
		sessions.finish(id);
		sessions.saveDraft(stale);
		assertEquals(1, sessions.history().size());
		assertTrue(sessions.pending().isEmpty());
		assertEquals(first.getCompletedAt(), sessions.load(id).getCompletedAt());
		assertEquals(Session.Status.FINISHED, sessions.load(id).getStatus());
	}

	@Test
	public void repeatedAcceptanceAndDraftSaveKeepOneStableRecord() throws Exception {
		Routine proposal = proposal();
		long first = routines.saveAccepted(proposal);
		assertTrue(first > 0);
		assertEquals(first, routines.saveAccepted(proposal));
		Session draft = new Session(routines.load(first));
		long id = sessions.saveDraft(draft);
		assertTrue(id > 0);
		assertEquals(id, sessions.saveDraft(draft));
		assertEquals(1, sessions.pending().size());
	}

	@Test
	public void accountsCannotReadUpdateOrClearEachOthersRecords() throws Exception {
		Routine mine = accepted();
		long id = sessions.saveDraft(new Session(mine));
		assertTrue(id > 0);
		try (EnerGymDatabase other = new EnerGymDatabase(context, "other-" + uid)) {
			RoutineRepository otherRoutines = new RoutineRepository(other);
			SessionRepository otherSessions = new SessionRepository(other);
			assertNull(otherRoutines.load(mine.getVersionId()));
			assertNull(otherSessions.load(id));
			assertThrows(IllegalArgumentException.class, () -> otherSessions.finish(id));
			other.clearLocalData();
			assertNotNull(sessions.load(id));
			assertTrue(otherSessions.history().isEmpty());
		}
		assertThrows(IllegalArgumentException.class, () -> new EnerGymDatabase(context, " "));
	}

	@Test
	public void completeProfileIncludingPrivacyAndExplicitResourcesSurvivesReopen() {
		TrainingProfile profile =
			new TrainingProfile.Builder().sportId("cycling").goal(Goal.ENDURANCE).environment(Environment.OUTDOORS)
				.focus(Focus.COMPLEMENTARY).category(Category.SPORT)
				.resources(EnumSet.of(Resource.BICYCLE, Resource.BANDS)).daysPerWeek(5).durationMinutes(47)
				.level("Intermedio").experience("dos años").space("parque").preferences("sin saltos")
				.limitations("rodilla").historyConsent(true).build();
		db.saveProfile(profile);
		assertNotNull(db.loadProfile());
		db.close();
		db = new EnerGymDatabase(context, uid);
		assertEquals(profile, db.loadProfile());
	}

	@Test
	public void localEraseRemovesOwnProfileVersionsAndSessions() throws Exception {
		db.saveProfile(new TrainingProfile.Builder().build());
		accepted();
		assertFalse(routines.versions().isEmpty());
		db.clearLocalData();
		assertNull(db.loadProfile());
		assertTrue(routines.versions().isEmpty());
		assertTrue(sessions.pending().isEmpty());
		assertTrue(db.pendingSyncRecords().isEmpty());
	}

	@Test
	public void outboxAcknowledgesOnlyExactRevisionAndOwner() {
		db.saveProfile(new TrainingProfile.Builder().build());
		assertEquals(1, db.pendingSyncRecords().size());
		SyncRecord first = db.pendingSyncRecords().get(0);
		db.saveProfile(new TrainingProfile.Builder().preferences("nuevo").build());
		assertFalse(db.acknowledge(first.getKind(), first.getSyncId(), first.getRevision()));
		assertEquals(1, db.pendingSyncRecords().size());
		SyncRecord latest = db.pendingSyncRecords().get(0);
		assertTrue(latest.getRevision() > first.getRevision());
		assertEquals(uid, latest.getOwnerUid());
		assertTrue(db.acknowledge(latest.getKind(), latest.getSyncId(), latest.getRevision()));
		assertTrue(db.pendingSyncRecords().isEmpty());
	}

	@Test
	public void exportedSnapshotRestoresAfterEraseAndIsIdempotent() throws Exception {
		TrainingProfile profile = new TrainingProfile.Builder().historyConsent(true).preferences("persistir").build();
		db.saveProfile(profile);
		Routine first = accepted();
		Session draft =
			new Session(first).withResult("bodyweight-squat", Collections.singletonMap("repetitions", "21"));
		long id = sessions.saveDraft(draft);
		assertTrue(id > 0);
		sessions.finish(id);
		String snapshot = db.exportSnapshot();
		assertFalse(snapshot.isEmpty());
		db.clearLocalData();
		db.importSnapshot(snapshot);
		db.importSnapshot(snapshot);
		assertEquals(profile, db.loadProfile());
		assertEquals(1, routines.versions().size());
		assertEquals(1, sessions.history().size());
		assertEquals(draft.getSyncId(), sessions.history().get(0).getSyncId());
		assertEquals("21", sessions.history().get(0).getResults().get("bodyweight-squat").get("repetitions"));
		assertEquals(first.getItems().get(0).getExercise().getName(),
			sessions.history().get(0).getRoutine().getItems().get(0).getExercise().getName());
		assertTrue(db.pendingSyncRecords().isEmpty());
	}

	@Test
	public void remoteSnapshotNeverOverwritesPendingLocalProfileOrDraft() throws Exception {
		db.saveProfile(new TrainingProfile.Builder().preferences("cloud").build());
		Session session = new Session(accepted());
		long id = sessions.saveDraft(session);
		assertTrue(id > 0);
		String old = db.exportSnapshot();
		db.saveProfile(new TrainingProfile.Builder().preferences("local").build());
		sessions.saveDraft(session.withResult("bodyweight-squat", Collections.singletonMap("repetitions", "30")));
		db.importSnapshot(old);
		assertEquals("local", db.loadProfile().getPreferences());
		assertEquals("30", sessions.load(id).getResults().get("bodyweight-squat").get("repetitions"));
		assertFalse(db.pendingSyncRecords().isEmpty());
	}

	@Test
	public void invalidOrForeignSnapshotsFailAtomically() throws Exception {
		accepted();
		String snapshot = db.exportSnapshot();
		assertFalse(snapshot.isEmpty());
		try (EnerGymDatabase other = new EnerGymDatabase(context, "foreign-" + uid)) {
			assertThrows(IllegalArgumentException.class, () -> other.importSnapshot(snapshot));
			assertTrue(new RoutineRepository(other).versions().isEmpty());
		}
		db.clearLocalData();
		assertThrows(IllegalArgumentException.class,
			() -> db.importSnapshot(snapshot.replace("\"schemaVersion\":1", "\"schemaVersion\":99")));
		assertThrows(IllegalArgumentException.class, () -> db.importSnapshot("{broken"));
		assertTrue(routines.versions().isEmpty());
	}

	@Test
	public void oldUploadAcknowledgmentCannotClearProfileRecreatedAfterLocalErase() {
		db.saveProfile(new TrainingProfile.Builder().preferences("antes").build());
		assertEquals(1, db.pendingSyncRecords().size());
		SyncRecord old = db.pendingSyncRecords().get(0);
		db.clearLocalData();
		db.saveProfile(new TrainingProfile.Builder().preferences("después").build());
		assertFalse("old upload must not acknowledge new local data",
			db.acknowledge(old.getKind(), old.getSyncId(), old.getRevision()));
		assertEquals(1, db.pendingSyncRecords().size());
	}

	@Test
	public void remoteCannotChangeExistingSessionIdentityAndImportRollsBackOtherWrites() throws Exception {
		Session original = new Session(accepted());
		long id = sessions.saveDraft(original);
		assertTrue(id > 0);
		for (SyncRecord pending : db.pendingSyncRecords())
			db.acknowledge(pending.getKind(), pending.getSyncId(), pending.getRevision());
		Session changedIdentity = new Session(original.getSyncId(), original.getRoutine(), Collections.emptyMap(),
			original.getStartedAt() + 1000, 0, Session.Status.DRAFT);
		long future = System.currentTimeMillis() + 100000;
		SyncRecord profile = new SyncRecord(SyncRecord.Kind.PROFILE, "profile", uid,
			SnapshotCodec.encodeProfile(new TrainingProfile.Builder().preferences("no debe guardar").build()), 1,
			future, SyncRecord.State.CLEAN);
		SyncRecord badSession = new SyncRecord(SyncRecord.Kind.SESSION, original.getSyncId(), uid,
			SnapshotCodec.encodeSession(changedIdentity), 2, future, SyncRecord.State.CLEAN);
		assertThrows(IllegalArgumentException.class, () -> db.importSyncRecords(Arrays.asList(profile, badSession)));
		assertNull(db.loadProfile());
		assertEquals(original.getStartedAt(), sessions.load(id).getStartedAt());
	}

	@Test
	public void restoredDraftCanBeEditedAgainstRemappedLocalRoutineVersion() throws Exception {
		Session original = new Session(accepted());
		long id = sessions.saveDraft(original);
		assertTrue(id > 0);
		String snapshot = db.exportSnapshot();
		db.clearLocalData();
		db.importSnapshot(snapshot);
		Session recovered = sessions.pending().get(0);
		assertNotEquals(original.getRoutine().getVersionId(), recovered.getRoutine().getVersionId());
		Session updated = recovered.withResult("bodyweight-squat", Collections.singletonMap("loadKg", "3"));
		long restoredId = sessions.saveDraft(updated);
		assertTrue(restoredId > 0);
		assertEquals("3", sessions.load(restoredId).getResults().get("bodyweight-squat").get("loadKg"));
	}

	@Test
	public void inconsistentBatchRoutineAndSessionSnapshotsAreRejectedBeforeAnyWrite() throws Exception {
		Routine routine = proposal();
		Routine changed =
			new Routine(routine.getProposalId(), 123, "Otro snapshot", routine.getProfile(), routine.getItems(),
				routine.getExplanation());
		Session inconsistent = new Session(changed);
		long timestamp = System.currentTimeMillis();
		SyncRecord profile =
			new SyncRecord(SyncRecord.Kind.PROFILE, "profile", uid, SnapshotCodec.encodeProfile(routine.getProfile()),
				1, timestamp, SyncRecord.State.CLEAN);
		SyncRecord session = new SyncRecord(SyncRecord.Kind.SESSION, inconsistent.getSyncId(), uid,
			SnapshotCodec.encodeSession(inconsistent), 2, timestamp, SyncRecord.State.CLEAN);
		SyncRecord version =
			new SyncRecord(SyncRecord.Kind.ROUTINE, routine.getProposalId(), uid, SnapshotCodec.encodeRoutine(routine),
				3, timestamp, SyncRecord.State.CLEAN);
		// La sesión llega antes que la rutina: el orden del lote no cambia su integridad.
		assertThrows(IllegalArgumentException.class,
			() -> db.importSyncRecords(Arrays.asList(profile, session, version)));
		assertNull(db.loadProfile());
		assertTrue(routines.versions().isEmpty());
		assertTrue(sessions.pending().isEmpty());
	}

	@Test
	public void standaloneSessionRestoreMaterializesItsAcceptedSnapshotAndRemainsEditable() throws Exception {
		Session standalone = new Session(proposal().withVersionId(123));
		SyncRecord remote = new SyncRecord(SyncRecord.Kind.SESSION, standalone.getSyncId(), uid,
			SnapshotCodec.encodeSession(standalone), 1, System.currentTimeMillis(), SyncRecord.State.CLEAN);
		db.importSyncRecords(Collections.singletonList(remote));
		assertEquals(1, routines.versions().size());
		assertEquals(1, sessions.pending().size());
		assertTrue(db.pendingSyncRecords().isEmpty());
		Session restored = sessions.pending().get(0);
		assertEquals(routines.versions().get(0).getVersionId(), restored.getRoutine().getVersionId());
		long id =
			sessions.saveDraft(restored.withResult("bodyweight-squat", Collections.singletonMap("repetitions", "24")));
		assertTrue(id > 0);
		assertEquals("24", sessions.load(id).getResults().get("bodyweight-squat").get("repetitions"));
		db.importSyncRecords(Collections.singletonList(remote));
		assertEquals("24", sessions.load(id).getResults().get("bodyweight-squat").get("repetitions"));
		assertEquals(1, routines.versions().size());
	}

	@Test
	public void inconsistentSessionCannotReplacePendingLocalRoutine() throws Exception {
		Routine local = accepted();
		Routine changed =
			new Routine(local.getProposalId(), 123, "Remoto distinto", local.getProfile(), local.getItems(),
				local.getExplanation());
		Session inconsistent = new Session(changed);
		SyncRecord remote = new SyncRecord(SyncRecord.Kind.SESSION, inconsistent.getSyncId(), uid,
			SnapshotCodec.encodeSession(inconsistent), 1, System.currentTimeMillis() + 1000, SyncRecord.State.CLEAN);
		assertThrows(IllegalArgumentException.class, () -> db.importSyncRecords(Collections.singletonList(remote)));
		assertEquals(local.getTitle(), routines.load(local.getVersionId()).getTitle());
		assertTrue(sessions.pending().isEmpty());
		assertEquals(1, db.pendingSyncRecords().size());
	}
}
