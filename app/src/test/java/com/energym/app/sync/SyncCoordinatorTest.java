package com.energym.app.sync;

import com.energym.app.data.SyncRecord;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pruebas de contrato con almacén en memoria y transporte diferido: ninguna llamada a nube real.
 */
public class SyncCoordinatorTest {
	private String uid = "alice";
	private final Transport cloud = new Transport();
	private final List<SyncCoordinator.Status> statuses = new ArrayList<>();

	private SyncCoordinator coordinator() {
		return new SyncCoordinator(cloud, () -> uid, Runnable::run, (s, e) -> statuses.add(s));
	}

	private SyncRecord record(String owner, long revision, String payload) {
		return new SyncRecord(SyncRecord.Kind.PROFILE, "profile", owner, payload, revision, revision, SyncRecord.State.PENDING);
	}

	@Test
	public void signedOutAndDemoCannotContactCloud() {
		SyncCoordinator c = coordinator();
		uid = null;
		c.activate(new Store("alice"));
		c.sync();
		assertFalse(statuses.isEmpty());
		assertEquals(SyncCoordinator.Status.SIGNED_OUT, statuses.get(statuses.size() - 1));
		c.activate(new Store("demo:local"));
		c.sync();
		assertEquals(SyncCoordinator.Status.DEMO_LOCAL, statuses.get(statuses.size() - 1));
		assertEquals(0, cloud.restores);
	}

