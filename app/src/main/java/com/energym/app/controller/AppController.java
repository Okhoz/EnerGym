package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import java.util.*;

/**
 * CONTROLADOR raíz MVC: propietario, perfil y navegación independientes de Android.
 */
public final class AppController {
	public TrainingProfile profile;

	public enum Screen {HOME, TRAINING, ASSISTANT, HISTORY, PROFILE, ROUTINE, MANUAL_ROUTINE, SESSION, EXERCISE, ACCOUNT, NUTRITION, FOOD, BODY, PLAN}

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
	public final ManualRoutineController manual;
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
	public boolean loading, trainingMutation;
	/** Becomes true only when a current account snapshot includes cached or validated restored data. */
	public boolean initialDataReady = true;
	private final AppStore store;
	private String owner;
	/** Null conserva la elección automática; sólo elegir o aceptar fija un identificador. */
	private String selectedRoutineId;
	private Routine lastLoadedRoutine;
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
		manual = new ManualRoutineController(store, catalog.getExercises(), this::changed, saved -> {
			routines.accepted = saved;
			selectedRoutineId = saved.getProposalId();
			notice = "Rutina manual guardada. Tu perfil global no ha cambiado.";
			reload();
			navigate(Screen.TRAINING);
		});
		sessions = new SessionController(store, this::changed, this::error, () -> {
			notice = "Sesión finalizada y guardada localmente.";
			selectNextPlanDay();
			retireFreeRoutine();
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

	/** Invalida los diálogos abiertos antes de cambiar de cuenta, incluso A → B → A. */
	public long accountRevision() { return generation; }

	public String owner() {
		return owner;
	}

	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		owner = next;
		initialDataReady = owner == null;
		generation++;
		navigationRevision++;
		loadRequest++;
		profile = null;
		history = Collections.emptyList();
		pending = Collections.emptyList();
		versions = Collections.emptyList();
		planPreview = Collections.emptyList();
		selectedRoutineId = null;
		lastLoadedRoutine = null;
		progression = null;
		progressionBase = null;
		progressionHistory = null;
		lastProgression = Collections.emptyList();
		autoProgressing = false;
		profileDraft.clear();
		backStack.clear();
		profileDirty = false;
		profileSaving = false;
		routines.reset();
		manual.activate(next);
		sessions.reset();
		exercise = null;
		error = "";
		notice = "";
		screen = Screen.HOME;
		tab = Screen.HOME;
		loading = trainingMutation = false;
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
				initialDataReady = snapshot.initialDataReady;
				profile = snapshot.profile;
				history = snapshot.history;
				pending = snapshot.pending;
				versions = snapshot.routines;
				if (!profileDirty)
					fillProfile(profile == null ? new TrainingProfile.Builder().build() : profile);
				// RoutineController también acepta directamente: una instancia nueva procede de esa aceptación,
				// mientras que una recarga previa dejó exactamente lastLoadedRoutine como selección automática.
				if (routines.accepted != null && routines.accepted != lastLoadedRoutine)
					selectedRoutineId = routines.accepted.getProposalId();
				Routine selected = null;
				for (Routine version : versions)
					if (version.getProposalId().equals(selectedRoutineId)) {
						selected = version;
						break;
					}
				// Sin elección explícita se sigue el orden restaurado; una elección humana ausente permanece pendiente.
				if (selectedRoutineId == null && !versions.isEmpty()) {
					selected = defaultRoutine();
				}
				routines.accepted = selected;
				lastLoadedRoutine = selected;
				changed();
				autoProgress();
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
		if (trainingMutation) return;
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		routines.create();
		if (routines.preview != null)
			navigate(Screen.ROUTINE);
	}

	/** Ejercicios que se pueden añadir al entreno libre de hoy. */
	public List<Exercise> freeCandidates() {
		if (profile == null)
			return Collections.emptyList();
		Routine current = sessions.session != null && FreeWorkout.is(sessions.session.getRoutine()) ?
			sessions.session.getRoutine() : null;
		return FreeWorkout.candidates(profile, catalog.getExercises(), current);
	}

	/**
	 * Entreno libre (como apuntar en FitNotes sin rutina): empieza con un ejercicio o, si ya hay uno libre abierto,
	 * le añade este. Cada cambio guarda una versión nueva y la sesión pasa a ella sin perder lo apuntado.
	 */
	public void addFreeExercise(Exercise exercise) {
		if (trainingMutation || loading || !initialDataReady || sessions.busy || exercise == null) return;
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		if (profile == null) {
			error("Guarda tu perfil (lugar y material) antes de entrenar.");
			return;
		}
		Session open = sessions.session != null && sessions.session.getStatus() == Session.Status.DRAFT ?
			sessions.session : null;
		if (open != null && !FreeWorkout.is(open.getRoutine())) {
			error("Ya tienes un entreno de rutina abierto. Termínalo o descártalo antes de empezar uno libre.");
			return;
		}
		String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d/M"));
		Routine next = FreeWorkout.with(profile, open == null ? null : open.getRoutine(), exercise, today);
		trainingMutation = true;
		long token = generation;
		changed();
		store.acceptManual(next, new AppStore.Callback<Routine>() {
			public void success(Routine saved) {
				if (token != generation) return;
				trainingMutation = false;
				if (open == null) {
					sessions.start(saved);
					navigate(Screen.SESSION);
					return;
				}
				String oldRoutine = open.getRoutine().getProposalId();
				sessions.migrate(saved, old -> {
					// El borrador y la versión anteriores se archivan: la sesión nueva ya lleva todo lo apuntado.
					store.archiveTraining(false, old.getSyncId(), ignore());
					store.archiveTraining(true, oldRoutine, ignore());
				});
				navigate(Screen.SESSION);
			}

			public void failure(Exception e) {
				if (token != generation) return;
				trainingMutation = false;
				error(e.getMessage());
			}
		});
	}

	/** Al terminar un entreno libre su rutina del día se retira de la lista; la sesión guarda su copia. */
	private void retireFreeRoutine() {
		if (sessions.session != null && FreeWorkout.is(sessions.session.getRoutine()))
			store.archiveTraining(true, sessions.session.getRoutine().getProposalId(), ignore());
	}

	/** Archivado en segundo plano: al acabar refresca listas; un fallo no interrumpe el entreno. */
	private AppStore.Callback<Void> ignore() {
		long token = generation;
		return new AppStore.Callback<Void>() {
			public void success(Void ignored) {
				if (token == generation)
					reload();
			}

			public void failure(Exception e) {
			}
		};
	}

	/**
	 * Rutina de hoy sin elección explícita: en un plan semanal, el día que sigue al último entreno terminado; si el
	 * plan aún no se ha empezado, su día 1; si no hay plan, la más reciente.
	 */
	private Routine defaultRoutine() {
		Session last = null;
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED && (last == null || s.getCompletedAt() > last.getCompletedAt()))
				last = s;
		Routine next = last == null ? null : WeeklyPlan.next(last.getRoutine(), versions);
		if (next != null)
			return next;
		Routine newest = versions.get(0);
		for (Routine r : versions)
			if (!FreeWorkout.is(r)) {
				newest = r;
				break;
			}
		int[] day = WeeklyPlan.dayOf(newest);
		if (day != null && day[0] != 1)
			for (Routine r : versions) {
				int[] other = WeeklyPlan.dayOf(r);
				if (other != null && other[0] == 1 && other[1] == day[1] && WeeklyPlan.samePlan(r, newest))
					return r;
			}
		return newest;
	}

