package com.energym.server;

import java.io.IOException;

/**
 * Error externo reducido al contrato público; nunca conserva cuerpos ni secretos del proveedor.
 */
final class ProviderFailure extends IOException {
	private final int status;

	ProviderFailure(int status) {
		super("Proveedor no disponible");
		this.status = status == 429 ? 429 : status == 503 ? 503 : 502;
	}

	int status() {
		return status;
	}

	String code() {
		return status == 429 ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE";
	}
}
