package com.energym.app.auth;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AccountUserPhotoTest {
	@Test public void googlePhotoBelongsToTheSameImmutableIdentity() {
		AccountUser user = new AccountUser("uid-a", "Nombre", "correo", "https://lh3.googleusercontent.com/photo");
		assertEquals("uid-a", user.getUid());
		assertEquals("https://lh3.googleusercontent.com/photo", user.getPhotoUrl());
	}
	@Test public void olderIdentitiesAndMissingPhotosKeepTheFallback() {
		assertEquals("", new AccountUser("uid-a", "", "").getPhotoUrl());
		assertEquals("", new AccountUser("uid-a", "", "", null).getPhotoUrl());
	}
}
