package com.energym.app.auth;

/**
 * Identidad MODEL creada desde FirebaseUser; nombre/correo son presentación, nunca claves de datos.
 */
public final class AccountUser {
	private final String uid, displayName, email, photoUrl;

	public AccountUser(String uid, String displayName, String email) {
		this(uid, displayName, email, "");
	}

	public AccountUser(String uid, String displayName, String email, String photoUrl) {
		if (uid == null || uid.trim().isEmpty() || uid.contains("/") || uid.startsWith("demo:"))
			throw new IllegalArgumentException("UID autenticado inválido");
		this.uid = uid;
		this.displayName = displayName == null ? "" : displayName;
		this.email = email == null ? "" : email;
		this.photoUrl = photoUrl == null ? "" : photoUrl.trim();
	}

	public String getUid() {
		return uid;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPhotoUrl() {
		return photoUrl;
	}

	public String getEmail() {
		return email;
	}
}
