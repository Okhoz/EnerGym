package com.energym.app.controller;

import com.energym.app.domain.*;

import java.util.*;
import java.util.function.*;

/**
 * CONTROLADOR: valida resultados y serializa guardado/finalización de una sola sesión.
 */
public final class SessionController {
	public Session session;
	public boolean busy;
	public int selected;
	private final AppStore store;
	private final Runnable changed, finished;
	private final Consumer<String> error;
	private final MetricValidator validator = new MetricValidator();
	private final Map<String, Map<String, String>> edits = new LinkedHashMap<>();
	private long generation, id;

	public SessionController(AppStore store, Runnable changed, Consumer<String> error, Runnable finished) {
		this.store = store;
		this.changed = changed;
		this.error = error;
		this.finished = finished;
	}

	/**
	 * El snapshot aceptado se conserva: cambiar el perfil después no reescribe la sesión.
	 */
	public void start(Routine routine) {
		if (busy)
			return;
		if (session != null && session.getStatus() == Session.Status.DRAFT) {
			error.accept("Ya hay un borrador abierto. Continúa esa sesión.");
			return;
		}
		if (routine == null || routine.getVersionId() <= 0) {
			error.accept("Acepta una rutina antes de empezar.");
			return;
		}
		reset();
		session = new Session(routine);
		save(session, () -> {
		});
	}

	/**
	 * Abrir es solo lectura por UUID dentro del scope actual. El snapshot de la lista puede ser antiguo.
	 * Si es la sesión abierta, conserva selección/texto no guardado y refresca únicamente los datos confirmados.
	 */
	public void resume(Session draft) {
		if (busy)
			return;
		if (draft == null) {
			error.accept("No hay un borrador seleccionado.");
			return;
		}
		Session previous = session != null && session.getStatus() == Session.Status.DRAFT &&
			session.getSyncId().equals(draft.getSyncId()) ? session : null;
		if (previous == null)
			reset();
		long token = generation;
		busy = true;
		changed.run();
		store.openDraft(draft.getSyncId(), new AppStore.Callback<AppStore.OpenDraft>() {
			public void success(AppStore.OpenDraft current) {
				if (token != generation)
					return;
				busy = false;
				if (current == null || current.id <= 0 || current.session == null ||
					!draft.getSyncId().equals(current.session.getSyncId()) ||
					current.session.getStatus() != Session.Status.DRAFT) {
					session = null;
					id = 0;
					selected = 0;
					edits.clear();
					error.accept("No hay un borrador vigente de esta cuenta. Puede haberse finalizado o eliminado.");
					changed.run();
					return;
				}
				if (previous != null) {
					// Las filas renderizadas también guardan una copia de valores originales. Solo conservar
					// texto que difiera de esos valores; el resto debe reflejar la nueva lectura de SQLite.
					Iterator<Map.Entry<String, Map<String, String>>> iterator = edits.entrySet().iterator();
					while (iterator.hasNext()) {
						Map.Entry<String, Map<String, String>> edit = iterator.next();
						for (RoutineItem item : previous.getRoutine().getItems())
							if (item.getExercise().getId().equals(edit.getKey()) &&
								validator.normalized(item.getExercise().getMetricType(), edit.getValue()).equals(
									validator.normalized(item.getExercise().getMetricType(),
										previous.getResults().getOrDefault(edit.getKey(), Collections.emptyMap())))) {
								iterator.remove();
								break;
							}
					}
				}
				session = current.session;
				id = current.id;
				changed.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				error.accept(e.getMessage());
				changed.run();
			}
		});
	}

	public void reset() {
		generation++;
		session = null;
		busy = false;
		selected = 0;
		id = 0;
		edits.clear();
	}

	public RoutineItem item() {
		return session == null ? null : session.getRoutine().getItems().get(selected);
	}

	/**
	 * Conserva texto aún no validado durante rotación/navegación; solo record escribe valores realizados.
	 */
	public void input(String key, String value) {
		if (item() != null)
			editable().put(key, value);
	}

	private Map<String, String> editable() {
		String key = item().getExercise().getId();
		return edits.computeIfAbsent(key, k -> {
			Map<String, String> saved = session.getResults().get(k);
			Map<String, String> values = new LinkedHashMap<>(saved == null ? Collections.emptyMap() : saved);
			for (MetricValidator.Field field : validator.fields(item().getExercise().getMetricType())) {
				// Sin valores guardados, se parte de lo previsto: solo hay que cambiar lo que fue distinto.
				if (saved == null && item().getPrescription().containsKey(field.key))
					values.putIfAbsent(field.key, item().getPrescription().get(field.key));
				if (!field.choices.isEmpty())
					values.putIfAbsent(field.key, field.choices.get(0));
			}
			return values;
		});
	}

	public Map<String, String> inputs() {
		return item() == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(editable()));
	}

	public void select(int index) {
		if (session != null && !busy && index >= 0 && index < session.getRoutine().getItems().size()) {
			selected = index;
			changed.run();
		}
	}

	public void record() {
		if (busy || item() == null || session.getStatus() != Session.Status.DRAFT)
			return;
		List<String> errors = validator.validate(item().getExercise().getMetricType(), inputs());
		if (!errors.isEmpty()) {
			error.accept(String.join("\n", errors));
			return;
		}
		Session next = session.withResult(item().getExercise().getId(),
			validator.normalized(item().getExercise().getMetricType(), inputs()));
		save(next, () -> {
		});
	}

	private void save(Session next, Runnable after) {
		busy = true;
		long token = generation;
		changed.run();
		store.draft(next, new AppStore.Callback<Long>() {
			public void success(Long value) {
				if (token != generation)
					return;
				id = value;
				session = next;
				busy = false;
				changed.run();
				after.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				error.accept(e.getMessage());
				changed.run();
			}
		});
	}

	/**
	 * La finalización requiere registros válidos para cada ejercicio; bloquea doble pulsación y callbacks antiguos.
	 */
	public void finish() {
		if (busy || session == null || session.getStatus() == Session.Status.FINISHED)
			return;
		for (RoutineItem item : session.getRoutine().getItems())
			if (!validator.validate(item.getExercise().getMetricType(),
				session.getResults().get(item.getExercise().getId())).isEmpty()) {
				error.accept("Registra los valores realizados de cada ejercicio antes de finalizar.");
				return;
			}
		for (RoutineItem item : session.getRoutine().getItems()) {
			Map<String, String> edited = edits.get(item.getExercise().getId());
			if (edited != null && !validator.normalized(item.getExercise().getMetricType(), edited)
				.equals(session.getResults().get(item.getExercise().getId()))) {
				error.accept("Hay valores escritos sin guardar. Guarda ese ejercicio antes de finalizar.");
				return;
			}
		}
		if (session.getResults().isEmpty() || id <= 0) {
			error.accept("La sesión vacía no puede finalizarse.");
			return;
		}
		busy = true;
		long token = generation;
		changed.run();
		store.finish(id, new AppStore.Callback<Void>() {
			public void success(Void ignored) {
				if (token != generation)
					return;
				busy = false;
				session = session.finishedAt(System.currentTimeMillis());
				changed.run();
				finished.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				error.accept(e.getMessage());
				changed.run();
			}
		});
	}
}
