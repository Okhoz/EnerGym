package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TrainingMutationControllerTest {
	private final Exercise exercise = new Exercise("squat", "Sentadilla", MetricType.STRENGTH,
		EnumSet.of(Goal.STRENGTH), EnumSet.of(Environment.HOME), Collections.emptySet(),
		Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());

	private Routine routine(String id, long version) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("sets", "2"); p.put("repetitions", "8"); p.put("loadKg", "0"); p.put("restSeconds", "60");
		return new Routine(id, version, id, new TrainingProfile.Builder().build(),
			Collections.singletonList(new RoutineItem(exercise, p, 5, "Ejemplo")), "Ejemplo");
	}

	private AppController app(Store store) {
		CatalogRepository catalog = new CatalogRepository(1, Collections.singletonList(new Sport("strength", "Fuerza",
			Sport.CoverageStatus.EXAMPLE_UNREVIEWED, Sport.CoverageStatus.PENDING_VALIDATION)), Collections.singletonList(exercise));
		AppController app = new AppController(store, catalog);
		app.activate("a");
		return app;
	}

	@Test public void deleteBlocksDuplicateMutationsAndStartUntilReloadChoosesTheNextRoutine() {
		Store store = new Store();
		Routine original = routine("original", 1), other = routine("other", 2);
		store.routines = Arrays.asList(original, other);
		AppController app = app(store);
		app.selectRoutine(original.getProposalId());
		app.deleteRoutine(original);
		assertTrue(app.trainingMutation);
		app.deleteRoutine(original); app.startSession();
		assertEquals(1, store.deletes);
		assertEquals(0, store.drafts);
		store.routines = Collections.singletonList(other);
		store.archived.success(null);
		assertFalse(app.trainingMutation);
		assertSame(other, app.routines.accepted);
	}

	@Test public void lateDeletionCallbackCannotChangeAnotherAccountOrItsSelection() {
		Store store = new Store();
		Routine original = routine("original", 1);
		store.routines = Collections.singletonList(original);
		AppController app = app(store);
		long revision = app.accountRevision();
		app.deleteRoutine(original);
		AppStore.Callback<Void> old = store.archived;
		Routine next = routine("next", 2);
		store.routines = Collections.singletonList(next);
		app.activate("b");
		assertTrue(revision != app.accountRevision());
		old.success(null);
		assertEquals("b", app.owner());
		assertSame(next, app.routines.accepted);
		assertFalse(app.trainingMutation);
	}

	private static final class Store implements AppStore {
		List<Routine> routines = Collections.emptyList();
		int deletes, drafts;
		Callback<Void> archived;
		public void archiveTraining(boolean routine, String id, Callback<Void> c) { deletes++; archived = c; }
		public void load(Callback<Snapshot> c) { c.success(new Snapshot(null, routines, Collections.emptyList(), Collections.emptyList())); }
		public void profile(TrainingProfile p, Callback<Void> c) {}
		public void accept(Routine r, Callback<Routine> c) {}
		public void draft(Session s, Callback<Long> c) { drafts++; }
		public void openDraft(String id, Callback<OpenDraft> c) {}
		public void finish(long id, Callback<Void> c) {}
	}
}
