package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import java.util.*;

/**
 * CONTROLADOR raíz MVC: propietario, perfil y navegación independientes de Android.
 */
public final class AppController {
	public TrainingProfile profile;

	public enum Screen {HOME, TRAINING, ASSISTANT, HISTORY, PROFILE, ROUTINE, SESSION, EXERCISE, ACCOUNT, NUTRITION, FOOD, BODY}

	/**
	 * Descriptor de edición: la vista infla XML y remite texto, no interpreta enums ni reglas.
	 */
	public static final class InputField {
		public final String key, label, value;
		public final List<String> values, labels;
		public final boolean bool, number;

		InputField(String key, String label, String value, List<String> values, List<String> labels, boolean bool,
			boolean number) {
			this.key = key;
			this.label = label;
			this.value = value;
			this.values = values;
			this.labels = labels;
			this.bool = bool;
			this.number = number;
		}
	}

	public final RoutineController routines;
	public final SessionController sessions;
	public final CatalogRepository catalog;
	public List<Session> history = Collections.emptyList(), pending = Collections.emptyList();
	public List<Routine> versions = Collections.emptyList();
	private ProgressionEngine.Plan progression;
	private Routine progressionBase;
	private List<Session> progressionHistory;

	/**
	 * Siguiente nivel de la rutina aceptada calculado con la última sesión; null si no hay cambios.
	 * Se recalcula cuando cambia la rutina aceptada o el historial.
	 */
	public ProgressionEngine.Plan progression() {
		if (routines.accepted != progressionBase || history != progressionHistory) {
			progressionBase = routines.accepted;
			progressionHistory = history;
			progression = new ProgressionEngine().next(routines.accepted, history);
		}
		return progression;
	}
	public Screen screen = Screen.HOME, tab = Screen.HOME;
	public Exercise exercise;
	public String error = "", notice = "";
	public boolean loading;
	private final AppStore store;
	private String owner;
	private long generation, loadRequest;
	private boolean profileDirty, profileSaving;
	private final Map<String, String> profileDraft = new LinkedHashMap<>();
	private final Deque<Screen> backStack = new ArrayDeque<>();
	private Runnable observer = () -> {
	};

	public interface ExerciseRoute {
		void open(Exercise exercise, TrainingProfile profile);
	}

	private ExerciseRoute explanationRoute, visualizationRoute;
	private java.util.function.Predicate<Exercise> visualizationPolicy = e -> false;
	private java.util.function.Function<Exercise, String> visualizationExplanation = e -> null;
	private List<Exercise> animations = Collections.emptyList();
	private long navigationRevision;

	public long navigationRevision() {
		return navigationRevision;
	}

	public void visualizations(ExerciseRoute route, java.util.function.Predicate<Exercise> policy,
		List<Exercise> available) {
		visualizationRoute = route;
		visualizationPolicy = policy;
		animations = available;
	}

	public void visualizationExplanations(java.util.function.Function<Exercise, String> explanation) {
		visualizationExplanation = explanation == null ? e -> null : explanation;
	}

	/**
	 * Indica si la vista 3D es una captura CC0 o una animación esquemática; null si no hay vista disponible.
	 */
	public String visualizationExplanation() {
		return visualizationAvailable() ? visualizationExplanation.apply(exercise) : null;
	}

	public List<Exercise> availableAnimations() {
		return animations;
	}

	/**
	 * Punto de conexión de Task6: ruta opcional; mientras falta se comunica pendiente con honestidad.
	 */
	public void explanations(ExerciseRoute route) {
		explanationRoute = route;
	}

	/**
	 * Punto de conexión de Task7: solo activar después de comprobar un recurso 3D realmente disponible.
	 */
	public void visualizations(ExerciseRoute route) {
		visualizationRoute = route;
	}

	public boolean visualizationAvailable() {
		return owner != null && visualizationRoute != null && visualizationPolicy.test(exercise);
	}

	public void requestExplanation() {
		if (exercise == null)
			return;
		if (explanationRoute == null)
			error("La explicación guiada aún está pendiente. Puedes consultar la técnica escrita de este ejercicio.");
		else
			explanationRoute.open(exercise, profile);
	}

	public void requestVisualization() {
		if (visualizationAvailable())
			visualizationRoute.open(exercise, profile);
	}

