package com.energym.app.controller;

import com.energym.app.domain.*;

import java.util.*;

/**
 * Puerto MVC asíncrono: el adaptador Android ejecuta SQLite fuera de la vista.
 */
public interface AppStore {
	interface Callback<T> {
		void success(T value);

		void failure(Exception error);
	}

	final class Snapshot {
		public final TrainingProfile profile;
		public final List<Routine> routines;
		public final List<Session> pending, history;
		public final boolean initialDataReady;

		public Snapshot(TrainingProfile p, List<Routine> r, List<Session> d, List<Session> h) {
			this(p, r, d, h, true);
		}

		public Snapshot(TrainingProfile p, List<Routine> r, List<Session> d, List<Session> h, boolean initialDataReady) {
			profile = p;
			routines = r;
			pending = d;
			history = h;
			this.initialDataReady = initialDataReady;
		}
	}

	/**
	 * Identidad local y snapshot leídos del scope actual; nunca se obtienen escribiendo un borrador cacheado.
	 */
	final class OpenDraft {
		public final long id;
		public final Session session;

		public OpenDraft(long id, Session session) {
			this.id = id;
			this.session = session;
		}
	}

	void load(Callback<Snapshot> callback);

	void profile(TrainingProfile value, Callback<Void> callback);

	void accept(Routine value, Callback<Routine> callback);

	/** No delegar una aceptación manual al puerto que puede depender del perfil general. */
	default void acceptManual(Routine value, Callback<Routine> callback) {
		callback.failure(new UnsupportedOperationException("Este almacén no admite rutinas manuales"));
	}

	/** Edición explícita: nueva versión y archivo de la anterior en una transacción por cuenta. */
	default void replaceManual(String originalId, Routine value, Callback<Routine> callback) {
		callback.failure(new UnsupportedOperationException("Este almacén no admite editar rutinas"));
	}

	/** Sustituye una rutina por su siguiente nivel de progresión, archivando la anterior. */
	default void progress(String originalId, Routine value, Callback<Routine> callback) {
		callback.failure(new UnsupportedOperationException("Este almacén no admite progresión automática"));
	}

	/** Lápida sincronizable, sin destruir los snapshots históricos. */
	default void archiveTraining(boolean routine, String syncId, Callback<Void> callback) {
		callback.failure(new UnsupportedOperationException("Este almacén no admite eliminar entrenamientos"));
	}

	/**
	 * Aceptación explícita de perfil propuesto y rutina juntos, comparando el perfil vigente.
	 */
	default void acceptProposal(Routine value, TrainingProfile expected, Callback<Routine> callback) {
		if (!Objects.equals(value.getProfile(), expected)) {
			callback.failure(new IllegalArgumentException("Almacén sin aceptación conjunta"));
			return;
		}
		accept(value, callback);
	}

	void draft(Session value, Callback<Long> callback);

	void openDraft(String syncId, Callback<OpenDraft> callback);

	void finish(long id, Callback<Void> callback);
}
