package com.energym.app.controller;

import com.energym.app.assistant.ChatProtocol;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import java.util.function.Consumer;

/** Editor local MVC: contexto propio, prescripción estricta y guardado manual explícito por cuenta. */
public final class ManualRoutineController {
	public static final int MAX_EXERCISES = 20;

	/** Copia de sólo lectura: la vista nunca modifica los mapas internos del borrador. */
	public static final class Entry {
		public final Exercise exercise;
		public final Map<String, String> values;
		public final String minutes;

		private Entry(Draft draft) {
			exercise = draft.exercise;
			minutes = draft.minutes;
			values = Collections.unmodifiableMap(new LinkedHashMap<>(draft.values));
		}
	}

	private static final class Draft {
		final Exercise exercise;
		final Map<String, String> values = new LinkedHashMap<>();
		String minutes;

		Draft(Exercise exercise) { this.exercise = exercise; }
	}

	private final AppStore store;
	private final List<Exercise> catalog;
	private final Runnable changed;
	private final Consumer<Routine> onSaved;
	private final List<Draft> drafts = new ArrayList<>();
	private final Map<String, String> contextFields = new LinkedHashMap<>();
	private TrainingProfile base = new TrainingProfile.Builder().build();
	private String owner, title = "", proposalId = UUID.randomUUID().toString();
	private boolean progression = true;
	private Routine saved;
	private String editingId;
	private long generation;
	public boolean busy;
	public String error = "", query = "";
	public int selected;

	public ManualRoutineController(AppStore store, List<Exercise> catalog, Runnable changed, Consumer<Routine> onSaved) {
		this.store = Objects.requireNonNull(store);
		this.catalog = new ArrayList<>(catalog);
		this.changed = changed;
		this.onSaved = onSaved;
		clear(null);
	}

	public void activate(String next) {
		if (Objects.equals(owner, next)) return;
		owner = next;
		clear(null);
	}

	public void begin(TrainingProfile profile) {
		if (busy) return;
		if (owner == null) { fail("Selecciona una cuenta antes de crear una rutina."); return; }
		clear(profile);
		changed.run();
	}


	/** Copia completa y editable de una rutina aceptada; no modifica la versión histórica. */
	public void edit(Routine original) {
		if (busy || owner == null || original == null) return;
		clear(original.getProfile());
		editingId = original.getProposalId();
		title = original.getTitle();
		progression = original.isProgressionEnabled();
		for (RoutineItem item : original.getItems()) {
			Exercise canonical = ChatProtocol.find(catalog, item.getExercise().getId());
			if (canonical == null) {
				clear(original.getProfile());
				fail("Hay un ejercicio que ya no está en el catálogo. La versión guardada se conserva.");
				return;
			}
			Draft draft = new Draft(canonical);
			draft.values.putAll(item.getPrescription());
			draft.minutes = Integer.toString(item.getEstimatedMinutes());
			drafts.add(draft);
		}
		changed.run();
	}

	public boolean editing() { return editingId != null; }

	private void clear(TrainingProfile profile) {
		generation++;
		busy = false;
		drafts.clear();
		title = query = error = "";
		selected = 0;
		progression = true;
		saved = null;
		editingId = null;
		proposalId = UUID.randomUUID().toString();
		base = profile == null ? new TrainingProfile.Builder().build() : profile;
		contextFields.clear();
		contextFields.put("sportId", base.getSportId());
		contextFields.put("goal", base.getGoal().name());
		contextFields.put("environment", base.getEnvironment().name());
		contextFields.put("focus", base.getFocus().name());
		contextFields.put("level", base.getLevel());
		contextFields.put("durationMinutes", String.valueOf(base.getDurationMinutes()));
		for (Resource resource : Resource.values())
			contextFields.put("resource." + resource.name(), String.valueOf(base.getResources().contains(resource)));
	}

	private void touch() {
		error = "";
		if (saved != null) { proposalId = UUID.randomUUID().toString(); saved = null; }
	}

	private void fail(String message) {
		error = message == null ? "Revisa la rutina." : message;
		changed.run();
	}

	public String title() { return title; }
	public void title(String value) { if (!busy) { touch(); title = value == null ? "" : value; } }
	public boolean progression() { return progression; }
	public void progression(boolean enabled) { if (!busy) { touch(); progression = enabled; changed.run(); } }
	public String contextValue(String key) { return contextFields.getOrDefault(key, ""); }