	/** Plan semanal: al terminar un día queda seleccionado el siguiente, como en una rutina de FitNotes. */
	private void selectNextPlanDay() {
		Routine next = sessions.session == null ? null : WeeklyPlan.next(sessions.session.getRoutine(), versions);
		if (next != null) {
			selectedRoutineId = next.getProposalId();
			routines.accepted = next;
			notice = "Sesión guardada. Siguiente: " + next.getTitle() + ".";
		}
	}

	/** Plan semanal pendiente de revisar (una rutina por día) y lo pedido para rehacerlo. */
	public List<Routine> planPreview = Collections.emptyList();
	private String planAsk = "";

	/** Prepara un plan semanal sin guardar nada y abre su revisión. */
	public void createPlan(String asked) {
		if (trainingMutation) return;
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		if (profile == null) {
			error("Guarda tu perfil (días por semana, lugar y material) antes de crear un plan.");
			return;
		}
		planAsk = asked == null ? "" : asked;
		planPreview = WeeklyPlan.create(profile, catalog.getExercises(), planAsk, System.nanoTime());
		if (planPreview.isEmpty()) {
			error("No hay ejercicios compatibles con tu perfil para armar un plan. Revisa lugar y material.");
			return;
		}
		navigate(Screen.PLAN);
	}

