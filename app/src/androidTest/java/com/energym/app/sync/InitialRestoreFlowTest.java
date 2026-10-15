package com.energym.app.sync;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.controller.*;
import com.energym.app.data.*;
import com.energym.app.domain.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

/** Real Android JSON/codecs with in-memory fixture state and deferred cloud; no Firebase, SQL or account I/O. */
@RunWith(AndroidJUnit4.class)
public final class InitialRestoreFlowTest {
	private static final String OWNER = "restore-flow-fixture";
	private final Store local = new Store(OWNER);
	private final Transport cloud = new Transport();
	private final InitialRestoreGate gate = new InitialRestoreGate();
	private final SyncCoordinator sync = new SyncCoordinator(cloud, () -> OWNER, Runnable::run, (s, e) -> {});
	private final Bridge store = new Bridge();
	private final TrainingProfile remoteProfile = new TrainingProfile.Builder().daysPerWeek(5).durationMinutes(45)
		.level("Intermedio").preferences("REMOTE BACKUP SETTINGS").build();

	private AppController open() {
		gate.requireRestore();
		gate.cacheInspected(false);
		sync.activate(new LocalSyncStore() {
			public String ownerUid() { return OWNER; }
			public List<SyncRecord> pending() { return local.pending(); }
			public void importRecords(List<SyncRecord> records) {
				for (SyncRecord record : records) SnapshotCodec.decodeProfile(record.getPayload());
				local.importRecords(records);
				gate.serverRestored();
			}
			public boolean acknowledge(SyncRecord record) { return local.acknowledge(record); }
			public void close() { local.close(); }
		});
		sync.sync();
		AppController app = new AppController(store, new CatalogRepository(1, Collections.singletonList(
			new Sport("strength", "Fuerza", Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
				Sport.CoverageStatus.EXAMPLE_UNREVIEWED)), Collections.emptyList()));
		app.activate(OWNER);
		return app;
	}

	@Test public void prematureSetupCannotBecomePendingOrOverwriteTheDownloadedBackup() {
		AppController app = open();
		app.profileInput("preferences", "EMPTY SETUP");
		app.saveProfile();
		assertEquals(0, store.writes);
		assertNull(local.value);
		SyncRecord record = new SyncRecord(SyncRecord.Kind.PROFILE, "profile", OWNER,
			SnapshotCodec.encodeProfile(remoteProfile), 42, 100, SyncRecord.State.CLEAN);
		cloud.download.onSuccess(Collections.singletonList(CloudDocumentCodec.decode(OWNER, record.getKind(),
			"current", CloudDocumentCodec.encode(record, 7))));
		app.reload();
		assertTrue(app.initialDataReady);
		assertEquals(remoteProfile, app.profile);
		assertNull(cloud.sent);
		assertEquals(0, cloud.uploads);
	}

	@Test public void offlineFailureRemainsUnknownUntilAnEmptyServerRetrySucceeds() {
		AppController app = open();
		cloud.download.onFailure(new IllegalStateException("offline"));
		app.profileInput("preferences", "PREMATURE");
		app.saveProfile();
		assertTrue(gate.pending());
		assertEquals(0, store.writes);
		sync.sync();
		cloud.download.onSuccess(Collections.emptyList());
		app.reload();
		app.profileInput("preferences", "REAL NEW ACCOUNT");
		app.saveProfile();
		assertEquals(1, store.writes);
		assertEquals("REAL NEW ACCOUNT", app.profile.getPreferences());
	}

	private final class Bridge implements AppStore {
		int writes;
		public void load(Callback<Snapshot> callback) {
			callback.success(new Snapshot(local.value == null ? null : SnapshotCodec.decodeProfile(local.value.getPayload()),
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), !gate.pending()));
		}
		public void profile(TrainingProfile value, Callback<Void> callback) {
			gate.requireWritable();
			writes++;
			local.value = new SyncRecord(SyncRecord.Kind.PROFILE, "profile", OWNER, SnapshotCodec.encodeProfile(value),
				43, 1000, SyncRecord.State.PENDING);
			callback.success(null);
			sync.sync();
		}
		public void accept(Routine value, Callback<Routine> callback) { throw new AssertionError("unused"); }
		public void draft(Session value, Callback<Long> callback) { throw new AssertionError("unused"); }
		public void openDraft(String id, Callback<OpenDraft> callback) { throw new AssertionError("unused"); }
		public void finish(long id, Callback<Void> callback) { throw new AssertionError("unused"); }
	}

	private static final class Store implements LocalSyncStore {
		final String owner;
		SyncRecord value;
		boolean closed;

		Store(String owner) {
			this.owner = owner;
		}

		public String ownerUid() {
			return owner;
		}

		public List<SyncRecord> pending() {
			return value != null && value.getState() == SyncRecord.State.PENDING ? Collections.singletonList(value) :
				Collections.emptyList();
		}

		public void importRecords(List<SyncRecord> rs) {
			if (value == null || value.getState() != SyncRecord.State.PENDING)
				for (SyncRecord r : rs)
					value = new SyncRecord(r.getKind(), r.getSyncId(), r.getOwnerUid(), r.getPayload(), r.getRevision(),
						r.getUpdatedAt(), SyncRecord.State.CLEAN);
		}

		public boolean acknowledge(SyncRecord r) {
			if (value != null && value.getRevision() == r.getRevision()) {
				value =
					new SyncRecord(value.getKind(), value.getSyncId(), owner, value.getPayload(), value.getRevision(),
						value.getUpdatedAt(), SyncRecord.State.CLEAN);
				return true;
			}
			return false;
		}

		public void close() {
			closed = true;
		}
	}

	private static final class Transport implements CloudSyncGateway {
		int restores, uploads;
		long expected;
		SyncRecord sent;
		Callback<List<RemoteRecord>> download;
		Callback<UploadResult> upload;

		public void restore(String uid, Callback<List<RemoteRecord>> cb) {
			restores++;
			download = cb;
		}

		public void upload(SyncRecord r, long expected, Callback<UploadResult> cb) {
			uploads++;
			sent = r;
			this.expected = expected;
			upload = cb;
		}
	}
}