	@Test
	public void accountSwitchDropsOldRestoreAndClosesStore() {
		Store a = new Store("alice"), b = new Store("bob");
		SyncCoordinator c = coordinator();
		c.activate(a);
		c.sync();
		CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>> old = cloud.download;
		assertNotNull(old);
		uid = "bob";
		c.activate(b);
		old.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("alice", 1, "remote"), 1)));
		assertTrue(a.closed);
		assertNull(b.value);
		assertNull(a.value);
	}

	@Test
	public void pendingLocalSurvivesRestoreAndUploadsAtObservedServerVersion() {
		Store s = new Store("alice");
		s.value = record("alice", 2, "local");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("alice", 1, "remote"), 7)));
		assertEquals("local", s.value.getPayload());
		assertEquals("local", cloud.sent.getPayload());
		assertEquals(7, cloud.expected);
	}

	@Test
	public void olderAcknowledgmentDoesNotClearNewerLocalEdit() {
		Store s = new Store("alice");
		s.value = record("alice", 1, "first");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.emptyList());
		s.value = record("alice", 2, "edited");
		cloud.upload.onSuccess(new CloudSyncGateway.UploadResult(true, 1));
		assertEquals(SyncRecord.State.PENDING, s.value.getState());
		assertEquals("edited", s.value.getPayload());
		assertFalse("No declarar respaldo completo mientras queda una edición nueva", statuses.contains(SyncCoordinator.Status.SYNCED));
	}

	@Test
	public void staleUploadCannotAcknowledgeAfterSignOut() {
		Store s = new Store("alice");
		s.value = record("alice", 1, "local");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.emptyList());
		uid = null;
		cloud.upload.onSuccess(new CloudSyncGateway.UploadResult(true, 1));
		assertEquals(SyncRecord.State.PENDING, s.value.getState());
		assertFalse(statuses.contains(SyncCoordinator.Status.SYNCED));
	}

	@Test
	public void successfulUploadUsesStableIdentityAndStopsEchoingCleanRecords() {
		Store s = new Store("alice");
		s.value = record("alice", 1, "local");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.emptyList());
		assertEquals("profile", cloud.sent.getSyncId());
		cloud.upload.onSuccess(new CloudSyncGateway.UploadResult(true, 1));
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("alice", 1, "local"), 1)));
		assertEquals(1, cloud.uploads);
		assertEquals(SyncCoordinator.Status.SYNCED, statuses.get(statuses.size() - 1));
	}

	@Test
	public void serverConflictAndNetworkErrorKeepPending() {
		Store s = new Store("alice");
		s.value = record("alice", 1, "local");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.emptyList());
		cloud.upload.onSuccess(new CloudSyncGateway.UploadResult(false, 3));
		assertEquals(SyncRecord.State.PENDING, s.value.getState());
		assertEquals(SyncCoordinator.Status.CONFLICT, statuses.get(statuses.size() - 1));
		c.sync();
		cloud.download.onFailure(new Exception("offline"));
		assertEquals(SyncCoordinator.Status.ERROR, statuses.get(statuses.size() - 1));
		assertEquals(SyncRecord.State.PENDING, s.value.getState());
	}

	@Test
	public void foreignRemoteBatchRejectedBeforeImport() {
		Store s = new Store("alice");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		assertNotNull(cloud.download);
		cloud.download.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("bob", 1, "foreign"), 1)));
		assertNull(s.value);
		assertEquals(SyncCoordinator.Status.ERROR, statuses.get(statuses.size() - 1));
	}

	@Test
	public void temporaryHiddenUidDropsRestoreAndNewCycleIgnoresOldFailure() {
		Store s = new Store("alice");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>> old = cloud.download;
		uid = null;
		old.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("alice", 1, "stale"), 1)));
		assertNull(s.value);
		uid = "alice";
		c.sync();
		assertEquals("La cuenta recuperada debe abrir otro ciclo", 2, cloud.restores);
		CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>> fresh = cloud.download;
		old.onFailure(new Exception("fallo antiguo"));
		c.sync();
		assertEquals("Un callback antiguo no debe desbloquear el ciclo nuevo", 2, cloud.restores);
		fresh.onSuccess(Collections.emptyList());
		assertEquals(3, cloud.restores);
		cloud.download.onSuccess(Collections.emptyList());
		assertEquals(SyncCoordinator.Status.SYNCED, statuses.get(statuses.size() - 1));
	}

	@Test
	public void temporaryHiddenUidDropsUploadAckButRecoveryUploadsPending() {
		Store s = new Store("alice");
		s.value = record("alice", 1, "pending");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		cloud.download.onSuccess(Collections.emptyList());
		CloudSyncGateway.Callback<CloudSyncGateway.UploadResult> old = cloud.upload;
		uid = null;
		old.onSuccess(new CloudSyncGateway.UploadResult(true, 1));
		assertEquals(SyncRecord.State.PENDING, s.value.getState());
		uid = "alice";
		c.sync();
		assertEquals(2, cloud.restores);
		cloud.download.onSuccess(Collections.emptyList());
		old.onFailure(new Exception("fallo antiguo"));
		cloud.upload.onSuccess(new CloudSyncGateway.UploadResult(true, 1));
		assertEquals(SyncRecord.State.CLEAN, s.value.getState());
	}

	@Test
	public void temporaryHiddenUidOnFailureAllowsRecovery() {
		SyncCoordinator c = coordinator();
		c.activate(new Store("alice"));
		c.sync();
		uid = null;
		cloud.download.onFailure(new Exception("auth en curso"));
		uid = "alice";
		c.sync();
		assertEquals(2, cloud.restores);
	}

	@Test
	public void requestingSyncDuringTemporaryAuthAbsenceInvalidatesActiveCycle() {
		Store s = new Store("alice");
		SyncCoordinator c = coordinator();
		c.activate(s);
		c.sync();
		CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>> old = cloud.download;
		uid = null;
		c.sync();
		uid = "alice";
		c.sync();
		assertEquals(2, cloud.restores);
		old.onSuccess(Collections.singletonList(new CloudSyncGateway.RemoteRecord(record("alice", 1, "stale"), 1)));
		assertNull(s.value);
		cloud.download.onSuccess(Collections.emptyList());
		assertEquals(SyncCoordinator.Status.SYNCED, statuses.get(statuses.size() - 1));
	}

	static final class Store implements LocalSyncStore {
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
			return value != null && value.getState() == SyncRecord.State.PENDING ? Collections.singletonList(value) : Collections.emptyList();
		}

		public void importRecords(List<SyncRecord> rs) {
			if (value == null || value.getState() != SyncRecord.State.PENDING)
				for (SyncRecord r : rs)
					value = new SyncRecord(r.getKind(), r.getSyncId(), r.getOwnerUid(), r.getPayload(), r.getRevision(), r.getUpdatedAt(), SyncRecord.State.CLEAN);
		}

		public boolean acknowledge(SyncRecord r) {
			if (value != null && value.getRevision() == r.getRevision()) {
				value = new SyncRecord(value.getKind(), value.getSyncId(), owner, value.getPayload(), value.getRevision(), value.getUpdatedAt(), SyncRecord.State.CLEAN);
				return true;
			}
			return false;
		}

		public void close() {
			closed = true;
		}
	}

	static final class Transport implements CloudSyncGateway {
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

