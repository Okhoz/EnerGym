package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.*;

/**
 * Controlador de aplicación: autentica, limita, elimina historial sin consentimiento y valida el resultado del modelo.
 */
public final class ChatService {
	public int catalogVersion() {
		return validator.catalogVersion();
	}

	public record Result(int status, JsonNode body) {
	}

	private final SessionTokenStore tokens;
	private final Provider provider;
	private final ProposalValidator validator;
	private final Semaphore inFlight = new Semaphore(2);

	public ChatService(SessionTokenStore tokens, Provider provider, ProposalValidator validator) {
		this.tokens = tokens;
		this.provider = provider;
		this.validator = validator;
	}

	/**
	 * Tokens de backend independientes del UID Google; no guarda conversaciones ni aplica rutinas.
	 */
	public Result chat(String token, JsonNode request) {
		if (!tokens.authenticated(token))
			return error(401, "UNAUTHORIZED", false);
		if (!tokens.allowChat(token))
			return error(429, "RATE_LIMITED", true);
		try {
			validator.validateRequest(request);
		} catch (IllegalArgumentException e) {
			return error(400, "INVALID_REQUEST", false);
		}
		if (!provider.configured())
			return correlated(error(503, "PROVIDER_NOT_CONFIGURED", true), request);
		if (!inFlight.tryAcquire())
			return correlated(error(429, "SERVER_BUSY", true), request);
		try {
			JsonNode out = provider.respond(validator.providerRequest(request));
			validator.validateResponse(out, request);
			ObjectNode result = out.deepCopy();
			result.put("catalogVersion", validator.catalogVersion());
			result.put("proposalNotice", "Ejemplo sin revisión profesional; requiere vista previa y aceptación local.");
			return correlated(new Result(200, result), request);
		} catch (ProviderFailure e) {
			return correlated(error(e.status(), e.code(), true), request);
		} catch (HttpTimeoutException | TimeoutException e) {
			return correlated(error(504, "PROVIDER_TIMEOUT", true), request);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return correlated(error(503, "PROVIDER_UNAVAILABLE", true), request);
		} catch (IllegalArgumentException e) {
			return correlated(error(502, "INVALID_PROVIDER_RESPONSE", true), request);
		} catch (Exception e) {
			return correlated(error(502, "PROVIDER_UNAVAILABLE", true), request);
		} finally {
			inFlight.release();
		}
	}

	static Result error(int status, String code, boolean recoverable) {
		ObjectNode b = Json.object();
		b.put("error", code);
		b.put("recoverable", recoverable);
		return new Result(status, b);
	}

	private Result correlated(Result result, JsonNode request) {
		ObjectNode b = (ObjectNode) result.body();
		for (String key : new String[]{"conversationId", "requestId", "exerciseId"})
			b.set(key, request.get(key));
		return result;
	}
}
