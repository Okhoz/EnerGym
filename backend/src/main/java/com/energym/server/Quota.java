package com.energym.server;

/** Reserva atómica antes de llamar al proveedor: false significa cuota agotada, excepción falla cerrada. */
@FunctionalInterface
public interface Quota {
	boolean reserve(String uid) throws Exception;
}
