package com.energym.app.domain;

import java.util.*;

/**
 * MODELO de las series de aproximación antes del peso de trabajo: barra vacía y subidas al 40, 60 y 80 % con
 * cada vez menos repeticiones, para calentar sin cansar. Se redondean al salto disponible y se omiten las que
 * repiten carga o no superan la barra.
 */
public final class WarmupSets {
	private static final double[] PERCENT = {0.4, 0.6, 0.8};
	private static final int[] REPS = {5, 3, 2};

	private WarmupSets() {
	}

	/**
	 * @param workKg peso de trabajo.
	 * @param barKg  peso de la barra vacía (0 si no hay barra: se empieza en el 40 %).
	 * @param step   salto mínimo de carga.
	 * @return series de calentamiento (tipo WARMUP), vacía si el peso de trabajo es demasiado ligero.
	 */
	public static List<SetLog.Entry> of(double workKg, double barKg, double step) {
		List<SetLog.Entry> result = new ArrayList<>();
		if (workKg <= 0 || step <= 0 || barKg < 0)
			return result;
		double floor = barKg > 0 ? barKg : step;
		if (workKg <= floor + step)
			return result;
		if (barKg > 0)
			result.add(new SetLog.Entry(barKg, 10, SetLog.Kind.WARMUP, 0));
		double last = barKg;
		for (int i = 0; i < PERCENT.length; i++) {
			double kg = Math.round(workKg * PERCENT[i] / step) * step;
			if (kg <= last + 0.001 || kg >= workKg - 0.001 || kg < floor)
				continue;
			result.add(new SetLog.Entry(kg, REPS[i], SetLog.Kind.WARMUP, 0));
			last = kg;
		}
		return result;
	}
}
