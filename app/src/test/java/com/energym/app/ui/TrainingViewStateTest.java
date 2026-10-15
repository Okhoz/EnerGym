package com.energym.app.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/** La elección de Entreno pertenece a una cuenta y sobrevive a sus refrescos. */
public final class TrainingViewStateTest {
	@Test public void sameAccountKeepsPaneAndExpandedSections() {
		TrainingViewState state = new TrainingViewState();
		state.resetFor("owner-a");
		state.select(TrainingViewState.Pane.EXERCISES);
		state.draftsExpanded = state.optionsExpanded = true;
		long revision = state.bindingRevision();
		state.resetFor(new String("owner-a"));
		assertEquals(TrainingViewState.Pane.EXERCISES, state.pane);
		assertTrue(state.draftsExpanded);
		assertTrue(state.optionsExpanded);
		assertEquals(revision, state.bindingRevision());
	}

	@Test public void anotherAccountStartsWithItsOwnCollapsedRoutinePane() {
		TrainingViewState state = new TrainingViewState();
		state.resetFor("owner-a");
		state.select(TrainingViewState.Pane.AI);
		state.draftsExpanded = state.optionsExpanded = true;
		long revision = state.bindingRevision();
		state.resetFor("owner-b");
		assertEquals(TrainingViewState.Pane.MINE, state.pane);
		assertFalse(state.draftsExpanded);
		assertFalse(state.optionsExpanded);
		assertTrue(state.bindingRevision() > revision);
	}

	@Test public void signOutAndReturnCannotReuseAnOldBinding() {
		TrainingViewState state = new TrainingViewState();
		state.resetFor("owner-a");
		long previous = state.bindingRevision();
		state.select(TrainingViewState.Pane.EXERCISES);
		state.resetFor(null);
		assertEquals(TrainingViewState.Pane.MINE, state.pane);
		state.resetFor("owner-a");
		assertEquals(TrainingViewState.Pane.MINE, state.pane);
		assertTrue(state.bindingRevision() > previous);
	}

	@Test public void changingPaneDoesNotDiscardExpandedSectionPreferences() {
		TrainingViewState state = new TrainingViewState();
		state.resetFor("owner-a");
		state.draftsExpanded = state.optionsExpanded = true;
		state.select(TrainingViewState.Pane.AI);
		state.select(TrainingViewState.Pane.MINE);
		assertTrue(state.draftsExpanded);
		assertTrue(state.optionsExpanded);
	}
}