	public void contextInput(String key, String value) {
		if (busy || !contextFields.containsKey(key)) return;
		touch();
		contextFields.put(key, value);
		// Los campos de texto conservan el foco mientras se escribe; el guardado valida el texto completo.
		if (!key.equals("durationMinutes") && !key.equals("level")) changed.run();
	}

	/** Construye un contexto inmutable de la rutina sin persistir ni reemplazar el perfil global. */
	public TrainingProfile context() {
		Set<Resource> resources = EnumSet.noneOf(Resource.class);
		for (Resource resource : Resource.values())
			if (Boolean.parseBoolean(contextValue("resource." + resource.name()))) resources.add(resource);
		Environment environment = Environment.valueOf(contextValue("environment"));
		Category category = environment == Environment.GYM ? Category.GYM : resources.isEmpty() ? Category.NO_EQUIPMENT :
			environment == Environment.HOME ? Category.HOME_EQUIPMENT : Category.SPORT;
		return base.toBuilder().sportId(contextValue("sportId")).goal(Goal.valueOf(contextValue("goal")))
			.environment(environment).focus(Focus.valueOf(contextValue("focus"))).category(category).resources(resources)
			.level(contextValue("level")).durationMinutes(Integer.parseInt(contextValue("durationMinutes")))
			.details(base.getSportId().equals(contextValue("sportId")) ? base.getDetails() : Collections.emptyMap()).build();
	}

	public List<Entry> entries() {
		List<Entry> result = new ArrayList<>();
		for (Draft draft : drafts) result.add(new Entry(draft));
		return Collections.unmodifiableList(result);
	}

	public List<Exercise> search(String text) {
		return search(text, ExerciseSearch.Filters.all(), true);
	}

	/** La exploración puede ampliar resultados sin alterar el contexto ni la elegibilidad al añadir. */
	public List<Exercise> search(String text, ExerciseSearch.Filters filters, boolean onlyCompatible) {
		query = text == null ? "" : text;
		List<Exercise> result = new ExerciseSearch(catalog).find(query, filters);
		if (!onlyCompatible) return result;
		final TrainingProfile profile;
		try {
			profile = context();
		} catch (RuntimeException invalid) {
			return Collections.emptyList();
		}
		List<Exercise> compatible = new ArrayList<>();
		CompatibilityEngine eligibility = new CompatibilityEngine();
		for (Exercise exercise : result)
			if (eligibility.isCompatible(exercise, profile)) compatible.add(exercise);
		return Collections.unmodifiableList(compatible);
	}

	/** Identifica el borrador para reiniciar sólo su estado visual al comenzar otro. */
	public long draftRevision() {
		return generation;
	}

	public String compatibilityMessage(Exercise exercise) {
		for (Draft draft : drafts)
			if (draft.exercise.getId().equals(exercise.getId())) return "Ya añadido.";
		if (drafts.size() >= MAX_EXERCISES) return "Máximo 20 ejercicios por rutina.";
		return contextCompatibility(exercise);
	}

	public String contextCompatibility(Exercise exercise) {
		try {
			if (!new CompatibilityEngine().isCompatible(exercise, context()))
				return "No coincide con la disciplina, objetivo, entorno, enfoque o material de esta rutina.";
			return "";
		} catch (RuntimeException invalid) {
			return "Completa primero el contexto de la rutina.";
		}
	}

	/** Sólo admite identificadores del catálogo y conserva siempre su ejercicio canónico. */
	public boolean add(String exerciseId) {
		if (busy || owner == null) return false;
		Exercise exercise = ChatProtocol.find(catalog, exerciseId);
		if (exercise == null) { fail("El ejercicio no está en el catálogo."); return false; }
		String reason = compatibilityMessage(exercise);
		if (!reason.isEmpty()) { fail(reason); return false; }
		touch();
		Draft draft = new Draft(exercise);
		draft.minutes = "5";
		switch (exercise.getMetricType()) {
			case STRENGTH:
				draft.values.put("sets", "3");
				draft.values.put("repetitions", "8");
				draft.values.put("loadKg", "0");
				draft.values.put("restSeconds", "60");
				break;
			case ISOMETRIC:
				draft.values.put("sets", "3");
				draft.values.put("durationSeconds", "20");
				draft.values.put("restSeconds", "30");
				break;
			case ENDURANCE:
				draft.values.put("timeMinutes", "10");
				draft.values.put("intervals", "1");
				draft.minutes = "10";
				break;
			case MOBILITY:
				draft.values.put("durationSeconds", "30");
				draft.values.put("repetitions", "5");
				draft.values.put("side", "ambos");
				break;
			case SPORT_PRACTICE:
				draft.values.put("blocks", "1");
				draft.values.put("durationMinutes", "5");
				break;
		}
		drafts.add(draft);
		selected = drafts.size() - 1;
		changed.run();
		return true;
	}

