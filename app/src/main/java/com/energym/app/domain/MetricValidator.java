package com.energym.app.domain;

import java.util.*;
import java.math.BigDecimal;

/**
 * MODEL: valida valores realizados sin depender de Android ni de la prescripción propuesta.
 */
public final class MetricValidator {
	/**
	 * Esquema de formulario: unidades y obligatoriedad son parte del modelo de la métrica.
	 */
	public static final class Field {
		public final String key, label;
		public final boolean integer, optional;
		public final List<String> choices;

		private Field(String k, String l, boolean i, boolean o, String... choices) {
			key = k;
			label = l;
			integer = i;
			optional = o;
			this.choices = Collections.unmodifiableList(Arrays.asList(choices));
		}
	}

	public List<Field> fields(MetricType type) {
		List<Field> f = new ArrayList<>();
		switch (Objects.requireNonNull(type)) {
			case STRENGTH:
				f.add(new Field("sets", "Series realizadas", true, false));
				f.add(new Field("repetitions", "Repeticiones por serie", true, false));
				f.add(new Field("loadKg", "Carga (kg; 0 = peso corporal)", false, false));
				f.add(new Field("restSeconds", "Descanso entre series (s)", false, false));
				break;
			case ISOMETRIC:
				f.add(new Field("sets", "Series realizadas", true, false));
				f.add(new Field("durationSeconds", "Duración por serie (s)", false, false));
				f.add(new Field("restSeconds", "Descanso entre series (s)", false, false));
				break;
			case ENDURANCE:
				f.add(new Field("timeMinutes", "Tiempo realizado (min)", false, false));
				f.add(new Field("intervals", "Intervalos / bloques realizados", true, false));
				f.add(new Field("distanceKm", "Distancia (km; opcional)", false, true));
				break;
			case MOBILITY:
				f.add(new Field("durationSeconds", "Duración (s)", false, false));
				f.add(new Field("repetitions", "Repeticiones", true, false));
				f.add(new Field("side", "Lado trabajado", false, false, "ambos", "izquierdo", "derecho"));
				break;
			case SPORT_PRACTICE:
				f.add(new Field("blocks", "Bloques realizados", true, false));
				f.add(new Field("durationMinutes", "Tiempo de práctica (min)", false, false));
				break;
		}
		f.add(new Field("effort", "Esfuerzo percibido (0–10; opcional)", false, true));
		return Collections.unmodifiableList(f);
	}

	/**
	 * Acepta coma decimal española; nunca acepta NaN, infinito, negativos ni fracciones de repeticiones.
	 */
	public List<String> validate(MetricType type, Map<String, String> values) {
		List<String> errors = new ArrayList<>();
		Map<String, String> source = values == null ? Collections.emptyMap() : values;
		for (Field f : fields(type)) {
			String value = source.getOrDefault(f.key, "").trim();
			if (value.isEmpty()) {
				if (!f.optional)
					errors.add(f.label + ": obligatorio.");
				continue;
			}
			if (!f.choices.isEmpty()) {
				if (!f.choices.contains(value))
					errors.add(f.label + ": selecciona un lado válido.");
				continue;
			}
			try {
				if (!value.matches("[+-]?\\d+(?:[.,]\\d+)?"))
					throw new NumberFormatException();
				BigDecimal n = new BigDecimal(value.replace(',', '.'));
				boolean allowZero = f.key.equals("loadKg") || f.key.equals("restSeconds") || f.key.equals("distanceKm") || f.key.equals("effort");
				if (n.signum() < 0 || (!allowZero && n.signum() == 0))
					errors.add(f.label + ": debe ser " + (allowZero ? "cero o mayor." : "mayor que cero."));
				if (f.integer && n.stripTrailingZeros().scale() > 0)
					errors.add(f.label + ": introduce un número entero.");
				if (f.key.equals("effort") && n.compareTo(BigDecimal.TEN) > 0)
					errors.add(f.label + ": máximo 10.");
			} catch (NumberFormatException e) {
				errors.add(f.label + ": introduce un número válido (se admite coma decimal).");
			}
		}
		return Collections.unmodifiableList(errors);
	}

	/**
	 * Guarda solo el esquema conocido, con decimales portátiles, sin alterar el mapa editable de la vista.
	 */
	public Map<String, String> normalized(MetricType type, Map<String, String> values) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Field f : fields(type)) {
			String v = values.getOrDefault(f.key, "").trim();
			if (!v.isEmpty())
				result.put(f.key, v.replace(',', '.'));
		}
		// Fuerza también conserva sus series una a una (estilo FitNotes) si el formato es válido.
		if (type == MetricType.STRENGTH && SetLog.valid(values.get(SetLog.KEY)))
			result.put(SetLog.KEY, values.get(SetLog.KEY));
		// Nota libre del ejercicio (cualquier tipo), en una línea y como mucho 200 caracteres.
		String note = values.getOrDefault(SetLog.NOTE, "").replaceAll("\\s+", " ").trim();
		if (!note.isEmpty())
			result.put(SetLog.NOTE, note.length() > 200 ? note.substring(0, 200) : note);
		return result;
	}
}
