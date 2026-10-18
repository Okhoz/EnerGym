package com.energym.app.controller;

import com.energym.app.R;
import com.energym.app.i18n.Txt;
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
		Detail hand = new Detail("hand", Txt.t(R.string.df_mano_dominante), false, "Diestra|" + Txt.t(R.string.df_diestra), "Zurda|" + Txt.t(R.string.df_zurda));
		Detail matches = new Detail("matchesPerWeek", Txt.t(R.string.df_partidos_por_semana), false, "0|" + Txt.t(R.string.df_ninguno), "1|1", "2|2",
			"3|3", "4|" + Txt.t(R.string.df_4_o_mas));

		add(new Builder("strength").categories(gym)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY).resources(weights)
			.detail(new Detail("split", Txt.t(R.string.df_division_que_prefieres), false, "FULL_BODY|" + Txt.t(R.string.df_cuerpo_completo),
				"UPPER_LOWER|" + Txt.t(R.string.df_torso_pierna), "PUSH_PULL_LEGS|" + Txt.t(R.string.df_empuje_tiron_pierna), "BODY_PART|" + Txt.t(R.string.df_por_grupos_musculares)))
			.detail(new Detail("priority", Txt.t(R.string.df_zona_o_ejercicio_prioritario), false)));
		add(new Builder("powerlifting").categories(Category.GYM)
			.goals(Goal.STRENGTH).resources(Resource.BARBELL, Resource.BENCH).noSpace());
		add(new Builder("calisthenics").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT, Category.GYM)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY)
			.resources(Resource.BANDS, Resource.MAT, Resource.CHAIR, Resource.WALL, Resource.SUPPORT, Resource.BENCH)
			.detail(new Detail("skill", Txt.t(R.string.df_habilidad_que_buscas), false, "PULL_UP|" + Txt.t(R.string.df_primera_dominada),
				"DIPS|" + Txt.t(R.string.df_fondos), "HANDSTAND|" + Txt.t(R.string.df_pino), "MUSCLE_UP|" + Txt.t(R.string.df_muscle_up), "L_SIT|" + Txt.t(R.string.df_l_sit), "NONE|" + Txt.t(R.string.df_ninguna_en_concreto)))
			.detail(new Detail("maxPushups", Txt.t(R.string.df_flexiones_seguidas_que_haces), true))
			.detail(new Detail("maxPullups", Txt.t(R.string.df_dominadas_seguidas_que_haces), true)));
		add(new Builder("functional").categories(gym)
			.goals(Goal.STRENGTH, Goal.ENDURANCE, Goal.GENERAL_FITNESS).resources(weights)
			.detail(new Detail("format", Txt.t(R.string.df_formato_que_prefieres), false, "AMRAP|AMRAP", "EMOM|EMOM",
				"FOR_TIME|" + Txt.t(R.string.df_por_tiempo), "CIRCUIT|" + Txt.t(R.string.df_circuito), "MIXED|" + Txt.t(R.string.df_mixto)))
			.detail(new Detail("olympic", Txt.t(R.string.df_experiencia_con_halterofilia), false, "NONE|" + Txt.t(R.string.df_ninguna), "BASIC|" + Txt.t(R.string.df_basica),
				"ADVANCED|" + Txt.t(R.string.df_avanzada))));
		add(new Builder("running").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.MACHINE, Resource.MAT, Resource.WALL, Resource.SUPPORT)
			.focusLabels(Txt.t(R.string.df_correr), Txt.t(R.string.df_fuerza_y_movilidad_para_correr)).noSpace()
			.detail(new Detail("raceGoal", Txt.t(R.string.df_objetivo_de_carrera), false, "START|" + Txt.t(R.string.df_empezar_a_correr), "5K|" + Txt.t(R.string.df_5_km),
				"10K|" + Txt.t(R.string.df_10_km), "HALF|" + Txt.t(R.string.df_media_maraton), "MARATHON|" + Txt.t(R.string.df_maraton), "TRAIL|" + Txt.t(R.string.df_trail)))
			.detail(new Detail("weeklyKm", Txt.t(R.string.df_kilometros_por_semana_ahora), true))
			.detail(new Detail("bestTime", Txt.t(R.string.df_mejor_marca_reciente_opcional), false)));
		add(new Builder("cycling").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.BICYCLE, Resource.MACHINE, Resource.MAT, Resource.WALL)
			.focusLabels(Txt.t(R.string.df_rodar), Txt.t(R.string.df_fuerza_y_movilidad_para_ciclismo)).noSpace()
			.detail(new Detail("bike", Txt.t(R.string.df_tipo_de_bicicleta), false, "ROAD|" + Txt.t(R.string.df_carretera), "MTB|" + Txt.t(R.string.df_montana_mtb),
				"GRAVEL|" + Txt.t(R.string.df_gravel), "INDOOR|" + Txt.t(R.string.df_estatica_o_rodillo), "URBAN|" + Txt.t(R.string.df_urbana)))
			.detail(new Detail("weeklyKm", Txt.t(R.string.df_kilometros_por_semana_ahora), true))
			.detail(new Detail("ftp", Txt.t(R.string.df_ftp_en_vatios_si_lo_conoces), true)));
		add(new Builder("swimming").sport().environments(Environment.POOL)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.POOL, Resource.BANDS, Resource.MAT).included(Resource.POOL)
			.focusLabels(Txt.t(R.string.df_nadar), Txt.t(R.string.df_fuerza_y_movilidad_fuera_del_agua)).noSpace().duration(Txt.t(R.string.df_tiempo_por_sesion))
			.detail(new Detail("stroke", Txt.t(R.string.df_estilo_principal), false, "FREESTYLE|" + Txt.t(R.string.df_crol), "BACKSTROKE|" + Txt.t(R.string.df_backstroke),
				"BREASTSTROKE|" + Txt.t(R.string.df_braza), "BUTTERFLY|" + Txt.t(R.string.df_mariposa), "MIXED|" + Txt.t(R.string.df_varios)))
			.detail(new Detail("pool", Txt.t(R.string.df_donde_nadas), false, "25M|" + Txt.t(R.string.df_piscina_de_25_m), "50M|" + Txt.t(R.string.df_piscina_de_50_m),
				"OPEN_WATER|" + Txt.t(R.string.df_aguas_abiertas)))
			.detail(new Detail("sessionMeters", Txt.t(R.string.df_metros_por_sesion_ahora), true)));
		add(new Builder("football").team().environments(anywhere).resources(light)
			.detail(new Detail("position", Txt.t(R.string.df_posicion), false, "GOALKEEPER|" + Txt.t(R.string.df_portero), "DEFENDER|" + Txt.t(R.string.df_defensa),
				"MIDFIELDER|" + Txt.t(R.string.df_centrocampista), "FORWARD|" + Txt.t(R.string.df_delantero)))
			.detail(new Detail("format", Txt.t(R.string.df_modalidad), false, "ELEVEN|" + Txt.t(R.string.df_futbol_11), "SEVEN|" + Txt.t(R.string.df_futbol_7), "FUTSAL|" + Txt.t(R.string.df_futbol_sala)))
			.detail(matches));
		add(new Builder("basketball").team().environments(anywhere).resources(light)
			.detail(new Detail("position", Txt.t(R.string.df_posicion), false, "POINT_GUARD|" + Txt.t(R.string.df_base), "SHOOTING_GUARD|" + Txt.t(R.string.df_escolta),
				"SMALL_FORWARD|" + Txt.t(R.string.df_alero), "POWER_FORWARD|" + Txt.t(R.string.df_ala_pivot), "CENTER|" + Txt.t(R.string.df_pivot)))
			.detail(matches));
		add(new Builder("hiking").sport().environments(Environment.OUTDOORS, Environment.GYM)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.MOBILITY)
			.resources(Resource.MACHINE, Resource.SUPPORT, Resource.MAT, Resource.WALL, Resource.CHAIR)
			.focusLabels(Txt.t(R.string.df_caminar_y_subir), Txt.t(R.string.df_fuerza_y_movilidad_para_montana)).noSpace()
			.detail(new Detail("terrain", Txt.t(R.string.df_terreno_habitual), false, "FLAT|" + Txt.t(R.string.df_senderos_llanos),
				"MOUNTAIN|" + Txt.t(R.string.df_montana_media), "HIGH|" + Txt.t(R.string.df_alta_montana), "TRAIL|" + Txt.t(R.string.df_trail_running)))
			.detail(new Detail("elevation", Txt.t(R.string.df_desnivel_habitual_por_salida_m), true))
			.detail(new Detail("pack", Txt.t(R.string.df_mochila), false, "LIGHT|" + Txt.t(R.string.df_ligera), "MEDIUM|" + Txt.t(R.string.df_media_510_kg),
				"HEAVY|" + Txt.t(R.string.df_pesada_mas_de_10_kg))));
		add(new Builder("climbing").sport().environments(anywhere)
			.goals(Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.MOBILITY, Goal.SPORT_PRACTICE)
			.resources(Resource.BANDS, Resource.MAT, Resource.DUMBBELLS, Resource.BARBELL, Resource.MACHINE)
			.focusLabels(Txt.t(R.string.df_fuerza_de_traccion_y_agarre), Txt.t(R.string.df_preparacion_complementaria))
			.detail(new Detail("style", Txt.t(R.string.df_modalidad), false, "BOULDER|" + Txt.t(R.string.df_bloque_boulder), "SPORT|" + Txt.t(R.string.df_deportiva),
				"TRAD|" + Txt.t(R.string.df_clasica), "GYM|" + Txt.t(R.string.df_rocodromo)))
			.detail(new Detail("grade", Txt.t(R.string.df_grado_que_encadenas_p_ej_6a), false)));
		add(new Builder("combat").sport().environments(anywhere)
			.goals(Goal.ENDURANCE, Goal.GENERAL_FITNESS, Goal.STRENGTH, Goal.SPORT_PRACTICE, Goal.MOBILITY)
			.resources(light).focusLabels(Txt.t(R.string.df_acondicionamiento_de_combate), Txt.t(R.string.df_preparacion_complementaria))
			.detail(new Detail("style", Txt.t(R.string.df_modalidad), false, "BOXING|" + Txt.t(R.string.df_boxeo), "KICKBOXING|" + Txt.t(R.string.df_kickboxing_muay_thai),
				"MMA|MMA", "GRAPPLING|" + Txt.t(R.string.df_judo_bjj_lucha), "STRIKING|" + Txt.t(R.string.df_karate_taekwondo), "OTHER|" + Txt.t(R.string.df_otra)))
			.detail(new Detail("competes", Txt.t(R.string.df_compites), false, "NO|" + Txt.t(R.string.df_no), "AMATEUR|" + Txt.t(R.string.df_amateur), "PRO|" + Txt.t(R.string.df_profesional))));
		add(new Builder("yoga").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT)
			.goals(Goal.MOBILITY, Goal.GENERAL_FITNESS, Goal.STRENGTH)
			.resources(Resource.MAT, Resource.WALL, Resource.BANDS)
			.detail(new Detail("style", Txt.t(R.string.df_estilo), false, "HATHA|" + Txt.t(R.string.df_hatha), "VINYASA|" + Txt.t(R.string.df_vinyasa), "ASHTANGA|" + Txt.t(R.string.df_ashtanga),
				"YIN|" + Txt.t(R.string.df_yin_restaurativo), "UNKNOWN|" + Txt.t(R.string.df_no_lo_se)))
			.detail(new Detail("area", Txt.t(R.string.df_zona_que_quieres_mejorar), false, "BACK|" + Txt.t(R.string.df_back), "HIPS|" + Txt.t(R.string.df_caderas),
				"SHOULDERS|" + Txt.t(R.string.df_hombros), "HAMSTRINGS|" + Txt.t(R.string.df_isquiotibiales), "BALANCE|" + Txt.t(R.string.df_equilibrio_general))));
		add(new Builder("pilates").categories(Category.NO_EQUIPMENT, Category.HOME_EQUIPMENT)
			.goals(Goal.STRENGTH, Goal.MOBILITY, Goal.GENERAL_FITNESS)
			.resources(Resource.MAT, Resource.BANDS, Resource.WALL)
			.detail(new Detail("modality", Txt.t(R.string.df_modalidad), false, "MAT|" + Txt.t(R.string.df_suelo_mat), "REFORMER|" + Txt.t(R.string.df_reformer),
				"BOTH|" + Txt.t(R.string.df_ambas)))
			.detail(new Detail("area", Txt.t(R.string.df_prioridad), false, "CORE|" + Txt.t(R.string.df_core), "POSTURE|" + Txt.t(R.string.df_postura), "PELVIC|" + Txt.t(R.string.df_suelo_pelvico),
				"BACK|" + Txt.t(R.string.df_back))));
		add(new Builder("padel").team().environments(anywhere).resources(light)
			.detail(new Detail("side", Txt.t(R.string.df_lado_de_juego), false, "RIGHT|" + Txt.t(R.string.df_side_right), "LEFT|" + Txt.t(R.string.df_side_left), "BOTH|" + Txt.t(R.string.df_indistinto)))
			.detail(hand).detail(matches));
		add(new Builder("tennis").team().environments(anywhere).resources(light)
			.detail(hand)
			.detail(new Detail("backhand", Txt.t(R.string.df_backhand), false, "ONE|" + Txt.t(R.string.df_a_una_mano), "TWO|" + Txt.t(R.string.df_a_dos_manos)))
			.detail(new Detail("surface", Txt.t(R.string.df_superficie_habitual), false, "CLAY|" + Txt.t(R.string.df_tierra_batida), "HARD|" + Txt.t(R.string.df_pista_dura),
				"GRASS|" + Txt.t(R.string.df_hierba)))
			.detail(matches));
		add(new Builder("volleyball").team().environments(anywhere).resources(light)
			.detail(new Detail("position", Txt.t(R.string.df_posicion), false, "SETTER|" + Txt.t(R.string.df_colocador), "OPPOSITE|" + Txt.t(R.string.df_opuesto),
				"MIDDLE|" + Txt.t(R.string.df_central), "OUTSIDE|" + Txt.t(R.string.df_receptor), "LIBERO|" + Txt.t(R.string.df_libero)))
			.detail(new Detail("surface", Txt.t(R.string.df_modalidad), false, "INDOOR|" + Txt.t(R.string.df_pista), "BEACH|" + Txt.t(R.string.df_playa)))
			.detail(matches));
		add(new Builder("other").team().environments(anywhere).resources(EnumSet.allOf(Resource.class))
			.detail(new Detail("sportName", Txt.t(R.string.df_que_disciplina_practicas), false)));
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
		String specificLabel = Txt.t(R.string.df_especifica), complementaryLabel = Txt.t(R.string.df_preparacion_complementaria),
			durationLabel = Txt.t(R.string.df_tiempo_por_sesion);
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
