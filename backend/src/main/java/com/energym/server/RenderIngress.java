package com.energym.server;

import com.sun.net.httpserver.Headers;
import java.util.*;

/** Render termina TLS; este contrato sólo se habilita explícitamente en su entorno administrado. */
final class RenderIngress {
	private final String hostname;
	private RenderIngress(String hostname) { this.hostname = hostname; }
	static RenderIngress fromEnvironment(Map<String, String> env) {
		String hostname = env.getOrDefault("RENDER_EXTERNAL_HOSTNAME", "");
		if (!"true".equals(env.get("RENDER")) || !"firebase".equals(env.get("ENERGYM_AUTH_MODE")) ||
			!hostname.matches("[a-z0-9][a-z0-9-]*\\.onrender\\.com"))
			throw new IllegalArgumentException("Render administrado y autenticación Firebase obligatorios");
		return new RenderIngress(hostname);
	}
	boolean accepts(Headers headers) {
		List<String> hosts = headers.get("Host"), protocols = headers.get("X-Forwarded-Proto");
		return hosts != null && hosts.size() == 1 &&
			(hostname.equalsIgnoreCase(hosts.get(0)) || (hostname + ":443").equalsIgnoreCase(hosts.get(0))) &&
			protocols != null && protocols.size() == 1 && "https".equals(protocols.get(0));
	}
}
