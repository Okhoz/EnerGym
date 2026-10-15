package com.energym.app.progress;

import com.energym.app.controller.SyncController;
import com.energym.app.data.GoalRepository;

import java.util.List;

/**
 * Adaptador Android de objetivos sobre la base de la cuenta; cada cambio confirmado solicita sincronización.
 */
public final class SyncedGoalStore implements GoalsController.Store {
	private final SyncController sync;

	public SyncedGoalStore(SyncController sync) {
		this.sync = sync;
	}

	private interface Work<T> {
		T run(GoalRepository repository);
	}

	private <T> void run(String owner, boolean mutation, Work<T> work, Callback<T> callback) {
		sync.withDatabase(db -> {
			if (!db.getOwnerUid().equals(owner))
				throw new IllegalStateException("La cuenta activa cambió");
			return work.run(new GoalRepository(db));
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

	public void load(String owner, Callback<List<Goal>> callback) {
		run(owner, false, GoalRepository::goals, callback);
	}

	public void save(String owner, Goal goal, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.save(goal);
			return null;
		}, callback);
	}

	public void remove(String owner, String goalId, Callback<Void> callback) {
		run(owner, true, repo -> {
			repo.remove(goalId);
			return null;
		}, callback);
	}
}