	/** Otra combinación de ejercicios con el mismo reparto de días. */
	public void shufflePlan() {
		if (trainingMutation || profile == null) return;
		planPreview = WeeklyPlan.create(profile, catalog.getExercises(), planAsk, System.nanoTime());
		changed();
	}

	public void cancelPlan() {
		planPreview = Collections.emptyList();
		back();
	}

	/** Guarda todos los días del plan como rutinas y deja seleccionado el día 1. */
	public void acceptPlan() {
		if (trainingMutation || planPreview.isEmpty()) return;
		trainingMutation = true;
		List<Routine> pending = new ArrayList<>(planPreview);
		List<Routine> saved = new ArrayList<>();
		long token = generation;
		changed();
		acceptNext(pending, saved, token);
	}

	private void acceptNext(List<Routine> pending, List<Routine> saved, long token) {
		if (pending.isEmpty()) {
			trainingMutation = false;
			planPreview = Collections.emptyList();
			notice = "📅 Plan guardado: " + saved.size() + " días. Hoy toca " + saved.get(0).getTitle() + ".";
			routines.accepted = saved.get(0);
			selectedRoutineId = saved.get(0).getProposalId();
			reload();
			navigate(Screen.TRAINING);
			return;
		}
		store.accept(pending.remove(0), new AppStore.Callback<Routine>() {
			public void success(Routine value) {
				if (token != generation)
					return;
				saved.add(value);
				acceptNext(pending, saved, token);
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				trainingMutation = false;
				error(e.getMessage());
			}
		});
	}

	public void startSession() {
		if (trainingMutation || loading || !initialDataReady) return;
		if (sessions.session != null && sessions.session.getStatus() == Session.Status.DRAFT &&
			routines.accepted != null && !sessions.session.getRoutine().getProposalId().equals(routines.accepted.getProposalId())) {
			error("Ya hay una sesión de otra rutina abierta. Continúala o finalízala antes de empezar la seleccionada.");
			return;
		}
		if (sessions.session == null || sessions.session.getStatus() == Session.Status.FINISHED)
			sessions.start(routines.accepted);
		if (sessions.session != null)
			navigate(Screen.SESSION);
	}

	public void createManualRoutine() {
		if (trainingMutation) return;
		if (owner == null) {
			navigate(Screen.ACCOUNT);
			return;
		}
		if (loading || !initialDataReady) {
			error("Espera a que termine de restaurarse tu cuenta antes de crear una rutina.");
			return;
		}
		manual.begin(profile);
		navigate(Screen.MANUAL_ROUTINE);
	}

	/** Sugerencia del entrenador de hoy (null sin perfil o sin ejercicios compatibles) y por qué. */
	public Routine suggestion;
	public String suggestionReason = "";
	private long suggestionSeed = 1;
	private List<Session> suggestionHistory;
	private TrainingProfile suggestionProfile;

