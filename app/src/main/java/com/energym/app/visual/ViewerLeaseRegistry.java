package com.energym.app.visual;

/**
 * Registro mínimo en el proceso padre; no retiene cuentas, vistas ni recursos nativos.
 */
public final class ViewerLeaseRegistry {
	private static String lease, exerciseId;

	private ViewerLeaseRegistry() {
	}

	public static synchronized void publish(String token, String id) {
		lease = token;
		exerciseId = id;
	}

	public static synchronized void revoke(String token) {
		if (token != null && token.equals(lease)) {
			lease = null;
			exerciseId = null;
		}
	}

	public static synchronized boolean valid(String token, String id) {
		return token != null && id != null && token.equals(lease) && id.equals(exerciseId);
	}
}
