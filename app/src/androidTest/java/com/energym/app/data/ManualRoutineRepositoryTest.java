package com.energym.app.data;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.energym.app.domain.*;
import com.energym.app.sync.CloudDocumentCodec;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Sólo propietarios fixture aleatorios y SQLite local; no Google, demo ni llamadas Firebase. */
@RunWith(AndroidJUnit4.class)
public final class ManualRoutineRepositoryTest {
	private Context context;
	private String owner;
	private EnerGymDatabase db;
	private RoutineRepository routines;

	@Before public void open() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		owner = "manual-fixture-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, owner);
		routines = new RoutineRepository(db);
	}

	@After public void close() {
		if (db != null) {
			db.clearLocalData();
			db.close();
		}
	}

	private Routine manual() {
		Routine template = ManualRoutineCodecTest.legacy();
		TrainingProfile own = template.getProfile().toBuilder().durationMinutes(15).preferences("Sólo esta rutina").build();
		return new Routine(UUID.randomUUID().toString(), 0, "Mi rutina", own, template.getItems(), "Manual",
			new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
	}

	private Routine copy(Routine base, String id, List<RoutineItem> items, RoutineMetadata metadata) {
		return new Routine(id, 0, base.getTitle(), base.getProfile(), items, base.getExplanation(), metadata);
	}

	@Test public void manualSaveUsesItsOwnProfileWithoutChangingTheGeneralProfileOrAiCas() {
		TrainingProfile general = new TrainingProfile.Builder().preferences("Perfil general").build();
		db.saveProfile(general);
		Routine manual = manual();
		long id = routines.saveManual(manual);
		assertTrue(id > 0);
		assertEquals(general, db.loadProfile());
		assertEquals(manual.getProfile(), routines.load(id).getProfile());
		assertFalse(routines.load(id).isProgressionEnabled());
		long revision = db.find(SyncRecord.Kind.ROUTINE, manual.getProposalId()).record.getRevision();
		assertEquals(id, routines.saveManual(manual));
		assertEquals(revision, db.find(SyncRecord.Kind.ROUTINE, manual.getProposalId()).record.getRevision());
		Routine ai = copy(manual, UUID.randomUUID().toString(), manual.getItems(),
			new RoutineMetadata(RoutineMetadata.Origin.AI, true));
		assertThrows(IllegalArgumentException.class, () -> routines.saveAccepted(ai));
		assertThrows(IllegalArgumentException.class, () -> routines.acceptProposal(ai, manual.getProfile()));
		assertEquals(general, db.loadProfile());
	}

	@Test public void manualSaveWithoutGeneralProfileDoesNotCreateOneAndRejectsWrongOrigin() {
		Routine manual = manual();
		routines.saveManual(manual);
		assertNull(db.loadProfile());
		for (RoutineMetadata metadata : Arrays.asList(null,
			new RoutineMetadata(RoutineMetadata.Origin.AI, true), new RoutineMetadata(RoutineMetadata.Origin.LOCAL_RULES, true))) {
			Routine wrong = copy(manual, UUID.randomUUID().toString(), manual.getItems(), metadata);
			assertThrows(IllegalArgumentException.class, () -> routines.saveManual(wrong));
		}
		assertEquals(1, routines.versions().size());
	}

	@Test public void immutableVersionCannotBeReusedWithEditedTitleOrProgressionPermission() {
		Routine manual = manual();
		long id = routines.saveManual(manual);
		Routine changed = new Routine(manual.getProposalId(), 0, "Editada", manual.getProfile(), manual.getItems(),
			manual.getExplanation(), new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
		assertThrows(IllegalArgumentException.class, () -> routines.saveManual(changed));
		assertEquals("Mi rutina", routines.load(id).getTitle());
		assertFalse(routines.load(id).isProgressionEnabled());
		assertEquals(1, routines.versions().size());
	}

	@Test public void strictPrescriptionsCompatibilityDuplicatesAndTimeAreCheckedBeforeWriting() {
		Routine manual = manual();
		RoutineItem template = manual.getItems().get(0);
		for (String[] invalid : new String[][]{{"sets", "0"}, {"repetitions", "1.5"}, {"loadKg", "10"},
			{"restSeconds", "3601"}, {"effort", "7"}, {"sets", "21"}, {"sets", ""}}) {
			Map<String, String> prescription = new LinkedHashMap<>(template.getPrescription());
			prescription.put(invalid[0], invalid[1]);
			Routine invalidRoutine = copy(manual, UUID.randomUUID().toString(), Collections.singletonList(
				new RoutineItem(template.getExercise(), prescription, 5, "Fixture")), manual.getMetadata());
			assertThrows(IllegalArgumentException.class, () -> routines.saveManual(invalidRoutine));
		}
		Routine duplicate = copy(manual, UUID.randomUUID().toString(), Arrays.asList(template, template), manual.getMetadata());
		assertThrows(IllegalArgumentException.class, () -> routines.saveManual(duplicate));
		Routine tooLong = copy(manual, UUID.randomUUID().toString(), Collections.singletonList(
			new RoutineItem(template.getExercise(), template.getPrescription(), 16, "Fixture")), manual.getMetadata());
		assertThrows(IllegalArgumentException.class, () -> routines.saveManual(tooLong));
		TrainingProfile incompatible = manual.getProfile().toBuilder().environment(TrainingProfile.Environment.POOL).build();
		Routine wrongEnvironment = new Routine(UUID.randomUUID().toString(), 0, "Fixture", incompatible,
			manual.getItems(), "Fixture", manual.getMetadata());
		assertThrows(IllegalArgumentException.class, () -> routines.saveManual(wrongEnvironment));
		assertTrue(db.pendingSyncRecords().isEmpty());
		assertNull(db.loadProfile());
	}

	@Test public void initialRestoreBarrierAlsoRejectsManualAcceptance() {
		db.requireInitialRestore();
		db.inspectInitialCache();
		assertThrows(IllegalStateException.class, () -> routines.saveManual(manual()));
		assertTrue(db.pendingSyncRecords().isEmpty());
		assertNull(db.loadProfile());
	}

	@Test public void cloudRoundtripRestoresManualMetadataAndSessionSnapshotWithinTheSameOwner() {
		Routine manual = manual();
		long id = routines.saveManual(manual);
		Routine accepted = routines.load(id);
		Session session = new Session("fixture-session", accepted, Collections.emptyMap(), 100, 0, Session.Status.DRAFT);
		new SessionRepository(db).saveDraft(session);
		List<SyncRecord> remote = new ArrayList<>();
		for (SyncRecord record : db.pendingSyncRecords())
			remote.add(CloudDocumentCodec.decode(owner, record.getKind(), record.getSyncId(), CloudDocumentCodec.encode(record, 1)).record);
		db.clearLocalData();
		db.importSyncRecords(remote);
		Routine restored = routines.versions().get(0);
		Session restoredDraft = new SessionRepository(db).pending().get(0);
		assertEquals(manual.getMetadata(), restored.getMetadata());
		assertEquals(restored.getVersionId(), restoredDraft.getRoutine().getVersionId());
		assertEquals(manual.getMetadata(), restoredDraft.getRoutine().getMetadata());
		assertEquals(SnapshotCodec.encodeRoutine(accepted.withVersionId(0)),
			SnapshotCodec.encodeRoutine(restoredDraft.getRoutine().withVersionId(0)));
		assertTrue(db.pendingSyncRecords().isEmpty());
		String otherOwner = "manual-fixture-" + UUID.randomUUID();
		try (EnerGymDatabase other = new EnerGymDatabase(context, otherOwner)) {
			assertNull(new RoutineRepository(other).load(restored.getVersionId()));
			assertTrue(new RoutineRepository(other).versions().isEmpty());
		}
	}
}
