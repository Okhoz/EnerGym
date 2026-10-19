package com.energym.app.controller;

import com.energym.app.domain.Session;

import java.util.List;

/** Acceso de los tests de otros paquetes a los cálculos que AssistantContext prepara para el entrenador IA. */
public final class AssistantContextAccess {
	private AssistantContextAccess() {
	}

	public static String nextLoads(List<Session> history) {
		return AssistantContext.nextLoads(history);
	}

	public static String personal(List<Session> history) {
		return AssistantContext.personal(history, null);
	}
}
