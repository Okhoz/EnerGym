package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AppControllerTest {
	@Test
	public void restoredSelectionReplacesPreviouslyLoadedRoutineAndStartsIt() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		TrainingProfile p = new TrainingProfile.Builder().build();
		Routine old = new Routine("z-old", 99, "Anterior", p, Collections.emptyList(), "fixture");
		Routine current = new Routine("a-new", 2, "Vigente", p, Collections.emptyList(), "fixture");
		c.activate("owner");
		s.loading.success(
			new AppStore.Snapshot(p, Collections.singletonList(old), Collections.emptyList(), Collections.emptyList()));
		c.reload();
		s.loading.success(
			new AppStore.Snapshot(p, Arrays.asList(current, old), Collections.emptyList(), Collections.emptyList()));
		c.startSession();
		assertEquals("a-new", c.sessions.session.getRoutine().getProposalId());
		assertEquals(2, c.versions.size());
	}

	static class Store extends TrainingControllerTest.Store {
		Callback<Snapshot> loading;

		public void load(Callback<Snapshot> c) {
			loading = c;
		}
	}

	private CatalogRepository catalog() {
		return new CatalogRepository(1, Collections.singletonList(
			new Sport("strength", "Fuerza", Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
				Sport.CoverageStatus.EXAMPLE_UNREVIEWED)), Collections.emptyList());
	}

	private AppStore.Snapshot snapshot(TrainingProfile p) {
		return new AppStore.Snapshot(p, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	@Test
	public void activeAccountLoadsCompleteProfile() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		c.activate("uid-a");
		assertNotNull(s.loading);
		TrainingProfile p = new TrainingProfile.Builder().preferences("Preferencia A").build();
		s.loading.success(snapshot(p));
		assertEquals(p, c.profile);
	}

	@Test
	public void previousAccountLoadCannotLeakAfterSwitch() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		c.activate("uid-a");
		assertNotNull(s.loading);
		AppStore.Callback<AppStore.Snapshot> old = s.loading;
		c.activate("uid-b");
		TrainingProfile b = new TrainingProfile.Builder().preferences("B").build();
		s.loading.success(snapshot(b));
		old.success(snapshot(new TrainingProfile.Builder().preferences("A").build()));
		assertEquals(b, c.profile);
	}

	@Test
	public void signOutClearsPersonalProfile() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		c.activate("uid");
		assertNotNull(s.loading);
		s.loading.success(snapshot(new TrainingProfile.Builder().build()));
		assertNotNull(c.profile);
		c.activate(null);
		assertNull(c.profile);
	}

	@Test
	public void exerciseBackReturnsToSessionRatherThanLosingNavigation() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		c.navigate(AppController.Screen.TRAINING);
		c.navigate(AppController.Screen.SESSION);
		c.navigate(AppController.Screen.EXERCISE);
		c.back();
		assertEquals(AppController.Screen.SESSION, c.screen);
	}
}
