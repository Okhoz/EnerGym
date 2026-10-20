package com.energym.app.data;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.progress.Goal;
import com.energym.app.sync.CloudDocumentCodec;

import org.junit.*;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

/** Metas personales, avance apuntado y metas cumplidas: se guardan, viajan a la nube y vuelven igual. */
@RunWith(AndroidJUnit4.class)
public class GoalSyncTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);
	private String uid;
	private EnerGymDatabase db;
	private GoalRepository repo;

	@Before
	public void open() {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		uid = "goals-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, uid);
		repo = new GoalRepository(db);
	}

	@After
	public void close() {
		db.clearLocalData();
		db.close();
	}

	private SyncRecord pending(String id) {
		for (SyncRecord r : db.pendingSyncRecords())
			if (r.getKind() == SyncRecord.Kind.GOAL && r.getSyncId().equals(id))
				return r;
		return null;
	}

	@Test
	public void customGoalWithEntriesAndCompletionSurvivesStorageAndCloud() {
		Goal goal = Goal.custom("Leer libros", "libros", 12, 0).withEntry(DAY.minusDays(2), 1).withEntry(DAY, 3);
		repo.save(goal);
		Goal done = repo.goals().get(0).complete(1_700_000_000_000L);
		repo.save(done);
		Goal back = repo.goals().get(0);
		assertEquals("Leer libros", back.name);
		assertEquals("libros", back.customUnit);
		assertEquals(1_700_000_000_000L, back.completedAt);
		assertEquals(Arrays.asList(DAY.minusDays(2), DAY), new ArrayList<>(back.entries.keySet()));
		assertEquals(3, back.entries.get(DAY), 0);

		SyncRecord r = pending(goal.id);
		assertNotNull(r);
		SyncRecord cloud = CloudDocumentCodec.decode(uid, r.getKind(),
			CloudDocumentCodec.documentId(r.getKind(), r.getSyncId()), CloudDocumentCodec.encode(r, 2)).record;
		Goal remote = GoalRepository.decode(cloud.getPayload());
		assertEquals(back.entries, remote.entries);
		assertEquals(back.completedAt, remote.completedAt);
		// Misma identidad en la nube al editar: la regla de Firestore solo admite actualizar con la misma.
		Goal fresh = Goal.custom("Otra", "", 5, 0);
		assertEquals(GoalRepository.createdAt(GoalRepository.encode(goal)),
			GoalRepository.createdAt(GoalRepository.encode(done)));
		assertNotEquals(goal.id, fresh.id);
	}

	@Test
	public void goalsSavedBeforeThisVersionStillLoadUnchanged() {
		String old = "{\"id\":\"old-goal\",\"type\":\"SESSIONS_PER_WEEK\",\"target\":3,\"start\":0," +
			"\"exerciseId\":\"\",\"exerciseName\":\"\",\"createdAt\":1700000000000,\"deleted\":false}";
		Goal goal = GoalRepository.decode(old);
		assertFalse(goal.completed());
		assertTrue(goal.entries.isEmpty());
		assertEquals(GoalRepository.encode(goal), GoalRepository.canonical("old-goal", old));
		assertFalse(GoalRepository.encode(goal).contains("entries"));
		assertFalse(GoalRepository.encode(goal).contains("completedAt"));
	}
}
