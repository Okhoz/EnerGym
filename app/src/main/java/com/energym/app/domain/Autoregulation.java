package com.energym.app.domain;

/**
 * MODELO: consejo para la siguiente serie según el esfuerzo de la última (RPE), como haría un entrenador en la
 * sala: el objetivo es un RPE de 8 (quedaban 2 repeticiones). Con carga ajusta los kilos; sin carga, las
 * repeticiones.
 */
public final class Autoregulation {
	public static final class Advice {
		public final double kg;
		public final int reps;
		public final String text;

		Advice(double kg, int reps, String text) {
			this.kg = kg;
			this.reps = reps;
			this.text = text;
		}
	}

	private Autoregulation() {
	}

	/** null si la serie no tiene RPE o es de calentamiento. {@code step} es el salto mínimo de carga (2,5 kg). */
	public static Advice next(SetLog.Entry last, double step) {
		if (last == null || last.rpe <= 0 || !last.counts())
			return null;
		double rpe = last.rpe;
		if (last.kg <= 0) {
			int reps = rpe >= 9.5 ? Math.max(1, last.reps - 2) : rpe <= 6.5 ? last.reps + 2 : rpe <= 7.5 ? last.reps + 1 :
				last.reps;
			return new Advice(0, reps, rpe >= 9.5 ? "Al límite: haz " + reps + " repeticiones en la siguiente." :
				reps > last.reps ? "Te sobra margen: intenta " + reps + " repeticiones." :
					"Bien ajustado: repite " + reps + " repeticiones.");
		}
		double kg;
		String text;
		if (rpe >= 9.5 || last.kind == SetLog.Kind.FAILURE) {
			kg = round(last.kg * 0.9, step);
			text = "Al límite: baja a " + fmt(kg) + " kg para mantener la técnica.";
		} else if (rpe >= 8.75) {
			kg = last.kg;
			text = "Cerca del límite: mantén " + fmt(kg) + " kg; si fallas, baja un poco.";
		} else if (rpe >= 7.75) {
			kg = last.kg;
			text = "Esfuerzo perfecto: repite " + fmt(kg) + " kg.";
		} else if (rpe >= 6.75) {
			kg = last.kg + step;
			text = "Te quedan reservas: prueba con " + fmt(kg) + " kg.";
		} else {
			kg = Math.max(last.kg + 2 * step, round(last.kg * 1.05, step));
			text = "Muy fácil: sube a " + fmt(kg) + " kg.";
		}
		return new Advice(kg, last.reps, text);
	}

	private static double round(double kg, double step) {
		return Math.max(step, Math.round(kg / step) * step);
	}

	private static String fmt(double kg) {
		return SetLog.format(kg).replace('.', ',');
	}
}
