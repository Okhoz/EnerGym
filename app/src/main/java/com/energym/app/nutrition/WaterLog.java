package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.Objects;

/**
 * MODEL+CONTROLADOR del agua del día: vasos de 250 ml y un objetivo orientativo de 35 ml por kilo de peso
 * (redondeado a vasos). Se guarda en el propio móvil por cuenta y día.
 */
public final class WaterLog {
	public interface Store {
		int load(String key);

		void save(String key, int ml);
	}

	public static final int GLASS = 250, MAX = 8000;
	private final Store store;
	private final Runnable changed;
	private String owner;

	public WaterLog(Store store, Runnable changed) {
		this.store = store;
		this.changed = changed;
	}

	public void activate(String owner) {
		this.owner = owner;
	}

	public int ml(LocalDate day) {
		return owner == null ? 0 : store.load(key(day));
	}

	/** Suma (o resta, con valor negativo) agua al día; nunca baja de 0 ni pasa de 8 litros. */
	public void add(LocalDate day, int ml) {
		if (owner == null)
			return;
		store.save(key(day), Math.max(0, Math.min(MAX, ml(day) + ml)));
		changed.run();
	}

	/** Objetivo diario en ml: 35 ml/kg redondeado a vasos; 2 litros si no hay peso. */
	public static int goal(BodyProfile body) {
		double ml = body == null ? 2000 : body.weightKg * 35;
		return (int) Math.max(1500, Math.min(4500, Math.round(ml / GLASS) * GLASS));
	}

	public static String liters(int ml) {
		return NutritionController.format(Math.round(ml / 100.0) / 10.0) + " L";
	}

	private String key(LocalDate day) {
		return Objects.requireNonNull(owner) + "|" + day;
	}
}
