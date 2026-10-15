package com.energym.app.ui;

import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.MetricType;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Estado del buscador aislado por cuenta y borrador, sin dependencias Android. */
public final class ExerciseBrowserStateTest {

	@Test public void currentBindingCapturesFocusAndCursor() {
		ExerciseBrowserState state = populated();
		state.capture(state.bindingRevision(), true, 8);
		assertTrue(state.focused);
		assertEquals(8, state.selection);
	}

	@Test public void previousOwnerBindingCannotOverwriteResetFocus() {
		ExerciseBrowserState state = populated();
		long previous = state.bindingRevision();
		state.resetFor("owner-b", 4);
		assertEquals(previous + 1, state.bindingRevision());
		state.capture(previous, true, 8);
		assertFalse(state.focused);
		assertEquals(0, state.selection);
		state.capture(state.bindingRevision(), true, 3);
		assertTrue(state.focused);
		assertEquals(3, state.selection);
	}

	@Test public void previousDraftBindingCannotOverwriteResetFocus() {
		ExerciseBrowserState state = populated();
		long previous = state.bindingRevision();
		state.resetFor("owner-a", 5);
		assertEquals(previous + 1, state.bindingRevision());
		state.capture(previous, true, 8);
		assertFalse(state.focused);
		assertEquals(0, state.selection);
	}

	@Test public void unchangedOwnerAndDraftKeepExistingBindingValid() {
		ExerciseBrowserState state = populated();
		long previous = state.bindingRevision();
		state.resetFor(new String("owner-a"), 4);
		assertEquals(previous, state.bindingRevision());
		state.capture(previous, true, 9);
		assertTrue(state.focused);
		assertEquals(9, state.selection);
	}

	@Test public void accountRoundTripDoesNotRestoreAnOldBinding() {
		ExerciseBrowserState state = populated();
		long previous = state.bindingRevision();
		state.resetFor("owner-b", 4);
		state.resetFor("owner-a", 4);
		state.capture(previous, true, 8);
		assertFalse(state.focused);
		assertEquals(0, state.selection);
	}

	@Test public void currentBindingCapturesBlurAndClampsNegativeCursor() {
		ExerciseBrowserState state = populated();
		state.capture(state.bindingRevision(), false, -1);
		assertFalse(state.focused);
		assertEquals(0, state.selection);
	}

	@Test public void sameOwnerAndDraftPreserveNavigationState() {
		ExerciseBrowserState state = populated();
		state.resetFor(new String("owner-a"), 4);
		assertEquals("sentadilla", state.query);
		assertEquals("calisthenics", state.discipline);
		assertEquals("HOME", state.environment);
		assertEquals("NONE", state.material);
		assertEquals("STRENGTH", state.goal);
		assertTrue(state.expanded);
		assertTrue(state.focused);
		assertFalse(state.onlyCompatible);
		assertEquals(36, state.visible);
		assertEquals(5, state.selection);
	}

	@Test public void changingOwnerResetsSearchAndCompatibilityPreference() {
		ExerciseBrowserState state = populated();
		state.resetFor("owner-b", 4);
		assertReset(state);
		state.query = "flexión";
		state.resetFor("owner-b", 4);
		assertEquals("flexión", state.query);
	}

	@Test public void changingDraftResetsSearchWithoutChangingOwner() {
		ExerciseBrowserState state = populated();
		state.resetFor("owner-a", 5);
		assertReset(state);
		state.query = "plancha";
		state.resetFor("owner-a", 5);
		assertEquals("plancha", state.query);
	}

	@Test public void signingOutResetsSearchAndStableSignedOutStateIsPreserved() {
		ExerciseBrowserState state = populated();
		state.resetFor(null, 4);
		assertReset(state);
		state.query = "zancada";
		state.resetFor(null, 4);
		assertEquals("zancada", state.query);
	}

	@Test public void clearingFiltersKeepsQueryAndEditingState() {
		ExerciseBrowserState state = populated();
		state.clearFilters();
		assertEquals("", state.discipline);
		assertEquals("", state.environment);
		assertEquals("", state.material);
		assertEquals("", state.goal);
		assertEquals(12, state.visible);
		assertEquals("sentadilla", state.query);
		assertTrue(state.expanded);
		assertTrue(state.focused);
		assertFalse(state.onlyCompatible);
		assertEquals(5, state.selection);
	}