	public AppController(AppStore store, CatalogRepository catalog) {
		this.store = store;
		this.catalog = catalog;
		routines = new RoutineController(store, () -> profile, catalog.getExercises(), this::changed, this::error);
		sessions = new SessionController(store, this::changed, this::error, () -> {
			notice = "Sesión finalizada y guardada localmente.";
			reload();
			navigate(Screen.HISTORY);
		});
	}

	/**
	 * La suscripción solo renderiza; el propietario de ciclo de vida la retira al destruir su vista.
	 */
	public void observe(Runnable observer) {
		this.observer = observer == null ? () -> {
		} : observer;
		changed();
	}

	public void changed() {
		observer.run();
	}

	public void error(String message) {
		error = message == null ? "No se pudo completar la acción." : message;
		changed();
	}

	public void dismissError() {
		error = "";
	}

	public String owner() {
		return owner;
	}

	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		owner = next;
		generation++;
		navigationRevision++;
		loadRequest++;
		profile = null;
		history = Collections.emptyList();
		pending = Collections.emptyList();
		versions = Collections.emptyList();
		progression = null;
		progressionBase = null;
		progressionHistory = null;
		profileDraft.clear();
		backStack.clear();
		profileDirty = false;
		profileSaving = false;
		routines.reset();
		sessions.reset();
		exercise = null;
		error = "";
		notice = "";
		screen = Screen.HOME;
		tab = Screen.HOME;
		loading = false;
		if (owner != null)
			reload();
		else
			changed();
	}

	/**
	 * Lectura con dos tokens: cambio de cuenta y una lectura posterior invalidan resultados anteriores.
	 */
	public void reload() {
		if (owner == null)
			return;
		long token = generation, request = ++loadRequest;
		loading = true;
		changed();
		store.load(new AppStore.Callback<AppStore.Snapshot>() {
			public void success(AppStore.Snapshot snapshot) {
				if (token != generation || request != loadRequest)
					return;
				loading = false;
				profile = snapshot.profile;
				history = snapshot.history;
				pending = snapshot.pending;
				versions = snapshot.routines;
				if (!profileDirty)
					fillProfile(profile == null ? new TrainingProfile.Builder().build() : profile);
				routines.accepted = versions.isEmpty() ? null : versions.get(0);
				changed();
			}

			public void failure(Exception e) {
				if (token != generation || request != loadRequest)
					return;
				loading = false;
				error(e.getMessage());
			}
		});
	}

	public void navigate(Screen next) {
		navigationRevision++;
		if (next == Screen.HOME || next == Screen.TRAINING || next == Screen.ASSISTANT || next == Screen.HISTORY ||
			next == Screen.NUTRITION) {
			tab = next;
			backStack.clear();
		} else if (screen != next)
			backStack.push(screen);
		screen = next;
		changed();
	}

	public void back() {
		navigationRevision++;
		screen = backStack.isEmpty() ? tab : backStack.pop();
		changed();
	}

	public void openExercise(Exercise value) {
		exercise = value;
		navigate(Screen.EXERCISE);
	}

	public void createRoutine() {
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		routines.create();
		if (routines.preview != null)
			navigate(Screen.ROUTINE);
	}

	public void startSession() {
		if (sessions.session == null || sessions.session.getStatus() == Session.Status.FINISHED)
			sessions.start(routines.accepted);
		if (sessions.session != null)
			navigate(Screen.SESSION);
	}

	/**
	 * Abre la progresión como propuesta: se revisa y acepta igual que cualquier otra versión.
	 */
	public void reviewProgression() {
		ProgressionEngine.Plan plan = progression();
		if (plan == null || routines.busy)
			return;
		routines.preview = plan.routine;
		navigate(Screen.ROUTINE);
	}

	public void resume(Session draft) {
		sessions.resume(draft);
		navigate(Screen.SESSION);
	}

	public void profileInput(String key, String value) {
		profileDraft.put(key, value);
		profileDirty = true;
		// Cambiar disciplina o tipo cambia qué se pregunta: se ajusta el borrador y se redibuja el formulario.
		if (key.equals("sportId") || key.equals("category") || key.equals("focus")) {
			normalizeProfileDraft();
			changed();
		}
	}

	private DisciplineForm disciplineForm() {
		return DisciplineForm.of(profileDraft.getOrDefault("sportId", "strength"));
	}

	/**
	 * Deja cada opción dentro de lo que admite la disciplina; los textos y datos ya escritos se conservan.
	 */
	private void normalizeProfileDraft() {
		DisciplineForm form = disciplineForm();
		TrainingProfile.Category category = allowed("category", form.categories);
		TrainingProfile.Focus focus = allowed("focus", form.focuses);
		allowed("environment", form.environments(category, focus));
		allowed("goal", form.goals(focus, catalog.getExercises()));
		for (TrainingProfile.Resource r : form.includedResources)
			profileDraft.put("resource." + r.name(), "true");
	}

	private <E extends Enum<E>> E allowed(String key, List<E> options) {
		for (E option : options)
			if (option.name().equals(profileDraft.get(key)))
				return option;
		profileDraft.put(key, options.get(0).name());
		return options.get(0);
	}

	/**
	 * Nombre de la disciplina elegida en el borrador, para titular sus preguntas propias.
	 */
	public String disciplineName() {
		Sport s = catalog.findSport(profileDraft.getOrDefault("sportId", ""));
		return s == null ? "tu disciplina" : s.getName();
	}

	/**
	 * Validación centralizada: el formulario conserva el texto incluso si falla construir el perfil.
	 */
	public void saveProfile() {
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		if (profileSaving)
			return;
		try {
			normalizeProfileDraft();
			DisciplineForm form = disciplineForm();
			TrainingProfile.Category category = TrainingProfile.Category.valueOf(profileDraft.get("category"));
			// Solo cuenta el material que el formulario de esta disciplina pregunta (o incluye de serie).
			Set<TrainingProfile.Resource> resources = EnumSet.noneOf(TrainingProfile.Resource.class);
			resources.addAll(form.includedResources);
			for (TrainingProfile.Resource resource : form.askedResources(category))
				if (Boolean.parseBoolean(profileDraft.get("resource." + resource.name())))
					resources.add(resource);
			Map<String, String> details = new LinkedHashMap<>();
			for (DisciplineForm.Detail d : form.details)
				details.put(d.key, profileDraft.getOrDefault("detail." + d.key, ""));
			TrainingProfile p = new TrainingProfile.Builder().sportId(profileDraft.get("sportId"))
				.goal(TrainingProfile.Goal.valueOf(profileDraft.get("goal")))
				.environment(TrainingProfile.Environment.valueOf(profileDraft.get("environment")))
				.focus(TrainingProfile.Focus.valueOf(profileDraft.get("focus")))
				.category(TrainingProfile.Category.valueOf(profileDraft.get("category"))).resources(resources)
				.daysPerWeek(Integer.parseInt(profileDraft.get("daysPerWeek")))
				.durationMinutes(Integer.parseInt(profileDraft.get("durationMinutes"))).level(profileDraft.get("level"))
				.experience(profileDraft.get("experience")).space(profileDraft.get("space"))
				.preferences(profileDraft.get("preferences")).limitations(profileDraft.get("limitations"))
				.historyConsent(Boolean.parseBoolean(profileDraft.get("historyConsent"))).details(details).build();
			if (catalog.findSport(p.getSportId()) == null)
				throw new IllegalArgumentException("Selecciona un deporte del catálogo.");
			profileSaving = true;
			long token = generation;
			store.profile(p, new AppStore.Callback<Void>() {
				public void success(Void ignored) {
					if (token != generation)
						return;
					profileSaving = false;
					profile = p;
					profileDirty = false;
					fillProfile(p);
					notice = "Perfil guardado. La próxima propuesta usará estos filtros.";
					navigate(Screen.TRAINING);
				}

				public void failure(Exception e) {
					if (token != generation)
						return;
					profileSaving = false;
					error(e.getMessage());
				}
			});
		} catch (RuntimeException e) {
			error("Revisa el perfil: días 1–7, tiempo 5–240 min y nivel obligatorio. " + e.getMessage());
		}
	}

	public boolean profileSaving() {
		return profileSaving;
	}

	private void fillProfile(TrainingProfile p) {
		profileDraft.clear();
		profileDraft.put("sportId", p.getSportId());
		profileDraft.put("goal", p.getGoal().name());
		profileDraft.put("environment", p.getEnvironment().name());
		profileDraft.put("focus", p.getFocus().name());
		profileDraft.put("category", p.getCategory().name());
		profileDraft.put("daysPerWeek", Integer.toString(p.getDaysPerWeek()));
		profileDraft.put("durationMinutes", Integer.toString(p.getDurationMinutes()));
		profileDraft.put("level", p.getLevel());
		profileDraft.put("experience", p.getExperience());
		profileDraft.put("space", p.getSpace());
		profileDraft.put("preferences", p.getPreferences());
		profileDraft.put("limitations", p.getLimitations());
		profileDraft.put("historyConsent", Boolean.toString(p.isHistoryConsent()));
		for (TrainingProfile.Resource r : TrainingProfile.Resource.values())
			profileDraft.put("resource." + r.name(), Boolean.toString(p.getResources().contains(r)));
		for (Map.Entry<String, String> d : p.getDetails().entrySet())
			profileDraft.put("detail." + d.getKey(), d.getValue());
		normalizeProfileDraft();
	}

	public List<InputField> profileFields() {
		if (profileDraft.isEmpty())
			fillProfile(profile == null ? new TrainingProfile.Builder().build() : profile);
		DisciplineForm form = disciplineForm();
		TrainingProfile.Category category = TrainingProfile.Category.valueOf(profileDraft.get("category"));
		List<InputField> f = new ArrayList<>();
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		for (Sport s : catalog.getSports()) {
			ids.add(s.getId());
			names.add(s.getName());
		}
		f.add(new InputField("sportId", "Disciplina", profileDraft.get("sportId"), ids, names, false, false));
		// Las preguntas con una sola respuesta posible para esta disciplina no se muestran: ya están fijadas.
		if (form.categories.size() > 1)
			f.add(options("category", "Tipo de entrenamiento", form.categories));
		TrainingProfile.Focus focus = TrainingProfile.Focus.valueOf(profileDraft.get("focus"));
		if (form.focuses.size() > 1)
			f.add(new InputField("focus", "Qué quieres trabajar", profileDraft.get("focus"),
				Arrays.asList("SPECIFIC", "COMPLEMENTARY"), Arrays.asList(form.specificLabel, form.complementaryLabel),
				false, false));
		if (form.environments(category, focus).size() > 1)
			f.add(options("environment", "Dónde entrenas", form.environments(category, focus)));
		List<TrainingProfile.Goal> goals = form.goals(focus, catalog.getExercises());
		if (goals.size() > 1)
			f.add(options("goal", "Objetivo", goals));
		for (DisciplineForm.Detail d : form.details) {
			String key = "detail." + d.key;
			if (d.options.isEmpty()) {
				f.add(field(key, d.label, false, d.number));
				continue;
			}
			List<String> v = new ArrayList<>(), t = new ArrayList<>();
			v.add("");
			t.add("Sin indicar");
			for (String option : d.options) {
				String[] e = option.split("\\|", 2);
				v.add(e[0]);
				t.add(e[1]);
			}
			f.add(new InputField(key, d.label, profileDraft.getOrDefault(key, ""), v, t, false, false));
		}
		f.add(field("daysPerWeek", "Días por semana (1–7)", false, true));
		f.add(field("durationMinutes", form.durationLabel + " (5–240 min)", false, true));
		f.add(field("level", "Nivel", false, false));
		f.add(field("experience", "Experiencia previa", false, false));
		if (form.asksSpace)
			f.add(field("space", "Espacio disponible", false, false));
		f.add(field("preferences", "Preferencias", false, false));
		f.add(field("limitations", "Limitaciones que quieras declarar", false, false));
		for (TrainingProfile.Resource r : form.askedResources(category))
			f.add(field("resource." + r.name(), "Disponible: " + label(r.name()), true, false));
		f.add(field("historyConsent", "Permitir enviar mensajes anteriores al servidor opcional", true, false));
		return f;
	}

	private InputField options(String key, String label, List<? extends Enum<?>> values) {
		List<String> v = new ArrayList<>(), t = new ArrayList<>();
		for (Enum<?> value : values) {
			v.add(value.name());
			t.add(label(value.name()));
		}
		return new InputField(key, label, profileDraft.getOrDefault(key, ""), v, t, false, false);
	}

	private InputField field(String k, String l, boolean b, boolean n) {
		return new InputField(k, l, profileDraft.getOrDefault(k, ""), Collections.emptyList(), Collections.emptyList(),
			b, n);
	}

	public String profileSummary() {
		return profileSummary(profile);
	}

	/**
	 * Permite presentar el perfil del snapshot aceptado, aunque el usuario haya editado el actual.
	 */
	public String profileSummary(TrainingProfile profile) {
		if (profile == null)
			return "Aún no has guardado tu perfil. Elige disciplina, objetivo, entorno y los recursos que realmente tienes.";
		Sport s = catalog.findSport(profile.getSportId());
		return label(profile.getCategory().name()) + " · " + (s == null ? profile.getSportId() : s.getName()) + "\n" +
			label(profile.getGoal().name()) + " · " + label(profile.getEnvironment().name()) + " · " +
			profile.getDurationMinutes() + " min · " + profile.getDaysPerWeek() + " días/semana\nRecursos: " +
			(profile.getResources().isEmpty() ? "ninguno" : labels(profile.getResources())) + "\n" +
			details(profile) + (s == null ? "" : s.getCoverageMessage(profile.getFocus()));
	}

	/**
	 * Datos propios de la disciplina con las etiquetas del formulario, una línea; vacío si no hay ninguno.
	 */
	private static String details(TrainingProfile profile) {
		List<String> parts = new ArrayList<>();
		for (DisciplineForm.Detail d : DisciplineForm.of(profile.getSportId()).details) {
			String value = profile.getDetails().get(d.key);
			if (value == null)
				continue;
			for (String option : d.options)
				if (option.startsWith(value + "|"))
					value = option.substring(value.length() + 1);
			parts.add(d.label + ": " + value);
		}
		return parts.isEmpty() ? "" : String.join(" · ", parts) + "\n";
	}

	public static String labels(Collection<?> values) {
		List<String> result = new ArrayList<>();
		for (Object value : values)
			result.add(label(value.toString()));
		return String.join(", ", result);
	}

	public static String label(String name) {
		switch (name) {
			case "GYM":
				return "Gimnasio";
			case "HOME_EQUIPMENT":
				return "Casa con material";
			case "NO_EQUIPMENT":
				return "Sin equipamiento";
			case "SPORT":
				return "Deporte";
			case "HOME":
				return "Casa";
			case "OUTDOORS":
				return "Exterior";
			case "POOL":
				return "Piscina / acceso al agua";
			case "STRENGTH":
				return "Fuerza";
			case "ISOMETRIC":
				return "Isométrico";
			case "ENDURANCE":
				return "Resistencia";
			case "MOBILITY":
				return "Movilidad";
			case "SPORT_PRACTICE":
				return "Práctica deportiva";
			case "GENERAL_FITNESS":
				return "Condición general";
			case "DUMBBELLS":
				return "Mancuernas";
			case "BARBELL":
				return "Barra";
			case "BANDS":
				return "Bandas";
			case "MAT":
				return "Esterilla";
			case "BENCH":
				return "Banco";
			case "MACHINE":
				return "Máquina";
			case "CHAIR":
				return "Silla";
			case "WALL":
				return "Pared";
			case "SUPPORT":
				return "Apoyo";
			case "BICYCLE":
				return "Bicicleta";
			default:
				return name;
		}
	}

	public static String metrics(MetricType type, Map<String, String> values) {
		List<String> lines = new ArrayList<>();
		for (MetricValidator.Field field : new MetricValidator().fields(type))
			if (values.containsKey(field.key))
				lines.add(field.label + ": " + values.get(field.key).replace('.', ','));
		return String.join("\n", lines);
	}

	/**
	 * Etiquetas de propuesta separadas de las de resultado para no afirmar que un ejemplo ya se realizó.
	 */
	public static String prescription(MetricType type, Map<String, String> values) {
		return metrics(type, values).replace("Series realizadas", "Series previstas")
			.replace("Bloques realizados", "Bloques previstos")
			.replace("Intervalos / bloques realizados", "Intervalos / bloques previstos")
			.replace("Tiempo realizado", "Tiempo previsto")
			.replace("Carga (kg; 0 = peso corporal)", "Carga de ejemplo (kg)");
	}
}

