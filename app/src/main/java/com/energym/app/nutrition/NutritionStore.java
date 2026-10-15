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

		public Snapshot(BodyProfile body, List<FoodEntry> entries, SortedMap<LocalDate, Double> weights) {
			this.body = body;
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
			this.weights = Collections.unmodifiableSortedMap(new TreeMap<>(weights));
		}
	}

	/**
	 * Lee el perfil físico, los alimentos desde {@code from} y el registro de peso completo.
	 */
	void load(String owner, LocalDate from, Callback<Snapshot> callback);

	void saveBody(String owner, BodyProfile body, Callback<Void> callback);

	void add(String owner, FoodEntry entry, Callback<Void> callback);

	void remove(String owner, String entryId, Callback<Void> callback);

	void weight(String owner, LocalDate day, double kg, Callback<Void> callback);

	interface Lookup {
		/**
		 * Entrega null si el código no existe en la base abierta.
		 */
		void find(String barcode, Callback<FoodProduct> callback);
	}
}
