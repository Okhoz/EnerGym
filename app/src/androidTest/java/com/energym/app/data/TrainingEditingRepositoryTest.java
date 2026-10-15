package com.energym.app.data;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.energym.app.domain.*;
import com.energym.app.nutrition.*;
import com.energym.app.sync.CloudDocumentCodec;
import java.time.LocalDate;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Propietario fixture aleatorio: nunca modifica una cuenta Google ni hace peticiones Firebase. */
@RunWith(AndroidJUnit4.class)
public final class TrainingEditingRepositoryTest {
	private Context context;
	private String owner;
	private EnerGymDatabase db;
	private RoutineRepository routines;
	private SessionRepository sessions;
	private TrainingArchiveRepository archive;

	@Before public void open() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		owner = "edit-fixture-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, owner);
		routines = new RoutineRepository(db);
		sessions = new SessionRepository(db);
		archive = new TrainingArchiveRepository(db);
	}

	@After public void close() {
		if (db != null) { db.clearLocalData(); db.close(); }
	}

	private Routine routine(String title) {
		Routine template = ManualRoutineCodecTest.legacy();
		Routine value = new Routine(UUID.randomUUID().toString(), 0, title, template.getProfile(),
			template.getItems(), "Prueba aislada", new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
		return routines.load(routines.saveManual(value));
	}

	@Test public void replacingRoutineHidesTheOldVersionButPreservesItsSessionSnapshot() {
		Routine original = routine("Original");
		Session session = new Session(original);
		long sessionId = sessions.saveDraft(session);
		String previous = SnapshotCodec.encodeRoutine(original);
		Routine replacement = new Routine(UUID.randomUUID().toString(), 0, "Editada", original.getProfile(),
			original.getItems(), "Edición", new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
		Routine saved = routines.replaceManual(original.getProposalId(), replacement);
		assertEquals(1, routines.versions().size());
		assertEquals("Editada", routines.versions().get(0).getTitle());
		assertFalse(saved.isProgressionEnabled());
		assertEquals(previous, SnapshotCodec.encodeRoutine(sessions.load(sessionId).getRoutine()));
		assertEquals(previous, SnapshotCodec.encodeRoutine(routines.load(original.getVersionId())));
		assertEquals(saved.getVersionId(), routines.replaceManual(original.getProposalId(), replacement).getVersionId());
		assertEquals(1, routines.versions().size());
	}

	@Test public void archiveSurvivesSnapshotRestoreAndCannotResurrectWithOldRoutineOrDraft() {
		Routine original = routine("Original");
		Session draft = new Session(original);
		long sessionId = sessions.saveDraft(draft);
		archive.archive(SyncRecord.Kind.ROUTINE, original.getProposalId());
		assertTrue(routines.versions().isEmpty());
		// Un borrador existente sigue pudiéndose completar aunque su rutina deje de estar en la biblioteca.
		sessions.saveDraft(draft.withResult("fixture", original.getItems().get(0).getPrescription()));
		archive.archive(SyncRecord.Kind.SESSION, draft.getSyncId());
		assertNull(sessions.load(sessionId));
		assertTrue(sessions.pending().isEmpty());
		String snapshot = db.exportSnapshot();
		db.clearLocalData();
		db.importSnapshot(snapshot);
		assertTrue(routines.versions().isEmpty());
		assertTrue(sessions.pending().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> sessions.saveDraft(draft));
		assertThrows(IllegalArgumentException.class, () -> sessions.saveDraft(new Session(original)));
		assertThrows(IllegalArgumentException.class, () -> sessions.finish(sessionId));
		for (SyncRecord record : restoredRecords()) {
			if (record.getKind() != SyncRecord.Kind.TRAINING_ARCHIVE) continue;
			Map<String, Object> document = CloudDocumentCodec.encode(record, 1);
			assertEquals("trainingArchive", CloudDocumentCodec.collection(record.getKind()));
			assertEquals(true, document.get("terminal"));
			assertEquals(record.getPayload(), CloudDocumentCodec.decode(owner, record.getKind(), record.getSyncId(), document).record.getPayload());
		}
	}

	@Test public void finishedSessionDeletionRecalculatesHistoryWithoutDeletingTheRoutine() {
		Routine original = routine("Original");
		Session draft = new Session(original).withResult("fixture", original.getItems().get(0).getPrescription());
		long sessionId = sessions.saveDraft(draft);
		sessions.finish(sessionId);
		assertEquals(1, sessions.history().size());
		archive.archive(SyncRecord.Kind.SESSION, draft.getSyncId());
		assertTrue(sessions.history().isEmpty());
		assertEquals(1, routines.versions().size());
		int count = db.pendingSyncRecords().size();
		archive.archive(SyncRecord.Kind.SESSION, draft.getSyncId());
		assertEquals(count, db.pendingSyncRecords().size());
	}

	@Test public void unknownOwnerAndInvalidReplacementCannotCreateAnArchiveOrChangeTheOriginal() {
		Routine original = routine("Original");
		Routine invalid = new Routine(UUID.randomUUID().toString(), 0, "Vacía", original.getProfile(),
			Collections.emptyList(), "Inválida", new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
		assertThrows(IllegalArgumentException.class, () -> routines.replaceManual(original.getProposalId(), invalid));
		assertFalse(archive.contains(SyncRecord.Kind.ROUTINE, original.getProposalId()));
		assertEquals(1, routines.versions().size());
		EnerGymDatabase other = new EnerGymDatabase(context, "other-" + owner);
		try {
			assertThrows(IllegalArgumentException.class, () -> new TrainingArchiveRepository(other)
				.archive(SyncRecord.Kind.ROUTINE, original.getProposalId()));
			assertThrows(IllegalArgumentException.class, () -> other.importSnapshot(db.exportSnapshot()));
			assertTrue(other.pendingSyncRecords().isEmpty());
		} finally { other.clearLocalData(); other.close(); }
	}

	@Test public void foodEditPreservesCloudIdentityAndTombstonesBlockFurtherEdits() {
		NutritionRepository food = new NutritionRepository(db);
		FoodEntry original = FoodEntry.create(LocalDate.of(2026, 10, 15), FoodEntry.Meal.LUNCH,
			"Arroz", "", FoodEntry.Unit.G, 100, 360, 7, 79, 1);
		food.add(original);
		SyncRecord before = db.find(SyncRecord.Kind.FOOD, original.id).record;
		FoodEntry corrected = new FoodEntry(original.id, original.day, FoodEntry.Meal.DINNER,
			"Arroz corregido", "", FoodEntry.Unit.G, 250, 350, 7, 75, 1, original.createdAt);
		food.update(corrected);
		assertEquals(1, food.food(LocalDate.MIN).size());
		assertEquals(875, food.food(LocalDate.MIN).get(0).kcal(), 0.001);
		SyncRecord after = db.find(SyncRecord.Kind.FOOD, original.id).record;
		assertEquals(CloudDocumentCodec.encode(before, 1).get("identity"), CloudDocumentCodec.encode(after, 2).get("identity"));
		assertTrue(after.getRevision() > before.getRevision());
		food.remove(original.id);
		assertThrows(IllegalArgumentException.class, () -> food.update(corrected));
		assertTrue(food.food(LocalDate.MIN).isEmpty());
	}

	@Test public void foodEditRejectsIdentityOrDateChangesAndUnknownEntries() {
		NutritionRepository food = new NutritionRepository(db);
		FoodEntry original = FoodEntry.create(LocalDate.of(2026, 10, 15), FoodEntry.Meal.LUNCH,
			"Arroz", "", FoodEntry.Unit.G, 100, 360, 7, 79, 1);
		food.add(original);
		for (FoodEntry invalid : Arrays.asList(
			new FoodEntry("missing", original.day, original.meal, original.name, "", original.unit,
				200, 360, 7, 79, 1, original.createdAt),
			new FoodEntry(original.id, original.day.minusDays(1), original.meal, original.name, "", original.unit,
				200, 360, 7, 79, 1, original.createdAt),
			new FoodEntry(original.id, original.day, original.meal, original.name, "", original.unit,
				200, 360, 7, 79, 1, original.createdAt + 1)))
			assertThrows(IllegalArgumentException.class, () -> food.update(invalid));
		assertEquals(360, food.food(LocalDate.MIN).get(0).kcal(), 0.001);
	}

	@Test public void remoteFoodDeletionWinsOverAnUnsyncedLocalEditAndCannotBeResurrected() {
		NutritionRepository food = new NutritionRepository(db);
		FoodEntry original = FoodEntry.create(LocalDate.of(2026, 10, 15), FoodEntry.Meal.LUNCH,
			"Arroz", "", FoodEntry.Unit.G, 100, 360, 7, 79, 1);
		food.add(original);
		SyncRecord active = db.find(SyncRecord.Kind.FOOD, original.id).record;
		food.update(new FoodEntry(original.id, original.day, original.meal, original.name, "", original.unit,
			200, 360, 7, 79, 1, original.createdAt));
		// El reloj del dispositivo remoto puede ir por detrás; la lápida válida sigue siendo definitiva.
		SyncRecord deleted = new SyncRecord(SyncRecord.Kind.FOOD, original.id, owner,
			NutritionRepository.tombstone(original), active.getRevision(), active.getUpdatedAt(), SyncRecord.State.CLEAN);
		db.importSyncRecords(Collections.singletonList(deleted));
		assertTrue(food.food(LocalDate.MIN).isEmpty());
		assertNull(db.pendingSyncRecords().stream().filter(r -> r.getKind() == SyncRecord.Kind.FOOD).findFirst().orElse(null));
		db.importSyncRecords(Collections.singletonList(new SyncRecord(SyncRecord.Kind.FOOD, original.id, owner,
			active.getPayload(), active.getRevision() + 10, active.getUpdatedAt() + 10000, SyncRecord.State.CLEAN)));
		assertTrue(food.food(LocalDate.MIN).isEmpty());
	}

	private List<SyncRecord> restoredRecords() {
		try {
			org.json.JSONArray rows = new org.json.JSONObject(db.exportSnapshot()).getJSONArray("records");
			List<SyncRecord> result = new ArrayList<>();
			for (int i = 0; i < rows.length(); i++) {
				org.json.JSONObject row = rows.getJSONObject(i);
				result.add(new SyncRecord(SyncRecord.Kind.valueOf(row.getString("kind")), row.getString("syncId"), owner,
					row.getJSONObject("payload").toString(), row.getLong("revision"), row.getLong("updatedAt"), SyncRecord.State.CLEAN));
			}
			return result;
		} catch (org.json.JSONException e) { throw new AssertionError(e); }
	}
}
