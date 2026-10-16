package com.energym.app.domain;

import java.util.*;

/**
 * MODELO de la calculadora de discos, la unidad de peso (kg/lb) y la tabla de porcentajes del 1RM. Lo guardado
 * siempre está en kg; las libras solo se usan para mostrar y escribir.
 */
public final class Plates {
	public static final double LB_PER_KG = 2.20462262;
	public static final double[] KG_PLATES = {25, 20, 15, 10, 5, 2.5, 1.25};
	public static final double[] LB_PLATES = {45, 35, 25, 10, 5, 2.5};
	public static final double KG_BAR = 20, LB_BAR = 45;
	/** Porcentajes de la tabla del 1RM, de 100 % a 50 %. */
	public static final int[] PERCENTS = {100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50};

	private Plates() {
	}

	/** Discos por lado para cargar la barra lo más cerca posible (sin pasarse) del total. */
	public static final class Load {
		public final List<Double> perSide;
		/** Peso total que queda montado (barra + discos). */
		public final double total;

		Load(List<Double> perSide, double total) {
			this.perSide = Collections.unmodifiableList(perSide);
			this.total = total;
		}
	}

	public static Load load(double target, double bar, double[] plates) {
		List<Double> side = new ArrayList<>();
		double left = (target - bar) / 2;
		for (double p : plates)
			while (left >= p - 1e-9 && side.size() < 40) {
				side.add(p);
				left -= p;
			}
		double sum = 0;
		for (double p : side)
			sum += p;
		return new Load(side, target < bar ? bar : bar + 2 * sum);
	}

	/** Valor en la unidad elegida; las libras se redondean a media libra para que 135 lb siga siendo 135. */
	public static double toUnit(double kg, boolean lb) {
		return lb ? Math.round(kg * LB_PER_KG * 2) / 2.0 : kg;
	}

	/** De lo escrito en la unidad elegida a kg con dos decimales (el formato que se guarda). */
	public static double toKg(double value, boolean lb) {
		return lb ? Math.round(value / LB_PER_KG * 100) / 100.0 : value;
	}

	public static String unit(boolean lb) {
		return lb ? "lb" : "kg";
	}

	/** «60 kg», «135 lb» o «Peso corporal». */
	public static String text(double kg, boolean lb) {
		return kg > 0 ? SetLog.format(toUnit(kg, lb)) + " " + unit(lb) : "Peso corporal";
	}

	/** Repeticiones aproximadas a un porcentaje del 1RM (inversa de Epley, la misma fórmula del 1RM estimado). */
	public static int repsAt(int percent) {
		return percent >= 100 ? 1 : (int) Math.round(30 * (100.0 / percent - 1));
	}

	/** Peso a un porcentaje del 1RM redondeado al salto de carga más pequeño de la unidad. */
	public static double atPercent(double oneRepMaxKg, int percent, boolean lb) {
		double step = lb ? 5 : 2.5;
		return Math.round(toUnit(oneRepMaxKg, lb) * percent / 100.0 / step) * step;
	}
}
