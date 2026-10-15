package com.energym.server;

/** null rechaza la credencial; una excepción indica que no se pudo verificar con seguridad. */
@FunctionalInterface
public interface Authenticator {
	String uid(String token) throws Exception;
}
