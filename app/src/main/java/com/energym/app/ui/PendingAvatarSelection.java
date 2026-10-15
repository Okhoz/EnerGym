package com.energym.app.ui;

/** La selección pertenece a la cuenta y a la revisión que abrió el selector. */
public final class PendingAvatarSelection {
	private String owner, uri;
	private long revision = -1;

	public void begin(String owner, long revision) {
		this.owner = owner;
		this.revision = revision;
		uri = null;
	}

	/** Solo un controlador nuevo puede asociar el UID restaurado a su nueva revisión. */
	public void restore(String owner, long revision, String uri, boolean sameController) {
		this.owner = owner;
		this.revision = sameController ? revision : -1;
		this.uri = owner == null ? null : uri;
	}

	public void result(String uri) {
		if (uri == null)
			clear();
		else if (owner != null)
			this.uri = uri;
	}

	/** Entrega el resultado una vez confirmada la identidad; lo conserva durante la restauración. */
	public String take(String currentOwner, long currentRevision, boolean accountSettled) {
		if (owner == null)
			return null;
		if (revision != -1 && revision != currentRevision) {
			clear();
			return null;
		}
		if (!accountSettled)
			return null;
		if (!owner.equals(currentOwner)) {
			clear();
			return null;
		}
		revision = currentRevision;
		String result = uri;
		if (result != null)
			clear();
		return result;
	}

	public String owner() { return owner; }
	public long revision() { return revision; }
	public String uri() { return uri; }

	private void clear() {
		owner = uri = null;
		revision = -1;
	}
}
