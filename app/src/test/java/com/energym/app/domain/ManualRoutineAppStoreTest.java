package com.energym.app.domain;

import com.energym.app.controller.AppStore;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ManualRoutineAppStoreTest {
	@Test public void anUnadaptedStoreFailsExplicitlyWithoutUsingTheAiOrProfileWritePorts() {
		AppStore store = new AppStore() {
			public void load(Callback<Snapshot> callback) { fail("Unexpected load"); }
			public void profile(TrainingProfile profile, Callback<Void> callback) { fail("Must not change profile"); }
			public void accept(Routine routine, Callback<Routine> callback) { fail("Must not use AI acceptance"); }
			public void draft(Session session, Callback<Long> callback) { fail("Unexpected session"); }
			public void openDraft(String id, Callback<OpenDraft> callback) { fail("Unexpected session"); }
			public void finish(long id, Callback<Void> callback) { fail("Unexpected session"); }
		};
		Exception[] error = {null};
		store.acceptManual(null, new AppStore.Callback<Routine>() {
			public void success(Routine value) { fail("Unadapted store must not report success"); }
			public void failure(Exception failure) { error[0] = failure; }
		});
		assertTrue(error[0] instanceof UnsupportedOperationException);
	}
}
