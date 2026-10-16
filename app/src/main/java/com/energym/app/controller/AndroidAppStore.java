package com.energym.app.controller;

import com.energym.app.data.*;
import com.energym.app.domain.*;

/**
 * Adaptador del puerto MVC: todos los repositorios se crean dentro del scope UID protegido.
 */
public final class AndroidAppStore implements AppStore {
	private final SyncController sync;

	public AndroidAppStore(SyncController sync) {
		this.sync = sync;
	}

	private <T> SyncController.DataCallback<T> callback(Callback<T> c, boolean mutation) {
		return new SyncController.DataCallback<T>() {
			public void onSuccess(T value) {
				c.success(value);
				if (mutation)
					sync.requestSync();
			}

			public void onFailure(Exception error) {
				c.failure(error);
			}
		};
	}

	public void load(Callback<Snapshot> c) {
		sync.withDatabase(db -> new Snapshot(db.loadProfile(), new RoutineRepository(db).versions(),
			new SessionRepository(db).pending(), new SessionRepository(db).history(), !db.initialRestorePending()),
			callback(c, false));
	}

	public void profile(TrainingProfile p, Callback<Void> c) {
		sync.withDatabase(db -> {
			db.saveProfile(p);
			return null;
		}, callback(c, true));
	}

	public void accept(Routine r, Callback<Routine> c) {
		sync.withDatabase(db -> {
			RoutineRepository repo = new RoutineRepository(db);
			return repo.load(repo.saveAccepted(r));
		}, callback(c, true));
	}

	public void acceptProposal(Routine r, TrainingProfile expected, Callback<Routine> c) {
		sync.withDatabase(db -> new RoutineRepository(db).acceptProposal(r, expected), callback(c, true));
	}

	public void acceptManual(Routine r, Callback<Routine> c) {
		sync.withDatabase(db -> {
			RoutineRepository repo = new RoutineRepository(db);
			return repo.load(repo.saveManual(r));
		}, callback(c, true));
	}

	public void replaceManual(String originalId, Routine r, Callback<Routine> c) {
		sync.withDatabase(db -> new RoutineRepository(db).replaceManual(originalId, r), callback(c, true));
	}

	public void progress(String originalId, Routine r, Callback<Routine> c) {
		sync.withDatabase(db -> new RoutineRepository(db).replaceWithProgression(originalId, r), callback(c, true));
	}

	public void archiveTraining(boolean routine, String syncId, Callback<Void> c) {
		sync.withDatabase(db -> {
			new TrainingArchiveRepository(db).archive(routine ? SyncRecord.Kind.ROUTINE : SyncRecord.Kind.SESSION, syncId);
			return null;
		}, callback(c, true));
	}

	public void draft(Session s, Callback<Long> c) {
		sync.withDatabase(db -> new SessionRepository(db).saveDraft(s), callback(c, true));
	}

	/**
	 * Dos lecturas serializadas en el worker del scope; jamás convierte abrir en una mutación/sync pendiente.
	 */
	public void openDraft(String syncId, Callback<OpenDraft> c) {
		sync.withDatabase(db -> {
			SessionRepository repo = new SessionRepository(db);
			long id = repo.findIdBySyncId(syncId);
			Session current = id == 0 ? null : repo.load(id);
			return current == null ? null : new OpenDraft(id, current);
		}, callback(c, false));
	}

	public void finish(long id, Callback<Void> c) {
		sync.withDatabase(db -> {
			new SessionRepository(db).finish(id);
			return null;
		}, callback(c, true));
	}
}