	/**
	 * El entrenador propone una rutina para el grupo muscular que menos has trabajado en las dos últimas semanas
	 * (o de cuerpo completo si aún no hay entrenos). Se recalcula al cambiar el historial o el perfil.
	 */
	public Routine suggest() {
		if (profile == null || owner == null)
			return null;
		if (suggestion != null && history == suggestionHistory && profile == suggestionProfile)
			return suggestion;
		suggestionHistory = history;
		suggestionProfile = profile;
		java.time.LocalDate today = java.time.LocalDate.now();
		List<com.energym.app.progress.MuscleBreakdown.Share> shares = com.energym.app.progress.MuscleBreakdown.of(history,
			java.time.ZoneId.systemDefault(), today.minusDays(13), today);
		RoutineRequest.Area[] candidates = {RoutineRequest.Area.LEGS, RoutineRequest.Area.BACK, RoutineRequest.Area.CHEST,
			RoutineRequest.Area.SHOULDERS, RoutineRequest.Area.GLUTES, RoutineRequest.Area.CORE, RoutineRequest.Area.ARMS};
		RoutineRequest.Area least = null;
		double fewest = Double.MAX_VALUE;
		int offset = (int) Math.floorMod(suggestionSeed, candidates.length);
		// Solo zonas con suficientes ejercicios para tu lugar y material: si no, la rutina no sería de esa zona.
		CompatibilityEngine engine = new CompatibilityEngine();
		for (int i = 0; i < candidates.length; i++) {
			RoutineRequest.Area a = candidates[(i + offset) % candidates.length];
			int available = 0;
			for (Exercise e : catalog.getExercises())
				if (engine.isCompatible(e, profile) && RoutineRequest.areas(e).contains(a) &&
					e.getMetricType() != MetricType.MOBILITY)
					available++;
			if (available < 3)
				continue;
			double sets = 0;
			for (com.energym.app.progress.MuscleBreakdown.Share sh : shares)
				if (sh.area == a)
					sets = sh.sets;
			if (sets < fewest) {
				fewest = sets;
				least = a;
			}
		}
		RoutineRequest asked = shares.isEmpty() || least == null ? RoutineRequest.none() :
			RoutineRequest.of(EnumSet.of(least), RoutineRequest.Intensity.NORMAL, 0);
		Routine r = new RoutinePlanner().create(profile, catalog.getExercises(), asked,
			today.toEpochDay() * 31 + suggestionSeed);
		suggestion = r.isEmpty() ? null : r;
		suggestionReason = shares.isEmpty() || least == null ? "Para empezar, un entreno de cuerpo completo." :
			fewest == 0 ? "No has trabajado " + least.label + " en las últimas dos semanas." :
				"Es lo que menos has trabajado en las últimas dos semanas (" + Math.round(fewest) +
					(Math.round(fewest) == 1 ? " serie)." : " series).");
		return suggestion;
	}

	/** Otra sugerencia distinta (otro grupo o ejercicios). */
	public void nextSuggestion() {
		suggestionSeed++;
		suggestion = null;
		suggestionHistory = null;
		changed();
	}

	/** Guarda la rutina propuesta tal cual (sugerencia o propuesta de la IA) y, si se pide, empieza a entrenarla. */
	public void acceptAndStart(Routine proposal, boolean start) {
		if (trainingMutation || loading || !initialDataReady || proposal == null) return;
		if (sessions.session != null && sessions.session.getStatus() == Session.Status.DRAFT && start) {
			error("Ya tienes un entreno a medias. Termínalo o descártalo antes de empezar otro.");
			return;
		}
		trainingMutation = true;
		long token = generation;
		changed();
		store.accept(proposal, new AppStore.Callback<Routine>() {
			public void success(Routine saved) {
				if (token != generation) return;
				trainingMutation = false;
				if (routines.preview == proposal)
					routines.preview = null;
				routines.accepted = saved;
				selectedRoutineId = saved.getProposalId();
				suggestion = null;
				suggestionHistory = null;
				if (start) {
					sessions.start(saved);
					navigate(Screen.SESSION);
				} else
					navigate(Screen.TRAINING);
				reload();
			}

			public void failure(Exception e) {
				if (token != generation) return;
				trainingMutation = false;
				error(e.getMessage());
			}
		});
	}

	/** Abre una rutina propuesta en el editor para cambiar ejercicios, series o carga antes de guardarla. */
	public void editProposal(Routine proposal) {
		if (trainingMutation || loading || !initialDataReady || owner == null || proposal == null) return;
		manual.editDraft(proposal);
		if (!manual.error.isEmpty()) {
			error(manual.error);
			return;
		}
		if (routines.preview == proposal)
			routines.preview = null;
		navigate(Screen.MANUAL_ROUTINE);
	}