	@Test public void selectedFiltersConstrainRealCatalogSearch() {
		ExerciseBrowserState state = populated();
		Exercise home = exercise("home", Environment.HOME, Goal.STRENGTH, Collections.emptySet());
		Exercise outdoors = exercise("outdoors", Environment.OUTDOORS, Goal.STRENGTH, Collections.emptySet());
		Exercise loaded = exercise("loaded", Environment.HOME, Goal.STRENGTH, EnumSet.of(Resource.DUMBBELLS));
		Exercise mobility = exercise("mobility", Environment.HOME, Goal.MOBILITY, Collections.emptySet());
		ExerciseSearch search = new ExerciseSearch(Arrays.asList(home, outdoors, loaded, mobility));
		assertEquals(Collections.singletonList(home), search.find("", state.filters()));
		assertEquals(4, state.activeCount());
	}

	@Test public void resourceSelectionIsPassedToSearch() {
		ExerciseBrowserState state = new ExerciseBrowserState();
		state.material = "DUMBBELLS";
		Exercise bodyweight = exercise("bodyweight", Environment.HOME, Goal.STRENGTH, Collections.emptySet());
		Exercise loaded = exercise("loaded", Environment.HOME, Goal.STRENGTH, EnumSet.of(Resource.DUMBBELLS));
		assertEquals(Collections.singletonList(loaded),
			new ExerciseSearch(Arrays.asList(bodyweight, loaded)).find("", state.filters()));
		assertEquals(1, state.activeCount());
	}

	@Test public void invalidRestoredEnumsAreIgnoredAndNotCounted() {
		ExerciseBrowserState state = new ExerciseBrowserState();
		state.query = "sentadilla";
		state.environment = "OLD_ENVIRONMENT";
		state.material = "OLD_RESOURCE";
		state.goal = "OLD_GOAL";
		ExerciseSearch.Filters filters = state.filters();
		assertNull(filters.environment);
		assertEquals("", filters.material);
		assertNull(filters.goal);
		assertEquals(0, state.activeCount());
		state.discipline = "calisthenics";
		assertEquals(1, state.activeCount());
	}

	@Test public void nullRestoredFiltersBehaveAsNoFilters() {
		ExerciseBrowserState state = new ExerciseBrowserState();
		state.discipline = state.environment = state.material = state.goal = null;
		ExerciseSearch.Filters filters = state.filters();
		assertEquals("", filters.discipline);
		assertNull(filters.environment);
		assertEquals("", filters.material);
		assertNull(filters.goal);
		assertEquals(0, state.activeCount());
	}

	private static ExerciseBrowserState populated() {
		ExerciseBrowserState state = new ExerciseBrowserState();
		state.resetFor("owner-a", 4);
		state.query = "sentadilla";
		state.discipline = "calisthenics";
		state.environment = "HOME";
		state.material = "NONE";
		state.goal = "STRENGTH";
		state.expanded = state.focused = true;
		state.onlyCompatible = false;
		state.visible = 36;
		state.selection = 5;
		return state;
	}

	private static void assertReset(ExerciseBrowserState state) {
		assertEquals("", state.query);
		assertEquals("", state.discipline);
		assertEquals("", state.environment);
		assertEquals("", state.material);
		assertEquals("", state.goal);
		assertFalse(state.expanded);
		assertFalse(state.focused);
		assertTrue(state.onlyCompatible);
		assertEquals(12, state.visible);
		assertEquals(0, state.selection);
	}

	private static Exercise exercise(String id, Environment environment, Goal goal, Set<Resource> resources) {
		return new Exercise(id, "Sentadilla " + id, MetricType.STRENGTH, EnumSet.of(goal), EnumSet.of(environment),
			resources, Collections.singleton("calisthenics"), Exercise.Coverage.SPECIFIC, false,
			"Técnica de sentadilla", Collections.emptyList());
	}
}
