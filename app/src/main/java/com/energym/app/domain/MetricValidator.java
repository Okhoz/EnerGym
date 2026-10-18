package com.energym.app.domain;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
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

	/** Nombre para mostrar de un valor de elección (se guarda siempre en español). */
	public static String choiceLabel(String value) {
		switch (value) {
			case "ambos":
				return Txt.t(R.string.side_both);
			case "izquierdo":
				return Txt.t(R.string.side_left);
			case "derecho":
				return Txt.t(R.string.side_right);
			default:
				return value;
		}
	}

	public List<Field> fields(MetricType type) {
		List<Field> f = new ArrayList<>();
		switch (Objects.requireNonNull(type)) {
			case STRENGTH:
				f.add(new Field("sets", Txt.t(R.string.metric_sets), true, false));
				f.add(new Field("repetitions", Txt.t(R.string.metric_reps_per_set), true, false));
				f.add(new Field("loadKg", Txt.t(R.string.metric_load), false, false));
				f.add(new Field("restSeconds", Txt.t(R.string.metric_rest), false, false));
				break;
			case ISOMETRIC:
				f.add(new Field("sets", Txt.t(R.string.metric_sets), true, false));
				f.add(new Field("durationSeconds", Txt.t(R.string.metric_duration_per_set), false, false));
				f.add(new Field("restSeconds", Txt.t(R.string.metric_rest), false, false));
				break;
			case ENDURANCE:
				f.add(new Field("timeMinutes", Txt.t(R.string.metric_time), false, false));
				f.add(new Field("intervals", Txt.t(R.string.metric_intervals), true, false));
				f.add(new Field("distanceKm", Txt.t(R.string.metric_distance), false, true));
				break;
			case MOBILITY:
				f.add(new Field("durationSeconds", Txt.t(R.string.metric_duration), false, false));
				f.add(new Field("repetitions", Txt.t(R.string.metric_reps), true, false));
				f.add(new Field("side", Txt.t(R.string.metric_side), false, false, "ambos", "izquierdo", "derecho"));
				break;
			case SPORT_PRACTICE:
				f.add(new Field("blocks", Txt.t(R.string.metric_blocks), true, false));
				f.add(new Field("durationMinutes", Txt.t(R.string.metric_practice), false, false));
				break;
		}
		f.add(new Field("effort", Txt.t(R.string.metric_effort), false, true));
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
					errors.add(Txt.t(R.string.metric_required, f.label));
				continue;
			}
			if (!f.choices.isEmpty()) {
				if (!f.choices.contains(value))
					errors.add(Txt.t(R.string.metric_bad_side, f.label));
				continue;
			}
			try {
				if (!value.matches("[+-]?\\d+(?:[.,]\\d+)?"))
					throw new NumberFormatException();
				BigDecimal n = new BigDecimal(value.replace(',', '.'));
				boolean allowZero = f.key.equals("loadKg") || f.key.equals("restSeconds") || f.key.equals("distanceKm") || f.key.equals("effort");
				if (n.signum() < 0 || (!allowZero && n.signum() == 0))
					errors.add(Txt.t(allowZero ? R.string.metric_zero_or_more : R.string.metric_positive, f.label));
				if (f.integer && n.stripTrailingZeros().scale() > 0)
					errors.add(Txt.t(R.string.metric_integer, f.label));
				if (f.key.equals("effort") && n.compareTo(BigDecimal.TEN) > 0)
					errors.add(Txt.t(R.string.metric_max_10, f.label));
			} catch (NumberFormatException e) {
				errors.add(Txt.t(R.string.metric_number, f.label));
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
