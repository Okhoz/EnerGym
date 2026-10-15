package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.*;

/**
 * Puerto MVC de nutrición. La implementación Android trabaja en un worker y entrega en main.
 */
public interface NutritionStore {
	interface Callback<T> {
		void success(T value);

		void failure(Exception error);
	}

	final class Snapshot {
		public final BodyProfile body;
		public final List<FoodEntry> entries;
		public final SortedMap<LocalDate, Double> weights;
		public final SortedMap<LocalDate, Double> dailyCalories;

		public Snapshot(BodyProfile body, List<FoodEntry> entries, SortedMap<LocalDate, Double> weights) {
			this(body, entries, weights, summarize(entries));
		}

		public Snapshot(BodyProfile body, List<FoodEntry> entries, SortedMap<LocalDate, Double> weights,
			SortedMap<LocalDate, Double> dailyCalories) {
			this.body = body;
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
			this.weights = Collections.unmodifiableSortedMap(new TreeMap<>(weights));
			this.dailyCalories = Collections.unmodifiableSortedMap(new TreeMap<>(dailyCalories));
		}

		private static SortedMap<LocalDate, Double> summarize(List<FoodEntry> entries) {
			SortedMap<LocalDate, Double> result = new TreeMap<>();
			for (FoodEntry e : entries)
				result.merge(e.day, e.kcal(), Double::sum);
			return result;
		}
	}

	/**
	 * Lee el perfil físico, los alimentos desde {@code from}, el peso y el resumen diario completos.
	 */
	void load(String owner, LocalDate from, Callback<Snapshot> callback);

	void saveBody(String owner, BodyProfile body, Callback<Void> callback);

	void add(String owner, FoodEntry entry, Callback<Void> callback);

	/** Mantiene el ID y la fecha de alta de una entrada existente. */
	default void update(String owner, FoodEntry entry, Callback<Void> callback) {
		callback.failure(new UnsupportedOperationException("El almacén no admite editar alimentos"));
	}

	void remove(String owner, String entryId, Callback<Void> callback);

	void weight(String owner, LocalDate day, double kg, Callback<Void> callback);

	interface Lookup {
		/**
		 * Entrega null si el código no existe en la base abierta.
		 */
		void find(String barcode, Callback<FoodProduct> callback);
	}
}
