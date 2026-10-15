package com.energym.app.controller;

import com.energym.app.domain.Exercise;
import com.energym.app.domain.TrainingProfile.*;

import java.util.*;

/**
 * Qué pregunta el perfil según la disciplina: opciones válidas, material relevante y datos propios del deporte.
 * Un campo con una sola opción no se muestra; su valor se fija solo al normalizar el borrador.
 */
public final class DisciplineForm {
	/**
	 * Pregunta propia de la disciplina: opciones cerradas («valor|Etiqueta») o texto/número libre.
	 */
	public static final class Detail {
		public final String key, label;
		public final List<String> options;
		public final boolean number;

		Detail(String key, String label, boolean number, String... options) {
			this.key = key;
			this.label = label;
			this.number = number;
			this.options = Collections.unmodifiableList(Arrays.asList(options));
		}
	}

	public final String sportId;
	public final List<Category> categories;
	public final List<Goal> goals;
	public final List<Environment> environments;
	public final List<Focus> focuses;
	public final Set<Resource> resources, includedResources;
	public final List<Detail> details;
	public final String specificLabel, complementaryLabel, durationLabel;
	public final boolean asksSpace;

	private DisciplineForm(Builder b) {
		sportId = b.sportId;
		categories = Collections.unmodifiableList(b.categories);
		goals = Collections.unmodifiableList(b.goals);
		environments = Collections.unmodifiableList(b.environments);
		focuses = Collections.unmodifiableList(b.focuses);
		resources = Collections.unmodifiableSet(b.resources);
		includedResources = Collections.unmodifiableSet(b.included);
		details = Collections.unmodifiableList(b.details);
		specificLabel = b.specificLabel;
		complementaryLabel = b.complementaryLabel;
		durationLabel = b.durationLabel;
		asksSpace = b.asksSpace;
	}

	/**
	 * Entornos posibles: gimnasio y casa quedan fijados por el tipo de entrenamiento, y la preparación
	 * complementaria de un deporte se hace fuera de su sitio habitual (p. ej. fuerza de natación en seco).
	 */
	public List<Environment> environments(Category category, Focus focus) {
		if (category == Category.GYM)
			return Collections.singletonList(Environment.GYM);
		if (category == Category.HOME_EQUIPMENT || category == Category.NO_EQUIPMENT)
			return Collections.singletonList(Environment.HOME);
		if (focus == Focus.COMPLEMENTARY)
			return DRY_LAND;
		return environments;
	}

	private static final List<Environment> DRY_LAND =
		Collections.unmodifiableList(Arrays.asList(Environment.OUTDOORS, Environment.GYM, Environment.HOME));

	/**
	 * Objetivos con ejercicios en el catálogo para esta disciplina y cobertura; si no hay ninguno (contenido
	 * pendiente de validación), todos los de la disciplina, para que la propuesta lo explique.
	 */
	public List<Goal> goals(Focus focus, Collection<Exercise> catalog) {
		Set<Environment> places = EnumSet.noneOf(Environment.class);
		for (Category category : categories)
			places.addAll(environments(category, focus));
		List<Goal> available = new ArrayList<>();
		for (Goal goal : goals)
			for (Exercise e : catalog)
				if (e.getSportIds().contains(sportId) && e.getGoals().contains(goal) &&
					e.getCoverage().name().equals(focus.name()) && !Collections.disjoint(places, e.getEnvironments())) {
					available.add(goal);
					break;
				}
		return available.isEmpty() ? goals : available;
	}

	/**
	 * Material que tiene sentido preguntar: «sin equipamiento» no pregunta nada y el incluido se marca solo.
	 */
	public Set<Resource> askedResources(Category category) {
		if (category == Category.NO_EQUIPMENT)
			return Collections.emptySet();
		Set<Resource> asked = EnumSet.noneOf(Resource.class);
		asked.addAll(resources);
		asked.removeAll(includedResources);
		if (category == Category.HOME_EQUIPMENT)
			asked.remove(Resource.MACHINE);
		return asked;
	}

	private static final Map<String, DisciplineForm> FORMS = new LinkedHashMap<>();

