package com.energym.app.domain;

import java.util.*;

/**
 * MODELO de las series de un ejercicio de fuerza apuntadas una a una (como en FitNotes): «60:5;80:5» = 60 kg × 5 y
 * 80 kg × 5. Se guarda junto al resumen de siempre (series, repeticiones y carga) para que historial, gráficas y
 * sobrecarga progresiva sigan funcionando igual.
 */
public final class SetLog {
	public static final String KEY = "setLog";
	/** Nota libre del ejercicio en ese entreno («me molestó el hombro»). */
	public static final String NOTE = "note";
	public static final int MAX_SETS = 30;

	/** Tipo de serie: normal, calentamiento (no cuenta para progresar ni récords), drop set o al fallo. */
	public enum Kind {
		NORMAL('N', ""), WARMUP('W', "Calentamiento"), DROP('D', "Drop set"), FAILURE('F', "Al fallo");

		public final char code;
		public final String label;

		Kind(char code, String label) {
			this.code = code;
			this.label = label;
		}

		static Kind of(char code) {
			for (Kind k : values())
				if (k.code == code)
					return k;
			return NORMAL;
		}
	}

	public static final class Entry {
		public final double kg;
		public final int reps;
		public final Kind kind;
		/** Esfuerzo percibido 1–10 (RPE); 0 si no se indicó. */
		public final double rpe;

		public Entry(double kg, int reps) {
			this(kg, reps, Kind.NORMAL, 0);
		}

		public Entry(double kg, int reps, Kind kind, double rpe) {
			this.kg = kg;
			this.reps = reps;
			this.kind = kind == null ? Kind.NORMAL : kind;
			this.rpe = rpe < 0 || rpe > 10 ? 0 : rpe;
		}

		public boolean counts() {
			return kind != Kind.WARMUP;
		}

		/** 1RM estimado (Epley); con 1 repetición es el propio peso. */
		public double oneRepMax() {
			return reps <= 1 ? kg : kg * (1 + reps / 30.0);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Entry && ((Entry) o).kg == kg && ((Entry) o).reps == reps &&
				((Entry) o).kind == kind && ((Entry) o).rpe == rpe;
		}

		@Override
		public int hashCode() {
			return Objects.hash(kg, reps, kind, rpe);
		}
	}

	private SetLog() {
	}

	public static boolean valid(String encoded) {
		String set = "\\d{1,4}(?:\\.\\d{1,2})?:\\d{1,3}(?::[NWDF](?::\\d{1,2}(?:\\.\\d)?)?)?";
		return encoded != null && encoded.matches(set + "(?:;" + set + "){0,29}");
	}

	public static List<Entry> parse(String encoded) {
		List<Entry> result = new ArrayList<>();
		if (!valid(encoded))
			return result;
		for (String part : encoded.split(";")) {
			String[] kv = part.split(":");
			int reps = Integer.parseInt(kv[1]);
			Kind kind = kv.length > 2 ? Kind.of(kv[2].charAt(0)) : Kind.NORMAL;
			double rpe = kv.length > 3 ? Double.parseDouble(kv[3]) : 0;
			if (reps > 0)
				result.add(new Entry(Double.parseDouble(kv[0]), reps, kind, rpe));
		}
		return result;
	}

	public static String encode(List<Entry> sets) {
		StringBuilder s = new StringBuilder();
		for (Entry e : sets)
		{
			s.append(s.length() == 0 ? "" : ";").append(format(e.kg)).append(':').append(e.reps);
			// Lo nuevo solo se escribe si hace falta: los registros antiguos siguen siendo «kg:reps».
			if (e.kind != Kind.NORMAL || e.rpe > 0)
				s.append(':').append(e.kind.code);
			if (e.rpe > 0)
				s.append(':').append(format(e.rpe));
		}
		return s.toString();
	}

	/** Series de unos valores guardados: las apuntadas una a una o, si no hay, las del resumen («3 × 10 · 20 kg»). */
	public static List<Entry> of(Map<String, String> values) {
		if (values == null)
			return Collections.emptyList();
		List<Entry> logged = parse(values.get(KEY));
		if (!logged.isEmpty())
			return logged;
		List<Entry> result = new ArrayList<>();
		try {
			int sets = (int) Double.parseDouble(values.getOrDefault("sets", "0"));
			int reps = (int) Double.parseDouble(values.getOrDefault("repetitions", "0"));
			double kg = Double.parseDouble(values.getOrDefault("loadKg", "0"));
			for (int i = 0; i < Math.min(sets, MAX_SETS) && reps > 0; i++)
				result.add(new Entry(kg, reps));
		} catch (NumberFormatException ignored) {
			// Valores antiguos sin cifras válidas: sin series que mostrar.
		}
		return result;
	}

	/**
	 * Valores completos de fuerza a partir de las series: nº de series, repeticiones medias, carga máxima y el
	 * descanso/esfuerzo que ya hubiera. Sin series devuelve null.
	 */
	public static Map<String, String> summary(List<Entry> sets, Map<String, String> base, String defaultRest) {
		if (sets.isEmpty())
			return null;
		Map<String, String> values = new LinkedHashMap<>();
		// El resumen (y con él la progresión) usa las series efectivas; el calentamiento no cuenta.
		List<Entry> working = new ArrayList<>();
		for (Entry e : sets)
			if (e.counts())
				working.add(e);
		if (working.isEmpty())
			working = sets;
		int reps = 0;
		double kg = 0, rpe = 0;
		int rated = 0;
		for (Entry e : working) {
			reps += e.reps;
			kg = Math.max(kg, e.kg);
			if (e.rpe > 0) {
				rpe += e.rpe;
				rated++;
			}
		}
		values.put("sets", Integer.toString(working.size()));
		values.put("repetitions", Integer.toString(Math.max(1, Math.round(reps / (float) working.size()))));
		values.put("loadKg", format(kg));
		String rest = base == null ? "" : base.getOrDefault("restSeconds", "");
		values.put("restSeconds", rest.isEmpty() ? defaultRest : rest);
		if (rated > 0)
			values.put("effort", format(Math.round(rpe / rated * 10) / 10.0));
		else if (base != null && !base.getOrDefault("effort", "").isEmpty())
			values.put("effort", base.get("effort"));
		if (base != null && !base.getOrDefault(NOTE, "").isEmpty())
			values.put(NOTE, base.get(NOTE));
		values.put(KEY, encode(sets));
		return values;
	}

	/** Mejor 1RM estimado entre unas series (0 si no hay); el calentamiento no cuenta. */
	public static double best(List<Entry> sets) {
		double best = 0;
		for (Entry e : sets)
			if (e.counts())
				best = Math.max(best, e.oneRepMax());
		return best;
	}

	public static String format(double kg) {
		return kg == Math.rint(kg) ? Long.toString((long) kg) : String.format(Locale.ROOT, "%.2f", kg)
			.replaceAll("0+$", "").replaceAll("\\.$", "");
	}
}