	/** Las rutinas de IA también se pueden ajustar a mano, guardando una nueva versión. */
	public void editRoutine(Routine routine) {
		if (loading || trainingMutation || !initialDataReady || owner == null) return;
		for (Routine version : versions)
			if (version.getProposalId().equals(routine.getProposalId())) {
				manual.edit(version);
				if (manual.editing()) navigate(Screen.MANUAL_ROUTINE);
				else if (!manual.error.isEmpty()) error(manual.error);
				return;
			}
		error("Esta rutina ya no está disponible en tu cuenta.");
	}

	public void deleteRoutine(Routine routine) {
		if (routine != null) archiveTraining(true, routine.getProposalId());
	}

	public void deleteSession(Session session) {
		if (session != null) archiveTraining(false, session.getSyncId());
	}

	private void archiveTraining(boolean routine, String syncId) {
		if (owner == null || loading || trainingMutation || routines.busy || sessions.busy || manual.busy || !initialDataReady) return;
		boolean found = routine ? versions.stream().anyMatch(r -> r.getProposalId().equals(syncId)) :
			history.stream().anyMatch(s -> s.getSyncId().equals(syncId)) || pending.stream().anyMatch(s -> s.getSyncId().equals(syncId)) ||
			(sessions.session != null && sessions.session.getSyncId().equals(syncId));
		if (!found) { error("Este entrenamiento ya no está disponible en tu cuenta."); return; }
		trainingMutation = true;
		long token = generation;
		changed();
		store.archiveTraining(routine, syncId, new AppStore.Callback<Void>() {
			public void success(Void ignored) {
				if (token != generation) return;
				trainingMutation = false;
				if (routine && Objects.equals(selectedRoutineId, syncId)) {
					selectedRoutineId = null;
					routines.accepted = lastLoadedRoutine = null;
				}
				if (!routine && sessions.session != null && sessions.session.getSyncId().equals(syncId)) sessions.reset();
				notice = routine ? "Rutina eliminada. Tus sesiones registradas se conservan." : "Sesión eliminada del progreso.";
				reload();
				if (!routine && screen == Screen.SESSION) navigate(Screen.TRAINING);
			}
			public void failure(Exception e) {
				if (token != generation) return;
				trainingMutation = false;
				error(e.getMessage());
			}
		});
	}

	public void selectRoutine(String proposalId) {
		if (routines.busy || trainingMutation)
			return;
		for (Routine version : versions)
			if (version.getProposalId().equals(proposalId)) {
				selectedRoutineId = proposalId;
				routines.accepted = version;
				changed();
				return;
			}
		error("Esta rutina ya no está disponible en la cuenta actual.");
	}

	/** Punto único de selección tras una aceptación nueva, antes de refrescar la lista. */
	public void routineAccepted(Routine routine) {
		if (routine == null)
			return;
		routines.accepted = routine;
		selectedRoutineId = routine.getProposalId();
		reload();
	}

	public void viewSelectedRoutine() {
		if (routines.busy || routines.accepted == null || routines.preview != null)
			return;
		navigate(Screen.ROUTINE);
	}

