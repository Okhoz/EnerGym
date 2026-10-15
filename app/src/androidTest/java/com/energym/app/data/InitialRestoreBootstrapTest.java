package com.energym.app.data;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.energym.app.auth.*;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.controller.*;
import com.energym.app.domain.*;
import com.energym.app.nutrition.BodyProfile;
import com.energym.app.sync.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Real SQLite/codecs/controllers, deferred cloud, unique fixture UIDs; never Google/demo data. */
@RunWith(AndroidJUnit4.class)
public final class InitialRestoreBootstrapTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	private final List<String> owners = new ArrayList<>();
	private final Gateway accounts = new Gateway();
	private final Cloud cloud = new Cloud();
	private SyncController sync;
	private AppController app;
	private String uid;
	private TrainingProfile remoteProfile() {
		return new TrainingProfile.Builder().daysPerWeek(5).durationMinutes(45).level("Intermedio")
			.preferences("REMOTE BACKUP SETTINGS").build();
	}

	private String owner() {
		String value = "restore-fixture-" + UUID.randomUUID();
		owners.add(value);
		return value;
	}

	@Before public void start() {
		uid = owner();
		accounts.user = new AccountUser(uid, "Fixture", "");
		sync = new SyncController(context, accounts, cloud, Runnable::run, Runnable::run, (s, e) -> {});
		CatalogRepository catalog = new CatalogRepository(1, Collections.singletonList(new Sport("strength", "Fuerza",
			Sport.CoverageStatus.EXAMPLE_UNREVIEWED, Sport.CoverageStatus.EXAMPLE_UNREVIEWED)), Collections.emptyList());
		app = new AppController(new AndroidAppStore(sync), catalog);
	}

	@After public void cleanOnlyFixtureOwners() {
		if (sync != null) sync.close();
		for (String owner : owners)
			try (EnerGymDatabase db = new EnerGymDatabase(context, owner)) { db.clearLocalData(); }
	}

	private void login() {
		sync.authenticated(accounts.user);
		app.activate(uid);
		assertNotNull(cloud.download);
	}

	private CloudSyncGateway.RemoteRecord remote(String owner) {
		SyncRecord record = new SyncRecord(SyncRecord.Kind.PROFILE, "profile", owner,
			SnapshotCodec.encodeProfile(remoteProfile()), 42, 100, SyncRecord.State.CLEAN);
		return CloudDocumentCodec.decode(owner, record.getKind(), "current", CloudDocumentCodec.encode(record, 7));
	}

	@Test public void freshSaveBeforeServerRestoreCannotOverwriteBackedUpProfile() {
		login();
		assertFalse(app.loading);
		assertNull(app.profile);
		app.profileInput("preferences", "NEW EMPTY SETUP");
		app.saveProfile();
		assertNull("A pending restore must reject the premature profile write", app.profile);
		try (EnerGymDatabase db = new EnerGymDatabase(context, uid)) { assertTrue(db.pendingSyncRecords().isEmpty()); }
		cloud.download.onSuccess(Collections.singletonList(remote(uid)));
		app.reload();
		assertEquals(remoteProfile(), app.profile);
		assertEquals(0, cloud.uploads);
	}

	@Test public void bodyAndRoutineAcceptanceCannotBypassTheInitialWriteBarrier() {
		login();
		List<Exception> errors = new ArrayList<>();
		sync.withDatabase(db -> {
			new NutritionRepository(db).saveBody(new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80,
				BodyProfile.Activity.MODERATE, BodyProfile.Goal.MAINTAIN, ""));
			return null;
		}, failed(errors));
		sync.withDatabase(db -> {
			TrainingProfile profile = new TrainingProfile.Builder().build();
			Routine routine = new Routine("fixture-routine", 0, "Fixture", profile,
				Collections.singletonList(new RoutineItem(new Exercise("fixture-squat", "Fixture squat",
					MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
					EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
					Exercise.Coverage.SPECIFIC, false, "Fixture", Collections.emptyList()),
					new LinkedHashMap<String, String>() {{ put("sets", "2"); put("repetitions", "8");
						put("loadKg", "0"); put("restSeconds", "60"); }}, 5, "Fixture")), "Fixture");
			new RoutineRepository(db).acceptProposal(routine, null);
			return null;
		}, failed(errors));
		assertEquals("Both mutation paths must fail", 2, errors.size());
		try (EnerGymDatabase db = new EnerGymDatabase(context, uid)) {
			assertTrue("No partial profile/routine/body may enter the outbox", db.pendingSyncRecords().isEmpty());
			assertNull(db.loadProfile());
		}
	}

	@Test public void validatedEmptyServerResultAllowsANewAccountToCreateItsProfile() {
		login();
		cloud.download.onSuccess(Collections.emptyList());
		app.reload();
		app.profileInput("preferences", "NEW ACCOUNT");
		app.saveProfile();
		assertNotNull(app.profile);
		assertEquals("NEW ACCOUNT", app.profile.getPreferences());
		try (EnerGymDatabase db = new EnerGymDatabase(context, uid)) { assertEquals(1, db.pendingSyncRecords().size()); }
	}

	@Test public void cachedProfileCanBeEditedOfflineWhileItsRestoreFails() {
		try (EnerGymDatabase db = new EnerGymDatabase(context, uid)) { db.saveProfile(remoteProfile()); }
		login();
		assertEquals(remoteProfile(), app.profile);
		cloud.download.onFailure(new IllegalStateException("fixture offline"));
		app.profileInput("preferences", "LEGITIMATE OFFLINE EDIT");
		app.saveProfile();
		assertEquals("LEGITIMATE OFFLINE EDIT", app.profile.getPreferences());
		try (EnerGymDatabase db = new EnerGymDatabase(context, uid)) { assertEquals(1, db.pendingSyncRecords().size()); }
	}

	@Test public void failedOrInvalidRestoreCannotUnlockAnEmptyAccountButRetryCan() {
		login();
		cloud.download.onFailure(new IllegalStateException("fixture offline"));
		app.saveProfile();
		assertNull(app.profile);
		sync.requestSync();
		cloud.download.onSuccess(Collections.singletonList(remote(owner())));
		app.saveProfile();
		assertNull("Foreign batch must not release the barrier", app.profile);
		sync.requestSync();
		cloud.download.onSuccess(Collections.emptyList());
		app.reload();
		app.saveProfile();
		assertNotNull(app.profile);
	}

	@Test public void oldAccountRestoreCannotUnlockTheNewAccount() {
		login();
		String previous = uid;
		CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>> retained = cloud.download;
		uid = owner();
		accounts.user = new AccountUser(uid, "Other fixture", "");
		sync.authenticated(accounts.user);
		app.activate(uid);
		retained.onSuccess(Collections.singletonList(remote(previous)));
		app.saveProfile();
		assertNull(app.profile);
		cloud.download.onSuccess(Collections.emptyList());
		app.reload();
		app.saveProfile();
		assertNotNull(app.profile);
	}

	private SyncController.DataCallback<Object> failed(List<Exception> errors) {
		return new SyncController.DataCallback<Object>() {
			public void onSuccess(Object value) {}
			public void onFailure(Exception error) { errors.add(error); }
		};
	}

	private static final class Gateway implements AccountGateway {
		AccountUser user;
		public boolean isConfigured() { return true; }
		public AccountUser getCurrentUser() { return user; }
		public void signInWithGoogleIdToken(String token, Callback callback) { throw new AssertionError("No Google"); }
		public void signOut() { throw new AssertionError("No signout"); }
		public Subscription observe(Listener listener) { return () -> {}; }
	}

	private static final class Cloud implements CloudSyncGateway {
		int uploads;
		Callback<List<RemoteRecord>> download;
		public void restore(String owner, Callback<List<RemoteRecord>> callback) { download = callback; }
		public void upload(SyncRecord record, long expected, Callback<UploadResult> callback) { uploads++; }
	}
}
