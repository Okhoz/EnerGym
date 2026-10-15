package com.energym.app.ui;

import org.junit.Test;

import static org.junit.Assert.*;

public final class PendingAvatarSelectionTest {
	private static final String OWNER = "uid-a", PHOTO = "content://photos/1";

	@Test
	public void selectedPhotoWaitsWhileIdentityIsNotSettled() {
		PendingAvatarSelection selection = selected();
		assertNull(selection.take(OWNER, 7, false));
		assertEquals(OWNER, selection.owner());
		assertEquals(PHOTO, selection.uri());
		assertEquals(PHOTO, selection.take(OWNER, 7, true));
	}

	@Test
	public void selectedPhotoIsConsumedOnlyOnce() {
		PendingAvatarSelection selection = selected();
		assertEquals(PHOTO, selection.take(OWNER, 7, true));
		assertNull(selection.take(OWNER, 7, true));
		assertNull(selection.owner());
	}

	@Test
	public void cancellationClearsPendingRequest() {
		PendingAvatarSelection selection = selected();
		selection.result(null);
		assertNull(selection.owner());
		assertNull(selection.uri());
	}

	@Test
	public void anotherAccountCannotReceiveTheSelectedPhoto() {
		PendingAvatarSelection selection = selected();
		assertNull(selection.take("uid-b", 8, true));
		assertNull(selection.owner());
		assertNull(selection.uri());
	}

	@Test
	public void completedSignOutDiscardsTheSelection() {
		PendingAvatarSelection selection = selected();
		assertNull(selection.take(null, 8, true));
		assertNull(selection.owner());
	}

	@Test
	public void leavingAndReturningToTheSameUidInvalidatesTheSelection() {
		PendingAvatarSelection selection = selected();
		assertNull(selection.take(OWNER, 9, true));
		assertNull(selection.owner());
	}

	@Test
	public void accountRevisionInvalidatesEvenWhileSignInIsStillRunning() {
		PendingAvatarSelection selection = selected();
		assertNull(selection.take(null, 8, false));
		assertNull(selection.owner());
	}

	@Test
	public void latePickerResultAfterAccountInvalidationIsIgnored() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.begin(OWNER, 7);
		selection.take(null, 8, false);
		selection.result(PHOTO);
		assertNull(selection.take(OWNER, 9, true));
		assertNull(selection.uri());
	}

	@Test
	public void rotationKeepsTheOriginalAccountRevision() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, PHOTO, true);
		assertNull(selection.take(OWNER, 9, true));
		assertNull(selection.owner());
	}

	@Test
	public void rotationPreservesAPickerWhoseResultHasNotArrived() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, null, true);
		assertNull(selection.take(OWNER, 7, true));
		selection.result(PHOTO);
		assertEquals(PHOTO, selection.take(OWNER, 7, true));
	}

	@Test
	public void replacementControllerRebindsTheSameUidAtItsNewRevision() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, PHOTO, false);
		assertNull(selection.take(null, 0, false));
		assertEquals(OWNER, selection.owner());
		assertEquals(PHOTO, selection.take(OWNER, 1, true));
	}

	@Test
	public void replacementControllerWaitsForSignInBeforeRebinding() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, PHOTO, false);
		assertNull(selection.take(OWNER, 1, false));
		assertEquals(-1, selection.revision());
		assertEquals(PHOTO, selection.take(OWNER, 1, true));
	}

	@Test
	public void replacementControllerRejectsAnotherRestoredUid() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, PHOTO, false);
		assertNull(selection.take("uid-b", 1, true));
		assertNull(selection.owner());
	}

	@Test
	public void replacementControllerRejectsACompletedSignedOutSession() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.restore(OWNER, 7, PHOTO, false);
		assertNull(selection.take(null, 0, true));
		assertNull(selection.owner());
	}

	private PendingAvatarSelection selected() {
		PendingAvatarSelection selection = new PendingAvatarSelection();
		selection.begin(OWNER, 7);
		selection.result(PHOTO);
		return selection;
	}
}
