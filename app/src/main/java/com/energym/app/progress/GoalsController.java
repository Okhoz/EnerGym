package com.energym.app.progress;

import java.util.*;
import java.util.function.Consumer;

/**
 * CONTROLADOR de objetivos: lista por cuenta, borrador del formulario y altas/bajas. Sin Android.
 */
public final class GoalsController {
	public interface Store {
		interface Callback<T> {
			void success(T value);

			void failure(Exception error);
		}

		void load(String owner, Callback<List<Goal>> callback);

		void save(String owner, Goal goal, Callback<Void> callback);

		void remove(String owner, String goalId, Callback<Void> callback);
	}

	public final Map<String, String> draft = new LinkedHashMap<>();
	public List<Goal> goals = Collections.emptyList();
	public boolean busy, editing;
	private final Store store;
	private final Runnable changed;
	private final Consumer<String> error;
	private String owner;
	private long generation;

	public GoalsController(Store store, Runnable changed, Consumer<String> error) {
		this.store = store;
		this.changed = changed;
		this.error = error;
	}

	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		owner = next;
		generation++;
		goals = Collections.emptyList();
		busy = false;
		editing = false;
		draft.clear();
		reload();
	}

	public void reload() {
		if (owner == null) {
			changed.run();
			return;
		}
		long token = generation;
		store.load(owner, new Store.Callback<List<Goal>>() {
			public void success(List<Goal> value) {
				if (token != generation)
					return;
				goals = value;
				changed.run();
			}

			public void failure(Exception e) {
				if (token == generation)
					error.accept("No se pudieron leer tus objetivos: " + e.getMessage());
			}
		});
	}

	public void startNew() {
		draft.clear();
		draft.put("type", Goal.Type.SESSIONS_PER_WEEK.name());
		editing = true;
		changed.run();
	}

	public void cancel() {
		editing = false;
		draft.clear();
		changed.run();
	}

	/**
	 * @param currentWeight peso más reciente (0 si no hay) para medir el avance del objetivo de peso.
	 * @param exerciseName  nombre visible del ejercicio elegido, o null.
	 */
	public void save(double currentWeight, String exerciseName) {
		if (owner == null || busy)
			return;
		Goal goal;
		try {
			Goal.Type type = Goal.Type.valueOf(draft.getOrDefault("type", ""));
			String raw = draft.getOrDefault("target", "").trim().replace(',', '.');
			if (!raw.matches("\\d+(?:\\.\\d+)?"))
				throw new IllegalArgumentException("Indica una cifra para el objetivo.");
			goal = Goal.create(type, Double.parseDouble(raw), type == Goal.Type.TARGET_WEIGHT ? currentWeight : 0,
				draft.get("exerciseId"), exerciseName);
		} catch (RuntimeException e) {
			error.accept(e.getMessage() == null ? "Revisa el objetivo." : e.getMessage());
			return;
		}
		busy = true;
		long token = generation;
		store.save(owner, goal, new Store.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				busy = false;
				editing = false;
				draft.clear();
				List<Goal> next = new ArrayList<>(goals);
				next.add(goal);
				goals = next;
				changed.run();
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		});
	}

	public void remove(Goal goal) {
		if (owner == null || busy)
			return;
		busy = true;
		long token = generation;
		store.remove(owner, goal.id, new Store.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				busy = false;
				List<Goal> next = new ArrayList<>(goals);
				next.removeIf(g -> g.id.equals(goal.id));
				goals = next;
				changed.run();
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		});
	}

	private void fail(long token, Exception e) {
		if (token != generation)
			return;
		busy = false;
		error.accept("No se pudo guardar el objetivo: " + e.getMessage());
	}
}