	static {
		Set<Resource> weights = EnumSet.of(Resource.DUMBBELLS, Resource.BARBELL, Resource.BANDS, Resource.MAT,
			Resource.BENCH, Resource.MACHINE, Resource.CHAIR, Resource.WALL, Resource.SUPPORT);
		Set<Resource> light = EnumSet.of(Resource.BANDS, Resource.MAT, Resource.WALL, Resource.SUPPORT,
			Resource.DUMBBELLS);
		List<Category> gym = Arrays.asList(Category.GYM, Category.HOME_EQUIPMENT, Category.NO_EQUIPMENT);
		List<Environment> anywhere = Arrays.asList(Environment.OUTDOORS, Environment.GYM, Environment.HOME);
		Detail hand = new Detail("hand", "Mano dominante", false, "Diestra|Diestra", "Zurda|Zurda");
		Detail matches = new Detail("matchesPerWeek", "Partidos por semana", true);

		add(new Builder("strength").categories(gym)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY).resources(weights)
			.detail(new Detail("split", "División que prefieres", false, "FULL_BODY|Cuerpo completo",
				"UPPER_LOWER|Torso / pierna", "PUSH_PULL_LEGS|Empuje / tirón / pierna", "BODY_PART|Por grupos musculares"))
			.detail(new Detail("priority", "Zona o ejercicio prioritario", false)));
		add(new Builder("powerlifting").categories(Category.GYM)
			.goals(Goal.STRENGTH).resources(Resource.BARBELL, Resource.BENCH).noSpace());
		add(new Builder("calisthenics").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT, Category.GYM)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY)
			.resources(Resource.BANDS, Resource.MAT, Resource.CHAIR, Resource.WALL, Resource.SUPPORT, Resource.BENCH)
			.detail(new Detail("skill", "Habilidad que buscas", false, "PULL_UP|Primera dominada",
				"DIPS|Fondos", "HANDSTAND|Pino", "MUSCLE_UP|Muscle-up", "L_SIT|L-sit", "NONE|Ninguna en concreto"))
			.detail(new Detail("maxPushups", "Flexiones seguidas que haces", true))
			.detail(new Detail("maxPullups", "Dominadas seguidas que haces", true)));
		add(new Builder("functional").categories(gym)
			.goals(Goal.STRENGTH, Goal.ENDURANCE, Goal.GENERAL_FITNESS).resources(weights)
			.detail(new Detail("format", "Formato que prefieres", false, "AMRAP|AMRAP", "EMOM|EMOM",
				"FOR_TIME|Por tiempo", "CIRCUIT|Circuito", "MIXED|Mixto"))
			.detail(new Detail("olympic", "Experiencia con halterofilia", false, "NONE|Ninguna", "BASIC|Básica",
				"ADVANCED|Avanzada")));
		add(new Builder("running").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.MACHINE, Resource.MAT, Resource.WALL, Resource.SUPPORT)
			.focusLabels("Correr", "Fuerza y movilidad para correr").noSpace()
			.detail(new Detail("raceGoal", "Objetivo de carrera", false, "START|Empezar a correr", "5K|5 km",
				"10K|10 km", "HALF|Media maratón", "MARATHON|Maratón", "TRAIL|Trail"))
			.detail(new Detail("weeklyKm", "Kilómetros por semana ahora", true))
			.detail(new Detail("bestTime", "Mejor marca reciente (opcional)", false)));
		add(new Builder("cycling").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.BICYCLE, Resource.MACHINE, Resource.MAT, Resource.WALL)
			.focusLabels("Rodar", "Fuerza y movilidad para ciclismo").noSpace()
			.detail(new Detail("bike", "Tipo de bicicleta", false, "ROAD|Carretera", "MTB|Montaña (MTB)",
				"GRAVEL|Gravel", "INDOOR|Estática o rodillo", "URBAN|Urbana"))
			.detail(new Detail("weeklyKm", "Kilómetros por semana ahora", true))
			.detail(new Detail("ftp", "FTP en vatios, si lo conoces", true)));
		add(new Builder("swimming").sport().environments(Environment.POOL)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.POOL, Resource.BANDS, Resource.MAT).included(Resource.POOL)
			.focusLabels("Nadar", "Fuerza y movilidad fuera del agua").noSpace().duration("Tiempo por sesión")
			.detail(new Detail("stroke", "Estilo principal", false, "FREESTYLE|Crol", "BACKSTROKE|Espalda",
				"BREASTSTROKE|Braza", "BUTTERFLY|Mariposa", "MIXED|Varios"))
			.detail(new Detail("pool", "Dónde nadas", false, "25M|Piscina de 25 m", "50M|Piscina de 50 m",
				"OPEN_WATER|Aguas abiertas"))
			.detail(new Detail("sessionMeters", "Metros por sesión ahora", true)));
		add(new Builder("football").team().environments(anywhere).resources(light)
			.detail(new Detail("position", "Posición", false, "GOALKEEPER|Portero", "DEFENDER|Defensa",
				"MIDFIELDER|Centrocampista", "FORWARD|Delantero"))
			.detail(new Detail("format", "Modalidad", false, "ELEVEN|Fútbol 11", "SEVEN|Fútbol 7", "FUTSAL|Fútbol sala"))
			.detail(matches));
		add(new Builder("basketball").team().environments(anywhere).resources(light)
			.detail(new Detail("position", "Posición", false, "POINT_GUARD|Base", "SHOOTING_GUARD|Escolta",
				"SMALL_FORWARD|Alero", "POWER_FORWARD|Ala-pívot", "CENTER|Pívot"))
			.detail(matches));
		add(new Builder("hiking").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.MACHINE, Resource.SUPPORT, Resource.MAT, Resource.WALL, Resource.CHAIR)
			.focusLabels("Caminar y subir", "Fuerza y movilidad para montaña").noSpace()
			.detail(new Detail("terrain", "Terreno habitual", false, "FLAT|Senderos llanos",
				"MOUNTAIN|Montaña media", "HIGH|Alta montaña", "TRAIL|Trail running"))
			.detail(new Detail("elevation", "Desnivel habitual por salida (m)", true))
			.detail(new Detail("pack", "Mochila", false, "LIGHT|Ligera", "MEDIUM|Media (5–10 kg)",
				"HEAVY|Pesada (más de 10 kg)")));
		add(new Builder("climbing").sport().environments(anywhere)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY, Goal.SPORT_PRACTICE)
			.resources(Resource.BANDS, Resource.MAT, Resource.DUMBBELLS, Resource.BARBELL, Resource.MACHINE)
			.focusLabels("Fuerza de tracción y agarre", "Preparación complementaria")
			.detail(new Detail("style", "Modalidad", false, "BOULDER|Bloque (boulder)", "SPORT|Deportiva",
				"TRAD|Clásica", "GYM|Rocódromo"))
			.detail(new Detail("grade", "Grado que encadenas (p. ej. 6a)", false)));
		add(new Builder("combat").sport().environments(anywhere)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.SPORT_PRACTICE, Goal.MOBILITY)
			.resources(light).focusLabels("Acondicionamiento de combate", "Preparación complementaria")
			.detail(new Detail("style", "Modalidad", false, "BOXING|Boxeo", "KICKBOXING|Kickboxing / muay thai",
				"MMA|MMA", "GRAPPLING|Judo / BJJ / lucha", "STRIKING|Kárate / taekwondo", "OTHER|Otra"))
			.detail(new Detail("competes", "¿Compites?", false, "NO|No", "AMATEUR|Amateur", "PRO|Profesional")));
		add(new Builder("yoga").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT)
			.goals(Goal.MOBILITY, Goal.GENERAL_FITNESS, Goal.STRENGTH)
			.resources(Resource.MAT, Resource.WALL, Resource.BANDS)
			.detail(new Detail("style", "Estilo", false, "HATHA|Hatha", "VINYASA|Vinyasa", "ASHTANGA|Ashtanga",
				"YIN|Yin / restaurativo", "UNKNOWN|No lo sé"))
			.detail(new Detail("area", "Zona que quieres mejorar", false, "BACK|Espalda", "HIPS|Caderas",
				"SHOULDERS|Hombros", "HAMSTRINGS|Isquiotibiales", "BALANCE|Equilibrio general")));
		add(new Builder("pilates").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT)
			.goals(Goal.STRENGTH, Goal.MOBILITY, Goal.GENERAL_FITNESS)
			.resources(Resource.MAT, Resource.BANDS, Resource.WALL)
			.detail(new Detail("modality", "Modalidad", false, "MAT|Suelo (mat)", "REFORMER|Reformer",
				"BOTH|Ambas"))
			.detail(new Detail("area", "Prioridad", false, "CORE|Core", "POSTURE|Postura", "PELVIC|Suelo pélvico",
				"BACK|Espalda")));
		add(new Builder("padel").team().environments(anywhere).resources(light)
			.detail(new Detail("side", "Lado de juego", false, "RIGHT|Derecha", "LEFT|Revés", "BOTH|Indistinto"))
			.detail(hand).detail(matches));
		add(new Builder("tennis").team().environments(anywhere).resources(light)
			.detail(hand)
			.detail(new Detail("backhand", "Revés", false, "ONE|A una mano", "TWO|A dos manos"))
			.detail(new Detail("surface", "Superficie habitual", false, "CLAY|Tierra batida", "HARD|Pista dura",
				"GRASS|Hierba"))
			.detail(matches));
		add(new Builder("volleyball").team().environments(anywhere).resources(light)
			.detail(new Detail("position", "Posición", false, "SETTER|Colocador", "OPPOSITE|Opuesto",
				"MIDDLE|Central", "OUTSIDE|Receptor", "LIBERO|Líbero"))
			.detail(new Detail("surface", "Modalidad", false, "INDOOR|Pista", "BEACH|Playa"))
			.detail(matches));
		add(new Builder("other").team().environments(anywhere).resources(EnumSet.allOf(Resource.class))
			.detail(new Detail("sportName", "¿Qué disciplina practicas?", false)));
	}

	private static void add(Builder b) {
		FORMS.put(b.sportId, new DisciplineForm(b));
	}

	/**
	 * Formulario de la disciplina; una desconocida del catálogo usa el genérico de «Otra disciplina».
	 */
	public static DisciplineForm of(String sportId) {
		DisciplineForm form = FORMS.get(sportId);
		return form != null ? form : FORMS.get("other");
	}

	public static boolean has(String sportId) {
		return FORMS.containsKey(sportId);
	}

	private static final class Builder {
		final String sportId;
		List<Category> categories = Collections.singletonList(Category.SPORT);
		List<Goal> goals = Arrays.asList(Goal.values());
		List<Environment> environments = Arrays.asList(Environment.values());
		List<Focus> focuses = Collections.singletonList(Focus.SPECIFIC);
		Set<Resource> resources = EnumSet.noneOf(Resource.class), included = EnumSet.noneOf(Resource.class);
		final List<Detail> details = new ArrayList<>();
		String specificLabel = "Específica", complementaryLabel = "Preparación complementaria",
			durationLabel = "Tiempo por sesión";
		boolean asksSpace = true;

		Builder(String sportId) {
			this.sportId = sportId;
		}

		Builder categories(Category... values) {
			return categories(Arrays.asList(values));
		}

		Builder categories(List<Category> values) {
			categories = values;
			return this;
		}

		Builder goals(Goal... values) {
			goals = Arrays.asList(values);
			return this;
		}

		Builder environments(Environment... values) {
			return environments(Arrays.asList(values));
		}

		Builder environments(List<Environment> values) {
			environments = values;
			return this;
		}

		Builder resources(Resource... values) {
			return resources(EnumSet.copyOf(Arrays.asList(values)));
		}

		Builder resources(Set<Resource> values) {
			resources = EnumSet.copyOf(values);
			return this;
		}

		Builder included(Resource... values) {
			included = EnumSet.copyOf(Arrays.asList(values));
			return this;
		}

		/**
		 * Disciplina con práctica propia y preparación complementaria en el catálogo.
		 */
		Builder sport() {
			categories = Collections.singletonList(Category.SPORT);
			focuses = Arrays.asList(Focus.SPECIFIC, Focus.COMPLEMENTARY);
			return this;
		}

		/**
		 * Deporte de equipo o raqueta: solo hay preparación complementaria; la práctica específica está pendiente.
		 */
		Builder team() {
			categories = Collections.singletonList(Category.SPORT);
			focuses = Collections.singletonList(Focus.COMPLEMENTARY);
			goals = Arrays.asList(Goal.SPORT_PRACTICE, Goal.STRENGTH, Goal.MOBILITY, Goal.GENERAL_FITNESS);
			return this;
		}

		Builder focusLabels(String specific, String complementary) {
			specificLabel = specific;
			complementaryLabel = complementary;
			return this;
		}

		Builder duration(String label) {
			durationLabel = label;
			return this;
		}

		Builder noSpace() {
			asksSpace = false;
			return this;
		}

		Builder detail(Detail detail) {
			details.add(detail);
			return this;
		}
	}
}
