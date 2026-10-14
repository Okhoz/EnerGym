package com.energym.server;
import com.fasterxml.jackson.databind.JsonNode;
/** Frontera del proveedor: permite probar sin conexión ni facturación. */
@FunctionalInterface public interface Provider {
    JsonNode respond(JsonNode request) throws Exception;
    default boolean configured(){ return true; }
}

