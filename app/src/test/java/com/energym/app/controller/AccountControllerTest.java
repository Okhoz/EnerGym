package com.energym.app.controller;

import com.energym.app.auth.*;
import com.energym.app.sync.*;
import com.energym.app.data.SyncRecord;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

public class AccountControllerTest {
	private final Gateway gateway = new Gateway();
	private final Target target = new Target();
	private final List<AccountController.Status> statuses = new ArrayList<>();

	private AccountController controller() {
		return new AccountController(gateway, target, Runnable::run, (s, u, e) -> statuses.add(s));
	}

	@Test
	public void unconfiguredIsExplicitAndDemoRemainsLocal() {
		gateway.configured = false;
		AccountController c = controller();
		c.start();
		assertTrue(statuses.contains(AccountController.Status.CONFIGURATION_NEEDED));
		c.enterDemo();
		assertEquals("demo:local", target.scope);
		assertTrue(statuses.contains(AccountController.Status.DEMO_LOCAL));
		assertEquals(0, gateway.signins);
	}

	@Test
	public void ownerComesFromAuthenticatedGatewayNotCallerToken() {
		AccountController c = controller();
		c.start();
		c.signInWithGoogleIdToken("not-an-owner");
		assertNotNull(gateway.callback);
		gateway.user = new AccountUser("firebase-uid", "Name", "mail");
		gateway.callback.onSuccess(gateway.user);
		assertEquals("firebase-uid", target.scope);
		assertTrue(statuses.contains(AccountController.Status.AUTHENTICATED));
	}

	@Test
	public void lateSignInAfterSignOutCannotOpenUserScope() {
		AccountController c = controller();
		c.start();
		c.signInWithGoogleIdToken("token");
		assertNotNull(gateway.callback);
		c.signOut();
		gateway.callback.onSuccess(new AccountUser("old", "", ""));
		assertNull(target.scope);
		assertFalse(statuses.contains(AccountController.Status.AUTHENTICATED));
	}

	@Test
	public void authoritativeObserverSwitchesAccountAndStopsAfterClose() {
		AccountController c = controller();
		c.start();
		assertNotNull(gateway.listener);
		gateway.user = new AccountUser("alice", "", "");
		gateway.listener.onAccountChanged(gateway.user);
		assertEquals("alice", target.scope);
		gateway.user = new AccountUser("bob", "", "");
		gateway.listener.onAccountChanged(gateway.user);
		assertEquals("bob", target.scope);
		c.close();
		gateway.listener.onAccountChanged(gateway.user);
		assertNull(target.scope);
		assertTrue(gateway.unsubscribed);
	}

	@Test
	public void failedGoogleAttemptResumesBackupOfPreviousAuthenticatedAccount() {
		recoverSameAccount(false);
	}

	@Test
	public void successfulGoogleAttemptForSameUidResumesBackup() {
		recoverSameAccount(true);
	}

	private void recoverSameAccount(boolean success) {
		RecoveryCloud cloud = new RecoveryCloud();
		SyncCoordinator sync = new SyncCoordinator(cloud, () -> gateway.user == null ? null : gateway.user.getUid(), Runnable::run, (s, e) -> {
		});
		LocalSyncStore store = new LocalSyncStore() {
			public String ownerUid() {
				return "alice";
			}

			public List<SyncRecord> pending() {
				return Collections.emptyList();
			}

			public void importRecords(List<SyncRecord> r) {
			}

			public boolean acknowledge(SyncRecord r) {
				return false;
			}

			public void close() {
			}
		};
		AccountController.SessionTarget backup = new AccountController.SessionTarget() {
			boolean opened;

			public void authenticated(AccountUser u) {
				if (!opened) {
					sync.activate(store);
					opened = true;
				}
				sync.sync();
			}

			public void demo() {
				sync.close();
			}

			public void signedOut() {
				sync.close();
				opened = false;
			}
		};
		gateway.user = new AccountUser("alice", "", "");
		AccountController controller = new AccountController(gateway, backup, Runnable::run, (s, u, e) -> {
		});
		controller.start();
		assertEquals(1, cloud.restores);
		controller.signInWithGoogleIdToken("retry-token");
		gateway.user = null;
		cloud.download.onSuccess(Collections.emptyList());
		gateway.user = new AccountUser("alice", "", "");
		if (success)
			gateway.callback.onSuccess(gateway.user);
		else
			gateway.callback.onFailure(new Exception("Google rechazó el cambio; Firebase conserva Alice"));
		assertEquals("El retorno al mismo UID debe reanudar backup sin cambiar cuenta", 2, cloud.restores);
		cloud.download.onSuccess(Collections.emptyList());
		controller.close();
	}

	static final class RecoveryCloud implements CloudSyncGateway {
		int restores;
		Callback<List<RemoteRecord>> download;

		public void restore(String uid, Callback<List<RemoteRecord>> cb) {
			restores++;
			download = cb;
		}

		public void upload(SyncRecord r, long expected, Callback<UploadResult> cb) {
			throw new AssertionError("No hay cola pendiente");
		}
	}

	static final class Target implements AccountController.SessionTarget {
		String scope;

		public void authenticated(AccountUser u) {
			scope = u.getUid();
		}

		public void demo() {
			scope = "demo:local";
		}

		public void signedOut() {
			scope = null;
		}
	}

	static final class Gateway implements AccountGateway {
		boolean configured = true, unsubscribed;
		AccountUser user;
		int signins;
		Callback callback;
		Listener listener;

		public boolean isConfigured() {
			return configured;
		}

		public AccountUser getCurrentUser() {
			return user;
		}

		public void signInWithGoogleIdToken(String t, Callback cb) {
			signins++;
			callback = cb;
		}

		public void signOut() {
			user = null;
		}

		public Subscription observe(Listener l) {
			listener = l;
			return () -> unsubscribed = true;
		}
	}
}
