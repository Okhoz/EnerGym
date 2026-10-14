package com.energym.server;

import java.io.IOException;

/**
 * Error externo reducido al contrato público; nunca conserva cuerpos ni secretos del proveedor.
 */
final class ProviderFailure extends IOException {
	private final int status;
	private final boolean configuration;

	ProviderFailure(int status) {
		this(status, false);
	}

	private ProviderFailure(int status, boolean configuration) {
		super("Proveedor no disponible");
		this.status = status == 429 ? 429 : status == 503 ? 503 : 502;
		this.configuration = configuration;
	}

	static ProviderFailure notConfigured() {
		return new ProviderFailure(503, true);
	}

	int status() {
		return status;
	}

	String code() {
		return configuration ? "PROVIDER_NOT_CONFIGURED" : status == 429 ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE";
	}
}