	/**
	 * Resumen del entrenamiento para el entrenador IA del móvil (nunca se envía fuera): rutina activa, últimas
	 * sesiones con lo anotado, entreno a medias y progresión disponible. Vacío si aún no hay nada.
	 */
	public String trainingSummary() {
		StringBuilder s = new StringBuilder();
		Routine active = routines.accepted;
		if (active != null && !active.isEmpty()) {
			s.append("Rutina activa: ").append(active.getTitle()).append(" (").append(active.getEstimatedMinutes())
				.append(" min): ");
			List<String> items = new ArrayList<>();
			for (RoutineItem i : active.getItems())
				items.add(i.getExercise().getName() + " " +
					com.energym.app.assistant.RoutineCard.dose(i.getExercise().getMetricType(), i.getPrescription()));
			s.append(String.join("; ", items)).append('\n');
		}
		s.append("Sesiones terminadas: ").append(history.size()).append('\n');
		List<Session> finished = new ArrayList<>(history);
		finished.sort((a, b) -> Long.compare(b.getCompletedAt(), a.getCompletedAt()));
		java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofPattern("d/M");
		for (Session session : finished.subList(0, Math.min(3, finished.size()))) {
			s.append("- ").append(java.time.Instant.ofEpochMilli(session.getCompletedAt())
				.atZone(java.time.ZoneId.systemDefault()).format(day)).append(' ')
				.append(session.getRoutine().getTitle()).append(": ");
			List<String> done = new ArrayList<>();
			for (RoutineItem i : session.getRoutine().getItems()) {
				Map<String, String> r = session.getResults().get(i.getExercise().getId());
				if (r == null)
					continue;
				String effort = r.containsKey("effort") ? " · esfuerzo " + r.get("effort") + "/10" : "";
				done.add(i.getExercise().getName() + " " +
					com.energym.app.assistant.RoutineCard.dose(i.getExercise().getMetricType(), r) + effort);
			}
			s.append(done.isEmpty() ? "sin datos anotados" : String.join("; ", done)).append('\n');
		}
		for (Session draft : pending)
			if (draft.getStatus() == Session.Status.DRAFT) {
				s.append("Entreno a medias: ").append(draft.getRoutine().getTitle()).append(" (")
					.append(draft.getResults().size()).append(" de ").append(draft.getRoutine().getItems().size())
					.append(" ejercicios anotados)\n");
				break;
			}
		if (!lastProgression.isEmpty())
			s.append("Última subida de nivel automática: ").append(String.join("; ", lastProgression)).append('\n');
		ProgressionEngine.Plan plan = progression();
		if (plan != null)
			s.append("Progresión lista para aplicar: ").append(String.join("; ", plan.changes)).append('\n');
		String text = s.toString().trim();
		return text.equals("Sesiones terminadas: 0") ? "" : text.length() > 1400 ? text.substring(0, 1399) + "…" : text;
	}

	/**
	 * Continuidad de la sobrecarga: una rutina nueva no reinicia lo conseguido. Los ejercicios con carga parten
	 * del último peso que se completó, y los isométricos de los segundos ya aguantados (si caben en su tiempo).
	 */
	public Routine continueFrom(Routine proposal) {
		if (proposal == null || proposal.isEmpty() || history.isEmpty())
			return proposal;
		List<RoutineItem> items = new ArrayList<>();
		boolean changed = false;
		for (RoutineItem item : proposal.getItems()) {
			Map<String, String> last = lastResult(item.getExercise().getId());
			Map<String, String> p = new LinkedHashMap<>(item.getPrescription());
			String note = "";
			MetricType type = item.getExercise().getMetricType();
			if (last != null && type == MetricType.STRENGTH && !item.getExercise().getRequiredResources().isEmpty()) {
				double load = number(last.get("loadKg"));
				if (load > 0 && number(p.get("loadKg")) != load) {
					p.put("loadKg", ProgressionEngine.fmt(load));
					note = "Parte de tu última carga: " + ProgressionEngine.fmt(load).replace('.', ',') + " kg.";
				}
			} else if (last != null && type == MetricType.ISOMETRIC) {
				double held = number(last.get("durationSeconds"));
				if (held > number(p.get("durationSeconds")) && held <= 90) {
					p.put("durationSeconds", ProgressionEngine.fmt(held));
					note = "Parte de los " + ProgressionEngine.fmt(held) + " s que ya aguantas.";
				}
			}
			if (!note.isEmpty()) {
				try {
					com.energym.app.assistant.ChatProtocol.validatePrescription(item.getExercise(), p,
						item.getEstimatedMinutes());
				} catch (IllegalArgumentException invalid) {
					note = "";
				}
			}
			if (note.isEmpty())
				items.add(item);
			else {
				changed = true;
				items.add(new RoutineItem(item.getExercise(), p, item.getEstimatedMinutes(),
					(item.getJustification() + " " + note).trim()));
			}
		}
		if (!changed)
			return proposal;
		Routine adjusted = new Routine(proposal.getProposalId(), proposal.getVersionId(), proposal.getTitle(),
			proposal.getProfile(), items, proposal.getExplanation(), proposal.getMetadata());
		return adjusted.isValidFor(proposal.getProfile()) ? adjusted : proposal;
	}