	public void select(int index) {
		if (!busy && index >= 0 && index < drafts.size()) { selected = index; changed.run(); }
	}

	public void input(int index, String key, String value) {
		if (busy || index < 0 || index >= drafts.size()) return;
		Draft draft = drafts.get(index);
		if (key.equals("loadKg") && !externalLoad(draft.exercise) && !"0".equals(value)) {
			fail("Este ejercicio usa peso corporal: la carga debe ser 0 kg.");
			return;
		}
		if (key.equals("estimatedMinutes")) { touch(); draft.minutes = value; return; }
		for (MetricValidator.Field field : fields(draft.exercise))
			if (field.key.equals(key)) { touch(); draft.values.put(key, value); return; }
	}

	public List<MetricValidator.Field> fields(Exercise exercise) {
		List<MetricValidator.Field> result = new ArrayList<>();
		for (MetricValidator.Field field : new MetricValidator().fields(exercise.getMetricType()))
			if (!field.key.equals("effort")) result.add(field);
		return result;
	}

	public boolean externalLoad(Exercise exercise) {
		return !Collections.disjoint(exercise.getRequiredResources(), EnumSet.of(Resource.DUMBBELLS, Resource.BARBELL, Resource.MACHINE));
	}

	public void remove(int index) {
		if (!busy && index >= 0 && index < drafts.size()) {
			touch();
			drafts.remove(index);
			selected = Math.max(0, Math.min(selected, drafts.size() - 1));
			changed.run();
		}
	}

	public void move(int index, int delta) {
		int target = index + delta;
		if (!busy && index >= 0 && index < drafts.size() && target >= 0 && target < drafts.size()) {
			touch();
			Collections.swap(drafts, index, target);
			selected = target;
			changed.run();
		}
	}

	public void save() {
		if (busy || saved != null) return;
		if (owner == null) { fail("Selecciona una cuenta antes de guardar."); return; }
		final Routine routine;
		try {
			if (title.trim().isEmpty() || title.trim().length() > 120)
				throw new IllegalArgumentException("Nombre obligatorio, máximo 120 caracteres.");
			TrainingProfile profile = context();
			List<RoutineItem> items = new ArrayList<>();
			for (Draft draft : drafts) {
				int minutes = Integer.parseInt(draft.minutes.trim());
				if (minutes < 1 || minutes > 240) throw new IllegalArgumentException("Minutos por ejercicio: 1–240.");
				Map<String, String> prescription = new MetricValidator().normalized(draft.exercise.getMetricType(), draft.values);
				ChatProtocol.validatePrescription(draft.exercise, prescription, minutes);
				items.add(new RoutineItem(draft.exercise, prescription, minutes, "Prescripción elegida por la persona en el editor manual."));
			}
			routine = new Routine(proposalId, 0, title.trim(), profile, items,
				"Rutina manual. Revisa la técnica y adapta la intensidad.", new RoutineMetadata(RoutineMetadata.Origin.MANUAL, progression));
			if (!routine.isValidFor(profile))
				throw new IllegalArgumentException("Añade ejercicios compatibles y respeta el tiempo disponible.");
		} catch (RuntimeException invalid) {
			fail("Revisa el editor: " + invalid.getMessage());
			return;
		}
		busy = true;
		error = "";
		long token = generation;
		changed.run();
		try {
			AppStore.Callback<Routine> callback = new AppStore.Callback<Routine>() {
				public void success(Routine value) {
					if (token != generation) return;
					busy = false;
					if (value == null || value.getVersionId() <= 0 || !routine.getProposalId().equals(value.getProposalId())) {
						fail("El guardado no devolvió una versión válida.");
						return;
					}
					saved = value;
					onSaved.accept(value);
					changed.run();
				}
				public void failure(Exception failure) {
					if (token != generation) return;
					busy = false;
					fail(failure.getMessage());
				}
			};
			if (editingId == null) store.acceptManual(routine, callback);
			else store.replaceManual(editingId, routine, callback);
		} catch (RuntimeException failure) {
			if (token == generation) { busy = false; fail("No se pudo guardar la rutina manual."); }
		}
	}
}
