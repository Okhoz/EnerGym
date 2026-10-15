package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RoutineEditingTest {
	@Test public void unavailableCatalogExerciseCannotSilentlySaveAnEmptyReplacement() {
		Exercise old = new Exercise("old", "Antiguo", MetricType.STRENGTH, EnumSet.of(Goal.STRENGTH),
			EnumSet.of(Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
		Routine original = new Routine("original", 1, "Anterior", new TrainingProfile.Builder().build(),
			Collections.singletonList(new RoutineItem(old, Collections.singletonMap("sets", "2"), 5, "Anterior")), "Anterior");
		Store store = new Store();
		ManualRoutineController c = new ManualRoutineController(store, Collections.emptyList(), () -> {}, r -> {});
		c.activate("a"); c.edit(original);
		assertFalse(c.editing());
		assertFalse(c.error.isEmpty());
		assertTrue(c.entries().isEmpty());
		assertEquals(0, store.writes);
	}

	@Test public void aiRoutineBecomesAnEditableCopyAndReplacesOnlyItsLibraryVersion() {
		Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH, EnumSet.of(Goal.STRENGTH),
			EnumSet.of(Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList());
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sets", "3"); values.put("repetitions", "8"); values.put("loadKg", "0"); values.put("restSeconds", "60");
		Routine original = new Routine("original", 7, "Con IA", new TrainingProfile.Builder().build(),
			Collections.singletonList(new RoutineItem(squat, values, 5, "Referencia")), "IA",
			new RoutineMetadata(RoutineMetadata.Origin.AI, false));
		Store store = new Store();
		ManualRoutineController c = new ManualRoutineController(store, Collections.singletonList(squat), () -> {}, r -> {});
		c.activate("a");
		c.edit(original);
		assertTrue(c.editing());
		assertEquals("Con IA", c.title());
		assertFalse(c.progression());
		assertEquals(values, c.entries().get(0).values);
		assertEquals(0, store.writes);
		c.title("Mi versión"); c.input(0, "repetitions", "10"); c.save();
		assertEquals(1, store.writes);
		assertEquals("original", store.originalId);
		assertNotEquals(original.getProposalId(), store.value.getProposalId());
		assertEquals(RoutineMetadata.Origin.MANUAL, store.value.getMetadata().getOrigin());
		assertEquals("8", original.getItems().get(0).getPrescription().get("repetitions"));
		assertEquals("10", store.value.getItems().get(0).getPrescription().get("repetitions"));
		c.activate("b");
		assertFalse(c.editing());
	}

	private static final class Store implements AppStore {
		int writes; String originalId; Routine value;
		public void replaceManual(String original, Routine r, Callback<Routine> c) {
			writes++; originalId = original; value = r; c.success(r.withVersionId(8));
		}
		public void load(Callback<Snapshot> c) {}
		public void profile(TrainingProfile p, Callback<Void> c) {}
		public void accept(Routine r, Callback<Routine> c) { throw new AssertionError(); }
		public void draft(Session s, Callback<Long> c) {}
		public void openDraft(String id, Callback<OpenDraft> c) {}
		public void finish(long id, Callback<Void> c) {}
	}
}
