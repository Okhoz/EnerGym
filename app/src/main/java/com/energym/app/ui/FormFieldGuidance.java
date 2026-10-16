package com.energym.app.ui;

import java.util.Locale;

/** Texto de ayuda de la vista: los ejemplos nunca forman parte del valor ni de la validación. */
public final class FormFieldGuidance {
	private FormFieldGuidance() {
	}

	/** Ejemplos sin unidades para los campos numéricos; la etiqueta conserva la unidad. */
	public static String placeholder(String key, String label, boolean number, boolean integer) {
		String name = key.startsWith("manual_") ? key.substring("manual_".length()) : key;
		if (name.startsWith("detail."))
			name = name.substring("detail.".length());
		if (name.endsWith("_search") || name.equals("search"))
			return "Ej.: sentadilla o remo";
		switch (name) {
			case "age": return "Ej.: 30";
			case "heightCm": return "Ej.: 175";
			case "weightKg":
			case "weight": return "Ej.: 75.5";
			case "daysPerWeek": return "Ej.: 3";
			case "duration":
			case "durationMinutes": return "Ej.: 45";
			case "durationHours":
			case "manual_duration": return "Ej.: 0:45";
			case "estimatedMinutes": return "Ej.: 10";
			case "level": return "Ej.: principiante";
			case "experience": return "Ej.: 6 meses de entrenamiento de fuerza";
			case "space": return "Ej.: una habitación despejada";
			case "preferences": return "Ej.: prefiero ejercicios con mancuernas";
			case "limitations": return "Ej.: evitar saltos";
			case "priority": return "Ej.: espalda o sentadilla";
			case "maxPushups": return "Ej.: 12";
			case "maxPullups": return "Ej.: 3";
			case "weeklyKm": return "Ej.: 20";
			case "bestTime": return "Ej.: 5 km en 28 min";
			case "ftp": return "Ej.: 200";
			case "sessionMeters": return "Ej.: 1000";
			case "matchesPerWeek": return "Ej.: 2";
			case "elevation": return "Ej.: 500";
			case "grade": return "Ej.: 6a";
			case "sportName": return "Ej.: bádminton";
			case "title": return "Ej.: Mi rutina de fuerza";
			case "sets":
			case "blocks": return "Ej.: 3";
			case "repetitions": return "Ej.: 10";
			case "loadKg": return "Ej.: 20";
			case "restSeconds": return "Ej.: 60";
			case "durationSeconds": return "Ej.: 30";
			case "timeMinutes": return "Ej.: 20";
			case "intervals": return "Ej.: 4";
			case "distanceKm": return "Ej.: 5";
			case "effort": return "Ej.: 7";
			case "amount": return "Ej.: 150";
			case "name": return "Ej.: yogur natural";
			case "kcal100": return "Ej.: 65";
			case "protein100": return "Ej.: 4.5";
			case "carbs100": return "Ej.: 5";
			case "fat100": return "Ej.: 3";
			case "allergies": return "Ej.: cacahuetes, lactosa";
			case "barcode": return "Ej.: 8412345678905";
			case "assistant_endpoint": return "Ej.: https://ia.tudominio.es";
			case "assistant_pairing": return "Pega tu código privado de emparejamiento";
			case "goal_target": return goalExample(label);
			default: return number ? (integer ? "Ej.: 3" : "Ej.: 10.5") : "Escribe tu respuesta";
		}
	}

	private static String goalExample(String label) {
		String text = label.toLowerCase(Locale.ROOT);
		if (text.contains("sesiones")) return "Ej.: 3";
		if (text.contains("min")) return "Ej.: 150";
		if (text.contains("km")) return "Ej.: 20";
		if (text.contains("kg") && text.contains("semana")) return "Ej.: 5000";
		return "Ej.: 70";
	}

	/** La condición opcional permanece visible aunque desaparezca el ejemplo al escribir. */
	public static String profileLabel(String key, String label) {
		if (key.equals("detail.grade")) label = "Grado que encadenas";
		if (key.equals("detail.ftp")) label = "FTP (W)";
		boolean optional = key.startsWith("detail.") || key.equals("experience") ||
			key.equals("space") || key.equals("preferences") || key.equals("limitations");
		return optional && !label.toLowerCase(Locale.ROOT).contains("opcional") ? label + " (opcional)" : label;
	}

