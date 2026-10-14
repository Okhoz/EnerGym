package com.energym.app.sync;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.energym.app.auth.*;
import com.energym.app.controller.SyncController;
import com.energym.app.data.*;
import com.energym.app.domain.*;
import com.energym.app.assistant.*;
import com.energym.app.catalog.CatalogRepository;

import java.util.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalDeletionIntegrationTest {
	@Test
	public void clearInvalidatesRetainedCloudAndQueuedConversationWithoutTouchingOtherOwner() throws Exception {
		Context context = ApplicationProvider.getApplicationContext();
		String owner = "erase-" + UUID.randomUUID(), other = "keep-" + UUID.randomUUID();
		ArrayDeque<Runnable> queue = new ArrayDeque<>();
		Identity identity = new Identity(owner);
		Cloud cloud = new Cloud();
		List<SyncCoordinator.Status> statuses = new ArrayList<>();
		SyncController controller = new SyncController(context, identity, cloud, queue::add, Runnable::run,
			(status, e) -> statuses.add(status));
		LocalConversationStore conversations = new LocalConversationStore(context, queue::add);
		try (EnerGymDatabase local = new EnerGymDatabase(context, owner); EnerGymDatabase foreign = new EnerGymDatabase(
			context, other)) {
			TrainingProfile p = new TrainingProfile.Builder().build();
			local.saveProfile(p);
			foreign.saveProfile(p.toBuilder().preferences("ajeno").build());
			Routine proposal = new RoutinePlanner().create(p, CatalogRepository.load(context).getExercises());
			RoutineRepository repository = new RoutineRepository(local);
			Routine accepted = repository.load(repository.saveAccepted(proposal));
			new SessionRepository(local).saveDraft(new Session(accepted));
			List<CloudSyncGateway.RemoteRecord> remote = new ArrayList<>();
			for (SyncRecord record : local.pendingSyncRecords())
				remote.add(new CloudSyncGateway.RemoteRecord(record, 1));
			controller.authenticated(identity.user);
			drain(queue);
			assertNotNull(cloud.retained);
			ConversationStore.State state = new ConversationStore.State("conversation",
				Collections.singletonList(new ConversationStore.Message("user", "privado")), Collections.emptyMap(),
				proposal, p, null);
			conversations.save(owner, state);
			conversations.save(other, state);
			conversations.clear(owner);
			boolean[] deleted = {false};
			controller.clearLocalData(new SyncController.DataCallback<Void>() {
				public void onSuccess(Void ignored) {
					deleted[0] = true;
				}

				public void onFailure(Exception error) {
					throw new AssertionError(error);
				}
			});
			drain(queue);
			int emitted = statuses.size();
			cloud.retained.onSuccess(remote);
			cloud.retained.onFailure(new IllegalStateException("tardía"));
			drain(queue);
			assertTrue(deleted[0]);
			assertEquals(emitted, statuses.size());
			assertNull(local.loadProfile());
			assertTrue(repository.versions().isEmpty());
			assertTrue(new SessionRepository(local).pending().isEmpty());
			assertNull(new LocalConversationStore(context, Runnable::run).load(owner));
			assertEquals("ajeno", foreign.loadProfile().getPreferences());
			assertNotNull(new LocalConversationStore(context, Runnable::run).load(other));
			foreign.clearLocalData();
		} finally {
			controller.close();
			drain(queue);
			conversations.clear(owner);
			conversations.clear(other);
		}
	}

	private static void drain(ArrayDeque<Runnable> queue) {
		while (!queue.isEmpty())
			queue.remove().run();
	}

	private static final class Identity implements AccountGateway {
		final AccountUser user;

		Identity(String uid) {
			user = new AccountUser(uid, "fixture", "fixture");
		}

		public boolean isConfigured() {
			return true;
		}

		public AccountUser getCurrentUser() {
			return user;
		}

		public void signInWithGoogleIdToken(String token, Callback callback) {
			throw new AssertionError();
		}

		public void signOut() {
		}

		public Subscription observe(Listener listener) {
			return () -> {
			};
		}
	}

	private static final class Cloud implements CloudSyncGateway {
		Callback<List<RemoteRecord>> retained;

		public void restore(String owner, Callback<List<RemoteRecord>> callback) {
			retained = callback;
		}

		public void upload(SyncRecord record, long version, Callback<UploadResult> callback) {
			throw new AssertionError("No se debe subir tras borrar");
		}
	}
}