	private Map<String, String> lastResult(String exerciseId) {
		Session best = null;
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED && s.getResults().containsKey(exerciseId) &&
				(best == null || s.getCompletedAt() > best.getCompletedAt()))
				best = s;
		return best == null ? null : best.getResults().get(exerciseId);
	}

	private static double number(String value) {
		try {
			return value == null ? 0 : Double.parseDouble(value.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private boolean autoProgressing;
	/** Cambios de la última subida de nivel automática, para avisar y para el entrenador IA. */
	public List<String> lastProgression = Collections.emptyList();

	/**
	 * Sobrecarga progresiva automática: al terminar una sesión, la rutina activa sube de nivel sola si lo
	 * registrado lo justifica. La versión anterior se archiva; las sesiones hechas conservan su copia. Si el perfil
	 * cambió desde entonces, queda el botón «Subir de nivel» para revisarla a mano.
	 */
	private void autoProgress() {
		Routine base = routines.accepted;
		if (autoProgressing || owner == null || !initialDataReady || routines.busy || base == null)
			return;
		ProgressionEngine.Plan plan = progression();
		if (plan == null)
			return;
		boolean manualOrigin = base.getMetadata() != null &&
			base.getMetadata().getOrigin() == RoutineMetadata.Origin.MANUAL;
		if (!manualOrigin && (profile == null || !plan.routine.isValidFor(profile)))
			return;
		autoProgressing = true;
		long token = generation;
		store.progress(base.getProposalId(), plan.routine, new AppStore.Callback<Routine>() {
			public void success(Routine saved) {
				if (token != generation)
					return;
				autoProgressing = false;
				lastProgression = plan.changes;
				notice = "📈 Tu rutina ha subido de nivel:\n• " + String.join("\n• ", plan.changes);
				routineAccepted(saved);
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				autoProgressing = false;
				changed();
			}
		});
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
		if (owner != null && !initialDataReady)
			return;
		if (key.equals("durationHours")) {
			key = "durationMinutes";
			value = minutes(value);
		}
		profileDraft.put(key, value);
		profileDirty = true;
		// La experiencia ya indica el nivel: se completa solo y la persona puede corregirlo.
		// Sin redibujar: la vista actualiza solo el selector de nivel para no cerrar el teclado.
		if (key.equals("experience") && !levelFor(value).isEmpty())
			profileDraft.put("level", levelFor(value));
		// Cambiar disciplina o tipo cambia qué se pregunta: se ajusta el borrador y se redibuja el formulario.
		if (key.equals("sportId") || key.equals("category") || key.equals("focus")) {
			normalizeProfileDraft();
			changed();
		}
	}

	public String profileValue(String key) {
		return profileDraft.getOrDefault(key, "");
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
		if (!initialDataReady) {
			error("Espera a que termine la restauración inicial. Puedes reintentar desde Cuenta.");
			return;
		}
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
			error("Revisa el perfil: días 1–7, tiempo por sesión entre 0:05 y 4:00 h y nivel obligatorio.");
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
		f.add(list("daysPerWeek", "Días por semana", DAYS, DAY_LABELS));
		// El tiempo admite cualquier valor, así que se escribe, en formato horas:minutos (0:20 h).
		f.add(new InputField("durationHours", form.durationLabel,
			hours(profileDraft.getOrDefault("durationMinutes", "")), Collections.emptyList(), Collections.emptyList(),
			false, false));
		// Experiencia y espacio necesitan detalle propio: siguen siendo texto libre.
		f.add(field("experience", "Experiencia previa", false, false));
		f.add(list("level", "Nivel", LEVELS, LEVELS));
		if (form.asksSpace)
			f.add(field("space", "Espacio disponible", false, false));
		f.add(field("preferences", "Preferencias", false, false));
		f.add(field("limitations", "Limitaciones que quieras declarar", false, false));
		for (TrainingProfile.Resource r : form.askedResources(category))
			f.add(field("resource." + r.name(), "Disponible: " + label(r.name()), true, false));
		// El permiso de historial solo servía para un servidor de IA externo; el entrenador corre en el móvil.
		return f;
	}

	public static final List<String> LEVELS = Collections.unmodifiableList(
		Arrays.asList("Principiante", "Intermedio", "Avanzado"));
	private static final List<String> DAYS = Arrays.asList("1", "2", "3", "4", "5", "6", "7"),
		DAY_LABELS = Arrays.asList("1 día", "2 días", "3 días", "4 días", "5 días", "6 días", "7 días");

	/** Minutos guardados → horas:minutos para mostrar («20» → «0:20»); un texto no numérico se deja igual. */
	public static String hours(String minutes) {
		try {
			int total = Integer.parseInt(minutes.trim());
			return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
		} catch (RuntimeException e) {
			return minutes;
		}
	}

	/**
	 * Tiempo escrito como horas:minutos («0:20», «1:30 h»), o solo horas («2», «1.5»), → minutos enteros; un
	 * texto no válido se conserva para avisar al guardar.
	 */
	public static String minutes(String hours) {
		String h = hours == null ? "" : hours.trim().toLowerCase(Locale.ROOT).replaceAll("\\s*h$", "").trim()
			.replace(',', '.');
		if (h.isEmpty())
			return "";
		java.util.regex.Matcher clock = java.util.regex.Pattern.compile("(\\d+):(\\d{1,2})").matcher(h);
		if (clock.matches()) {
			int m = Integer.parseInt(clock.group(2));
			return m >= 60 ? "horas no válidas" : Long.toString(Integer.parseInt(clock.group(1)) * 60L + m);
		}
		if (!h.matches("\\d+(?:\\.\\d+)?") && !h.matches("\\.\\d+"))
			return "horas no válidas";
		return Long.toString(Math.round(Double.parseDouble(h) * 60));
	}

	/**
	 * Lista cerrada que conserva un valor antiguo escrito a mano para no perderlo al editar el perfil.
	 */
	private InputField list(String key, String label, List<String> values, List<String> labels) {
		List<String> v = new ArrayList<>(), t = new ArrayList<>();
		String current = profileDraft.getOrDefault(key, "");
		if (!values.contains(current)) {
			v.add(current);
			t.add(current.isEmpty() ? "Sin indicar" : current);
		}
		v.addAll(values);
		t.addAll(labels);
		return new InputField(key, label, current, v, t, false, false);
	}

	private static final java.util.regex.Pattern TIME = java.util.regex.Pattern.compile(
		"(\\d+(?:[.,]\\d+)?)\\s*(años?|anos?|meses|mes|semanas?)");

	/**
	 * Nivel que se deduce del tiempo escrito en la experiencia («8 años», «6 meses de fuerza»); vacío si no
	 * menciona un tiempo.
	 */
	static String levelFor(String experience) {
		String text = experience == null ? "" : experience.toLowerCase(Locale.ROOT);
		if (text.contains("nunca") || text.contains("empiezo") || text.contains("ninguna"))
			return "Principiante";
		java.util.regex.Matcher m = TIME.matcher(text);
		if (!m.find())
			return "";
		double amount = Double.parseDouble(m.group(1).replace(',', '.'));
		double years = m.group(2).startsWith("a") ? amount : m.group(2).startsWith("m") ? amount / 12 : amount / 52;
		return years < 1 ? "Principiante" : years <= 3 ? "Intermedio" : "Avanzado";
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
			if (values.containsKey(field.key)) {
				String value = values.get(field.key);
				// «Carga: 0» se lee como un error: sin carga externa es peso corporal.
				if ("loadKg".equals(field.key) && isZero(value))
					lines.add("Carga: peso corporal");
				else
					lines.add(field.label + ": " + value.replace('.', ','));
			}
		return String.join("\n", lines);
	}

	private static boolean isZero(String value) {
		try {
			return Double.parseDouble(value.replace(',', '.')) == 0;
		} catch (NumberFormatException e) {
			return false;
		}
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

