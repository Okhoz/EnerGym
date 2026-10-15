package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Readiness belongs to the accepted account snapshot, never to a late cloud notification. */
public final class InitialRestoreControllerTest {
	private final Store store = new Store();
	private final AppController app = new AppController(store, new CatalogRepository(1,
		Collections.singletonList(new Sport("strength", "Fuerza", Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
			Sport.CoverageStatus.EXAMPLE_UNREVIEWED)), Collections.emptyList()));

	private AppStore.Snapshot snapshot(TrainingProfile profile, boolean ready) {
		return new AppStore.Snapshot(profile, Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), ready);
	}

	@Test public void pendingRestoreRejectsInputAndSaveThenUsesTheRestoredDraft() {
		app.activate("fixture-a");
		assertFalse(app.initialDataReady);
		store.loading.success(snapshot(null, false));
		app.profileInput("preferences", "EMPTY SETUP");
		app.saveProfile();
		assertEquals(0, store.writes);
		assertFalse(app.initialDataReady);
		TrainingProfile remote = new TrainingProfile.Builder().preferences("REMOTE SETTINGS").build();
		app.reload();
		store.loading.success(snapshot(remote, true));
		assertTrue(app.initialDataReady);
		app.saveProfile();
		assertEquals("REMOTE SETTINGS", store.saved.getPreferences());
	}

	@Test public void emptyServerResultEnablesRealNewAccountSetup() {
		app.activate("fixture-a");
		store.loading.success(snapshot(null, false));
		app.reload();
		store.loading.success(snapshot(null, true));
		app.profileInput("preferences", "NEW ACCOUNT");
		app.saveProfile();
		assertEquals("NEW ACCOUNT", store.saved.getPreferences());
	}

	@Test public void oldAccountAndSupersededReadsCannotReleaseTheNewAccount() {
		app.activate("fixture-a");
		AppStore.Callback<AppStore.Snapshot> previousAccount = store.loading;
		app.activate("fixture-b");
		AppStore.Callback<AppStore.Snapshot> previousRead = store.loading;
		app.reload();
		previousAccount.success(snapshot(null, true));
		previousRead.success(snapshot(null, true));
		assertFalse(app.initialDataReady);
		store.loading.success(snapshot(null, false));
		assertFalse(app.initialDataReady);
	}

	@Test public void cachedSnapshotRemainsEditableWhenRefreshFails() {
		app.activate("fixture-a");
		store.loading.success(snapshot(new TrainingProfile.Builder().build(), true));
		app.reload();
		store.loading.failure(new IllegalStateException("offline"));
		assertTrue(app.initialDataReady);
		app.profileInput("preferences", "OFFLINE EDIT");
		app.saveProfile();
		assertEquals("OFFLINE EDIT", store.saved.getPreferences());
	}

	@Test public void failedInitialReadRemainsBlockedButAccountNavigationWorks() {
		app.activate("fixture-a");
		store.loading.failure(new IllegalStateException("offline"));
		assertFalse(app.initialDataReady);
		app.navigate(AppController.Screen.ACCOUNT);
		assertEquals(AppController.Screen.ACCOUNT, app.screen);
		app.activate(null);
		assertTrue(app.initialDataReady);
	}

	private static final class Store extends AppControllerTest.Store {
		int writes;
		TrainingProfile saved;
		@Override public void profile(TrainingProfile value, Callback<Void> callback) {
			writes++;
			saved = value;
			callback.success(null);
		}
	}
}
