package com.energym.app.controller;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
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
			error.accept(Txt.t(R.string.sc_draft_open));
			return;
		}
		if (routine == null || routine.getVersionId() <= 0) {
			error.accept(Txt.t(R.string.sc_accept_first));
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
			error.accept(Txt.t(R.string.sc_no_draft));
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
					error.accept(Txt.t(R.string.sc_draft_gone));
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
				// Al retomar otro borrador se abre el primer ejercicio aún sin registrar.
				if (previous == null)
					selected = firstPending();
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
		save(next, this::advance);
	}

	/**
	 * Entreno libre: pasa la sesión abierta a una versión nueva de su rutina (con un ejercicio más), conservando lo
	 * apuntado y el inicio, y abre el ejercicio añadido. {@code replaced} recibe el borrador anterior para archivarlo.
	 */
	public void migrate(Routine routine, Consumer<Session> replaced) {
		if (busy || session == null || session.getStatus() != Session.Status.DRAFT || routine == null ||
			routine.getVersionId() <= 0)
			return;
		Session old = session;
		Session next = new Session(UUID.randomUUID().toString(), routine, old.getResults(), old.getStartedAt(), 0,
			Session.Status.DRAFT);
		save(next, () -> {
			selected = routine.getItems().size() - 1;
			changed.run();
			replaced.accept(old);
		});
	}

	/** Series ya apuntadas del ejercicio actual (vacío si no es de fuerza o aún no hay). */
	public List<SetLog.Entry> sets() {
		RoutineItem item = item();
		if (item == null || item.getExercise().getMetricType() != MetricType.STRENGTH)
			return Collections.emptyList();
		return SetLog.of(session.getResults().get(item.getExercise().getId()));
	}

	/** Apunta una serie (peso y repeticiones) y la guarda al momento, como en FitNotes. */
	public void addSet(double kg, int reps) {
		addSet(kg, reps, SetLog.Kind.NORMAL, 0);
	}

	/** Serie con su tipo (calentamiento, drop set, al fallo) y esfuerzo percibido (RPE 1–10, 0 sin indicar). */
	public void addSet(double kg, int reps, SetLog.Kind kind, double rpe) {
		addSet(kg, reps, kind, rpe, () -> {
		});
	}

	/** Igual, y {@code then} se ejecuta cuando la serie ya está guardada (p. ej. pasar al siguiente de la superserie). */
	public void addSet(double kg, int reps, SetLog.Kind kind, double rpe, Runnable then) {
		changeSets(list -> list.add(new SetLog.Entry(kg, reps, kind, rpe)), kg, reps, then);
	}

	/** Varias series de golpe (p. ej. las de calentamiento) en un solo guardado. */
	public void addSets(List<SetLog.Entry> entries) {
		if (entries == null || entries.isEmpty())
			return;
		SetLog.Entry last = entries.get(entries.size() - 1);
		changeSets(list -> list.addAll(entries), last.kg, last.reps, () -> {
		});
	}

	public void updateSet(int index, double kg, int reps) {
		changeSets(list -> {
			if (index >= 0 && index < list.size())
				list.set(index, new SetLog.Entry(kg, reps, list.get(index).kind, list.get(index).rpe));
		}, kg, reps, () -> {
		});
	}

	public void updateSet(int index, double kg, int reps, SetLog.Kind kind, double rpe) {
		changeSets(list -> {
			if (index >= 0 && index < list.size())
				list.set(index, new SetLog.Entry(kg, reps, kind, rpe));
		}, kg, reps, () -> {
		});
	}

	/** Nota del ejercicio actual en este entreno; se guarda al momento si ya tiene valores. */
	public String note() {
		RoutineItem item = item();
		if (item == null)
			return "";
		Map<String, String> saved = session.getResults().get(item.getExercise().getId());
		String typed = edits.containsKey(item.getExercise().getId()) ?
			edits.get(item.getExercise().getId()).get(SetLog.NOTE) : null;
		return typed != null ? typed : saved == null ? "" : saved.getOrDefault(SetLog.NOTE, "");
	}

	public void saveNote(String text) {
		RoutineItem item = item();
		if (busy || item == null || session.getStatus() != Session.Status.DRAFT)
			return;
		input(SetLog.NOTE, text == null ? "" : text);
		String id = item.getExercise().getId();
		Map<String, String> saved = session.getResults().get(id);
		if (saved == null)
			return;
		Map<String, String> next = new LinkedHashMap<>(saved);
		String clean = validator.normalized(item.getExercise().getMetricType(), Collections.singletonMap(SetLog.NOTE,
			text == null ? "" : text)).get(SetLog.NOTE);
		if (clean == null)
			next.remove(SetLog.NOTE);
		else
			next.put(SetLog.NOTE, clean);
		edits.remove(id);
		save(session.withResult(id, next), () -> {
		});
	}

	public void removeSet(int index) {
		changeSets(list -> {
			if (index >= 0 && index < list.size())
				list.remove(index);
		}, 0, 1, () -> {
		});
	}

	private void changeSets(Consumer<List<SetLog.Entry>> change, double kg, int reps, Runnable then) {
		RoutineItem item = item();
		if (busy || item == null || session.getStatus() != Session.Status.DRAFT ||
			item.getExercise().getMetricType() != MetricType.STRENGTH)
			return;
		if (kg < 0 || kg > 9999 || reps < 1 || reps > 999) {
			error.accept(Txt.t(R.string.sc_check_set));
			return;
		}
		List<SetLog.Entry> list = new ArrayList<>(sets());
		change.accept(list);
		if (list.size() > SetLog.MAX_SETS) {
			error.accept("Máximo " + SetLog.MAX_SETS + " series por ejercicio.");
			return;
		}
		String id = item.getExercise().getId();
		String rest = item.getPrescription().getOrDefault("restSeconds", "60");
		Map<String, String> base = new LinkedHashMap<>(session.getResults().getOrDefault(id, Collections.emptyMap()));
		String typedNote = edits.containsKey(id) ? edits.get(id).get(SetLog.NOTE) : null;
		if (typedNote != null)
			base.put(SetLog.NOTE, typedNote.replaceAll("\\s+", " ").trim());
		Map<String, String> values = SetLog.summary(list, base, rest);
		Session next = values == null ? session.withoutResult(id) : session.withResult(id, values);
		// Lo editable refleja lo guardado: así «terminar» no lo ve como cambios sin guardar.
		edits.remove(id);
		save(next, then);
	}

	/** Primer ejercicio sin valores guardados; el primero si ya están todos. */
	private int firstPending() {
		List<RoutineItem> items = session.getRoutine().getItems();
		for (int i = 0; i < items.size(); i++)
			if (!session.getResults().containsKey(items.get(i).getExercise().getId()))
				return i;
		return 0;
	}

	/** Tras guardar, pasa al siguiente ejercicio aún sin registrar (si queda alguno) para no tener que buscarlo. */
	private void advance() {
		List<RoutineItem> items = session.getRoutine().getItems();
		for (int step = 1; step < items.size(); step++) {
			int index = (selected + step) % items.size();
			if (!session.getResults().containsKey(items.get(index).getExercise().getId())) {
				selected = index;
				changed.run();
				return;
			}
		}
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
				error.accept(Txt.t(R.string.sc_log_all));
				return;
			}
		for (RoutineItem item : session.getRoutine().getItems()) {
			Map<String, String> edited = edits.get(item.getExercise().getId());
			if (edited != null && !validator.normalized(item.getExercise().getMetricType(), edited)
				.equals(session.getResults().get(item.getExercise().getId()))) {
				error.accept(Txt.t(R.string.sc_unsaved));
				return;
			}
		}
		if (session.getResults().isEmpty() || id <= 0) {
			error.accept(Txt.t(R.string.sc_empty));
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