	/** Ayudas solo para el perfil; las métricas de sesión comparten claves, pero no sus límites. */
	public static String profileHelper(String key) {
		switch (key) {
			case "sportId": return "La disciplina determina qué ejercicios y preguntas verás.";
			case "category": return "Elige el tipo de entrenamiento que podrás realizar.";
			case "focus": return "Distingue la práctica del deporte de su preparación complementaria.";
			case "environment": return "Indica dónde harás las sesiones para orientar la selección de ejercicios.";
			case "goal": return "Da prioridad al resultado que buscas con tu rutina.";
			case "daysPerWeek": return "Indica cuántos días podrás entrenar cada semana.";
			case "durationMinutes": return "Es el tiempo disponible para cada sesión, en minutos.";
			case "durationHours": return "Formato horas:minutos. 0:20 h = 20 minutos, 1:30 h = hora y media.";
			case "level": return "Describe tu nivel actual para orientar la dificultad.";
			case "experience": return "Cuenta qué has practicado y durante cuánto tiempo.";
			case "space": return "Describe el espacio libre en el que podrás moverte.";
			case "preferences": return "Indica ejercicios o formas de entrenar que disfrutas.";
			case "limitations": return "Señala movimientos o actividades que prefieres evitar.";
			case "historyConsent": return "Opcional. Actívalo si quieres adjuntar mensajes anteriores al chat conectado.";
			case "detail.split": return "Indica cómo prefieres repartir el trabajo muscular.";
			case "detail.priority": return "Añade una zona o un ejercicio al que quieras dar prioridad.";
			case "detail.skill": return "Señala una habilidad que quieras practicar.";
			case "detail.maxPushups":
			case "detail.maxPullups": return "Indica las repeticiones seguidas que haces con buena técnica.";
			case "detail.format": return "Describe la modalidad o el formato que practicas.";
			case "detail.olympic": return "Sitúa tu experiencia con los movimientos de halterofilia.";
			case "detail.raceGoal": return "Añade la distancia o el reto de carrera que tienes en mente.";
			case "detail.weeklyKm": return "Aporta tu distancia semanal actual, en kilómetros.";
			case "detail.bestTime": return "Incluye la distancia y el tiempo de una marca reciente.";
			case "detail.bike": return "Describe la bicicleta con la que entrenas.";
			case "detail.ftp": return "Añade tu potencia funcional en vatios, solo si ya la conoces.";
			case "detail.stroke": return "Indica el estilo que nadas con más frecuencia.";
			case "detail.pool": return "Da contexto sobre el espacio en el que nadas.";
			case "detail.sessionMeters": return "Aporta la distancia que nadas en una sesión habitual, en metros.";
			case "detail.position": return "Aporta contexto sobre tu función en el equipo.";
			case "detail.matchesPerWeek": return "Indica cuántos partidos juegas además de tus entrenamientos.";
			case "detail.terrain": return "Describe el terreno de tus salidas habituales.";
			case "detail.elevation": return "Indica los metros de subida acumulados en una salida habitual.";
			case "detail.pack": return "Describe la carga que sueles llevar en la mochila.";
			case "detail.style": return "Describe la modalidad que practicas habitualmente.";
			case "detail.grade": return "Indica un grado que completes con regularidad.";
			case "detail.competes": return "Añade tu experiencia en competición.";
			case "detail.area": return "Señala la zona o capacidad que te interesa trabajar.";
			case "detail.modality": return "Indica cómo practicas esta disciplina.";
			case "detail.side": return "Describe tu lado habitual de juego.";
			case "detail.hand": return "Indica con qué mano juegas habitualmente.";
			case "detail.backhand": return "Indica cómo ejecutas el revés.";
			case "detail.surface": return "Describe la pista o superficie donde juegas.";
			case "detail.sportName": return "Da nombre a la disciplina que no aparece en la lista.";
			default: return key.startsWith("detail.") ? "Añade contexto sobre tu práctica; puedes dejarlo sin indicar." : "";
		}
	}
}
