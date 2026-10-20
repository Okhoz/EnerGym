package com.energym.app.progress;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
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

	private static final String NUMBER = "\\d+(?:\\.\\d+)?";
	public final Map<String, String> draft = new LinkedHashMap<>();
	public List<Goal> goals = Collections.emptyList();
	public boolean busy, editing;
	/** Meta personal cuyo formulario de «Apuntar avance» está abierto, o null. */
	public String logging;
	public String progressDraft = "";
	/** Día de hoy; los tests lo fijan. */
	public java.util.function.Supplier<java.time.LocalDate> today = java.time.LocalDate::now;
	public java.util.function.LongSupplier clock = System::currentTimeMillis;
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
		logging = null;
		progressDraft = "";
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
					error.accept(Txt.t(R.string.goal_read_failed, e.getMessage()));
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
			if (!raw.matches(NUMBER))
				throw new IllegalArgumentException(Txt.t(R.string.goal_need_number));
			if (type == Goal.Type.CUSTOM) {
				String from = draft.getOrDefault("start", "").trim().replace(',', '.');
				if (!from.isEmpty() && !from.matches(NUMBER))
					throw new IllegalArgumentException(Txt.t(R.string.goal_need_number));
				goal = Goal.custom(draft.getOrDefault("name", ""), draft.getOrDefault("unit", ""),
					Double.parseDouble(raw), from.isEmpty() ? 0 : Double.parseDouble(from));
			} else
				goal = Goal.create(type, Double.parseDouble(raw), type == Goal.Type.TARGET_WEIGHT ? currentWeight : 0,
					draft.get("exerciseId"), exerciseName);
		} catch (RuntimeException e) {
			error.accept(e.getMessage() == null ? Txt.t(R.string.goal_check) : e.getMessage());
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

	public List<Goal> active() {
		List<Goal> result = new ArrayList<>();
		for (Goal g : goals)
			if (!g.completed())
				result.add(g);
		return result;
	}

	/** Cumplidas, la más reciente primero. */
	public List<Goal> completed() {
		List<Goal> result = new ArrayList<>();
		for (Goal g : goals)
			if (g.completed())
				result.add(g);
		result.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
		return result;
	}

	public void complete(Goal goal) {
		replace(goal.complete(clock.getAsLong()));
	}

	public void reopen(Goal goal) {
		replace(goal.reopen());
	}

	public void startLogging(Goal goal) {
		logging = goal.id;
		Double last = goal.entries.isEmpty() ? null : goal.entries.get(goal.entries.lastKey());
		progressDraft = last == null ? "" : Goal.fmt(last).replace(',', '.');
		changed.run();
	}

	public void cancelLogging() {
		logging = null;
		progressDraft = "";
		changed.run();
	}

	/** Apunta el valor de hoy de una meta personal; si ya había uno hoy, lo sustituye. */
	public void logProgress(Goal goal) {
		String raw = progressDraft.trim().replace(',', '.');
		if (!raw.matches(NUMBER)) {
			error.accept(Txt.t(R.string.goal_need_progress));
			return;
		}
		Goal next;
		try {
			next = goal.withEntry(today.get(), Double.parseDouble(raw));
		} catch (RuntimeException e) {
			error.accept(e.getMessage() == null ? Txt.t(R.string.goal_check) : e.getMessage());
			return;
		}
		replace(next);
	}

	public void removeEntry(Goal goal, java.time.LocalDate day) {
		replace(goal.withoutEntry(day));
	}

	/** Guarda la nueva versión de una meta que ya existe (misma identidad) y la sustituye en la lista. */
	private void replace(Goal goal) {
		if (owner == null || busy)
			return;
		busy = true;
		long token = generation;
		store.save(owner, goal, new Store.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				busy = false;
				if (goal.id.equals(logging)) {
					logging = null;
					progressDraft = "";
				}
				List<Goal> next = new ArrayList<>(goals);
				next.replaceAll(g -> g.id.equals(goal.id) ? goal : g);
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
		error.accept(Txt.t(R.string.goal_save_failed, e.getMessage()));
	}
}
