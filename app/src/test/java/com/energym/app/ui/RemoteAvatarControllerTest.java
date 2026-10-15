package com.energym.app.ui;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

/** Identidades y resultados simulados: sin red, Firebase ni cambios en cuentas reales. */
public final class RemoteAvatarControllerTest {
	private final Queue<Runnable> work = new ArrayDeque<>(), main = new ArrayDeque<>();
	private final List<String> shown = new ArrayList<>(), fetched = new ArrayList<>();
	private RemoteAvatarController<String> controller() {
		return new RemoteAvatarController<>(work::add, main::add, url -> {
			fetched.add(url);
			if (url.equals("broken")) throw new IllegalArgumentException("Imagen no disponible");
			return "image:" + url;
		}, shown::add);
	}
	private void finish() {
		while (!work.isEmpty()) work.remove().run();
		while (!main.isEmpty()) main.remove().run();
	}

	@Test public void imageLoadsOffTheViewAndIsDeliveredOnMain() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a");
		assertNull(c.image()); assertTrue(fetched.isEmpty());
		work.remove().run();
		assertNull(c.image());
		main.remove().run();
		assertEquals("image:photo-a", c.image());
		assertEquals(Arrays.asList(null, "image:photo-a"), shown);
	}
	@Test public void changingAccountCancelsAQueuedDownload() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); c.update("b", 2, "photo-b"); finish();
		assertEquals(Collections.singletonList("photo-b"), fetched);
		assertEquals("image:photo-b", c.image());
	}
	@Test public void completedOldResponseCannotReplaceAnotherAccountsPhoto() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); work.remove().run();
		c.update("b", 2, "photo-b"); main.remove().run();
		assertNull(c.image()); finish();
		assertEquals("image:photo-b", c.image());
	}
	@Test public void leavingAndReturningToTheSameAccountRejectsTheOldCallback() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); work.remove().run();
		c.update("b", 2, "photo-b"); c.update("a", 3, "photo-a");
		main.remove().run(); assertNull(c.image()); finish();
		assertEquals("image:photo-a", c.image());
		assertEquals(2, fetched.size());
	}
	@Test public void signingOutClearsTheImageAndDropsPendingResults() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); work.remove().run();
		c.update(null, 2, "photo-a"); finish();
		assertNull(c.image()); assertNull(shown.get(shown.size() - 1));
	}
	@Test public void unchangedIdentityDoesNotReloadOnEveryRender() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); finish(); c.update("a", 1, "photo-a"); finish();
		assertEquals(Collections.singletonList("photo-a"), fetched);
		assertEquals("image:photo-a", c.image());
	}
	@Test public void photoUrlOrAccountRevisionChangesStartANewRequest() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); finish();
		c.update("a", 1, "photo-new"); finish();
		c.update("a", 3, "photo-new"); finish();
		assertEquals(Arrays.asList("photo-a", "photo-new", "photo-new"), fetched);
	}
	@Test public void missingPhotoUsesFallbackWithoutRequestingTheNetwork() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, null); finish();
		assertNull(c.image()); assertTrue(fetched.isEmpty());
	}
	@Test public void failedPhotoKeepsFallbackWithoutAnAutomaticRetryLoop() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "broken"); finish(); c.update("a", 1, "broken"); finish();
		assertNull(c.image()); assertEquals(Collections.singletonList("broken"), fetched);
	}
	@Test public void closingTheViewCancelsRequestsAndRejectsLateDelivery() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); work.remove().run(); c.close(); finish();
		assertNull(c.image()); c.update("b", 2, "photo-b"); finish();
		assertEquals(Collections.singletonList("photo-a"), fetched);
	}
	@Test public void cachedPhotoCannotBeReadForAnotherAccountBeforeTheViewRendersAgain() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); finish();
		assertEquals("image:photo-a", c.imageFor("a", 1, "photo-a"));
		assertNull(c.imageFor("b", 2, "photo-b"));
		assertNull(c.imageFor(null, 2, ""));
	}
	@Test public void cachedPhotoCannotCrossAnAccountRevisionOrPhotoChange() {
		RemoteAvatarController<String> c = controller();
		c.update("a", 1, "photo-a"); finish();
		assertNull(c.imageFor("a", 3, "photo-a"));
		assertNull(c.imageFor("a", 1, "photo-new"));
	}
}
