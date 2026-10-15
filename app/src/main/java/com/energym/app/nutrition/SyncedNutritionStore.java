package com.energym.app.nutrition;

import android.content.Context;

import com.energym.app.controller.SyncController;
import com.energym.app.data.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador Android del puerto de nutrición sobre la base de la cuenta: comparte cola de salida y respaldo
 * Firebase con perfil, rutinas y sesiones. Cada mutación confirmada solicita sincronización.
 */
public final class SyncedNutritionStore implements NutritionStore {
	private final Context context;
	private final SyncController sync;

	public SyncedNutritionStore(Context context, SyncController sync) {
		this.context = context.getApplicationContext();
		this.sync = sync;
	}

	private interface Work<T> {
		T run(NutritionRepository repository);
	}

	private <T> void run(String owner, boolean mutation, Work<T> work, Callback<T> callback) {
		sync.withDatabase(db -> {
			if (!db.getOwnerUid().equals(owner))
				throw new IllegalStateException("La cuenta activa cambió");
			return work.run(new NutritionRepository(db));
		}, new SyncController.DataCallback<T>() {
			public void onSuccess(T value) {
				callback.success(value);
				if (mutation)
					sync.requestSync();
			}

			public void onFailure(Exception error) {
				callback.failure(error);
			}
		});
	}

	public void load(String owner, LocalDate from, Callback<Snapshot> callback) {
		boolean[] migrated = {false};
		run(owner, false, repo -> {
			migrated[0] = migrate(owner, repo);
			List<FoodEntry> entries = new ArrayList<>();
			SortedMap<LocalDate, Double> dailyCalories = new TreeMap<>();
			for (FoodEntry e : repo.food(LocalDate.MIN)) {
				dailyCalories.merge(e.day, e.kcal(), Double::sum);
				if (!e.day.isBefore(from))
					entries.add(e);
			}
			return new Snapshot(repo.body(), entries, repo.weights(), dailyCalories);
		}, new Callback<Snapshot>() {
			public void success(Snapshot value) {
				callback.success(value);
				if (migrated[0])
					sync.requestSync();
			}

			public void failure(Exception error) {
				callback.failure(error);
			}
		});
	}

	/**
	 * Copia una sola vez el diario de la versión anterior (solo local) y lo retira de allí.
	 * Un perfil físico ya existente en la cuenta tiene prioridad sobre el antiguo.
	 */
	private boolean migrate(String owner, NutritionRepository repo) {
		LegacyNutritionDatabase.Data old = LegacyNutritionDatabase.read(context, owner);
		if (old.empty())
			return false;
		if (old.body != null && repo.body() == null)
			repo.saveBody(old.body);
		Set<String> known = new HashSet<>();
		for (FoodEntry e : repo.food(LocalDate.MIN))
			known.add(e.id);
		for (FoodEntry e : old.food)
			if (known.add(e.id))
				try {
					repo.add(e);
				} catch (IllegalArgumentException ignored) {
					// Lápida o duplicado previo en la cuenta: no se resucita.
				}
		SortedMap<LocalDate, Double> current = repo.weights();
		for (Map.Entry<LocalDate, Double> w : old.weights.entrySet())
			if (!current.containsKey(w.getKey()))
				repo.weight(w.getKey(), w.getValue());
		LegacyNutritionDatabase.clear(context, owner);
		return true;
	}

	public void saveBody(String owner, BodyProfile body, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.saveBody(body);
			return null;
		}, callback);
	}

	public void add(String owner, FoodEntry entry, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.add(entry);
			return null;
		}, callback);
	}

	public void update(String owner, FoodEntry entry, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.update(entry);
			return null;
		}, callback);
	}

	public void remove(String owner, String entryId, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.remove(entryId);
			return null;
		}, callback);
	}

	public void weight(String owner, LocalDate day, double kg, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.weight(day, kg);
			return null;
		}, callback);
	}

	/**
	 * Borrado local de la cuenta: los registros se eliminan con la base; aquí solo queda el archivo antiguo.
	 */
	public void clearLegacy(String owner) {
		LegacyNutritionDatabase.clear(context, owner);
	}
}
