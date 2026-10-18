package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.controller.*;
import com.energym.app.domain.*;

import java.util.*;
import java.util.function.*;
import java.text.Normalizer;
import java.util.regex.*;

/**
 * CONTROLADOR JVM: reglas locales, correlación y decisiones. No conoce vistas Android ni realiza SQL.
 */
public final class ProposalController {
	public Routine preview, accepted;
	public Exercise exercise;
	public boolean busy;
	public String status = "", composer = "", endpoint = "", pairingCode = "";
	public final List<ConversationStore.Message> messages = new ArrayList<>();
	public boolean showHistory, showConnection;
	// El servidor respondió que su IA no está activada: release requiere elegir el guiado local explícitamente.
	private boolean remoteDisabled;
	private boolean localGuidance;
	private Boolean conversationHistoryConsent;
	private Boolean lastProfileHistoryConsent;
	private boolean planningDialogue, pendingHistory;
	/** Lo pedido durante el diálogo de planificación («piernas», «suave»…) aunque llegue en varios mensajes. */
	private String planningAsk = "";
	private final AppStore store;
	private final ConversationStore conversations;
	private final ChatClient client;
	private final List<Exercise> catalog;
	private final Supplier<TrainingProfile> current;
	private final Runnable changed;
	private final Consumer<Routine> onAccepted;
	private Consumer<OpenExercise> command = e -> {
	};
	private java.util.function.LongSupplier navigationOrigin = () -> -1;
	private String owner, conversationId;
	private long generation;
	private TrainingProfile base, previewBase;
	private final Map<String, String> fields = new LinkedHashMap<>();
	private final int catalogVersion;

	public ProposalController(AppStore app, ConversationStore conversations, ChatClient client, List<Exercise> catalog,
		Supplier<TrainingProfile> profile, Runnable changed, Consumer<Routine> accepted) {
		this(app, conversations, client, catalog, 1, profile, changed, accepted);
	}

	public ProposalController(AppStore app, ConversationStore conversations, ChatClient client, List<Exercise> catalog,
		int version, Supplier<TrainingProfile> profile, Runnable changed,
		Consumer<Routine> accepted) {
		store = app;
		this.conversations = conversations;
		this.client = client;
		this.catalog = catalog;
		catalogVersion = version;
		current = profile;
		this.changed = changed;
		onAccepted = accepted;
	}

	/**
	 * Cambiar cuenta invalida el cliente y sus callbacks; cada cuenta recupera únicamente su conversación local.
	 */
	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		generation++;
		client.activate(next);
		owner = next;
		clearPreview();
		accepted = null;
		exercise = null;
		busy = false;
		status = "";
		composer = "";
		pairingCode = "";
		endpoint = "";
		showHistory = false;
		showConnection = false;
		remoteDisabled = false;
		localGuidance = false;
		conversationHistoryConsent = null;
		lastProfileHistoryConsent = null;
		planningDialogue = false;
		pendingHistory = false;
		fields.clear();
		messages.clear();
		base = null;
		conversationId = UUID.randomUUID().toString();
		if (owner != null) {
			ConversationStore.State s = conversations.load(owner);
			if (s != null) {
				conversationId = s.conversationId;
				messages.addAll(s.messages);
				fields.putAll(s.fields);
				preview = s.preview;
				previewAnchor = Integer.MAX_VALUE;
				previewBase = s.previewBase;
				base = s.base;
				exercise = s.exercise;
			}
			if (messages.isEmpty())
				add("assistant",
					Txt.t(R.string.pc_welcome));
		}
		changed.run();
	}

	/**
	 * Vincula cada propuesta al perfil vigente en su creación; resembrar el diálogo nunca cambia este CAS.
	 */
	private void setPreview(Routine value, TrainingProfile expected) {
		preview = value == null ? null : carryOver.apply(value);
		previewBase = expected;
		previewAnchor = messages.size();
		swapped.clear();
	}

	/**
	 * Número de mensajes que había cuando llegó la propuesta: el chat la dibuja justo debajo de la respuesta que
	 * la generó, y lo que se hable después aparece debajo de la tarjeta (no encima).
	 */
	public int previewAnchor = Integer.MAX_VALUE;

	private Supplier<String> training = () -> "";

	/**
	 * Resumen del entrenamiento que recibe el entrenador del móvil (rutina activa, sesiones, progresión).
	 */
	public void trainingContext(Supplier<String> summary) {
		training = summary == null ? () -> "" : summary;
	}

	private java.util.function.Function<String, String> weeklyPlan;

	/**
	 * Plan semanal estilo FitNotes: recibe lo pedido, prepara una rutina por día para revisar y devuelve la
	 * respuesta del entrenador. Sin esto, la petición sigue como una rutina normal.
	 */
	public void weeklyPlan(java.util.function.Function<String, String> planner) {
		weeklyPlan = planner;
	}

	private java.util.function.UnaryOperator<Routine> carryOver = r -> r;

	/** Ajusta una propuesta nueva a lo último que la persona registró (sin reiniciar cargas ya conseguidas). */
	public void carryOver(java.util.function.UnaryOperator<Routine> adjust) {
		carryOver = adjust == null ? r -> r : adjust;
	}

	private void clearPreview() {
		preview = null;
		previewBase = null;
		swapped.clear();
	}

	/**
	 * Ejercicios que ya se han mostrado en cada posición de la propuesta: pulsar «cambiar» varias veces recorre
	 * alternativas distintas en vez de alternar entre dos.
	 */
	private final Map<Integer, Set<String>> swapped = new HashMap<>();

	public boolean connected() {
		return client.connected();
	}

	public boolean automaticConnection() {
		return client.automaticConnection();
	}

	/**
	 * La IA corre en el propio móvil (Gemini Nano): sin servidor, sin coste y sin enviar mensajes fuera.
	 */
	public boolean onDevice() {
		return client.onDevice();
	}

	public String modelLabel() {
		return client.modelLabel();
	}

	public String downloadOffer() {
		return client.downloadOffer();
	}

	/**
	 * Descarga del modelo pedida por el usuario; el chat sigue con el guiado local mientras tanto.
	 */
	public void downloadModel() {
		client.startDownload();
		status = connectionNotice();
		changed.run();
	}

	public boolean localGuidance() {
		return localGuidance;
	}

	public String connectionNotice() {
		String unavailable = client.unavailableCode();
		if (client.onDevice()) {
			String model = client.modelLabel();
			int progress = client.downloadProgress();
			if (unavailable.equals("MODEL_NEEDS_DOWNLOAD"))
				return Txt.t(R.string.pc_model_needs_download, model);
			if (unavailable.equals("MODEL_DOWNLOAD_FAILED"))
				return Txt.t(R.string.pc_model_download_failed, model);
			if (unavailable.equals("MODEL_DOWNLOADING"))
				return Txt.t(R.string.pc_model_downloading, model, progress >= 0 ? ", " + progress + " %" : "");
			if (unavailable.equals("MODEL_CHECKING"))
				return Txt.t(R.string.pc_model_checking);
			if (unavailable.equals("MODEL_UNAVAILABLE"))
				return Txt.t(R.string.pc_model_unavailable);
			return Txt.t(R.string.pc_model_ready, model);
		}
		if (unavailable.equals("BACKEND_NOT_CONFIGURED"))
			return Txt.t(R.string.pc_online_not_configured);
		if (unavailable.equals("CONFIGURACION_INVALIDA"))
			return Txt.t(R.string.pc_online_invalid);
		if (unavailable.equals("SIGN_IN_REQUIRED"))
			return Txt.t(R.string.pc_online_sign_in);
		if (remoteDisabled)
			return Txt.t(R.string.pc_online_disabled);
		return Txt.t(R.string.pc_online_ready);
	}

	/** Cambiar de modo es una elección explícita; no envía mensajes ni reintenta una petición. */
	public void selectLocalGuidance(boolean enabled) {
		if (!automaticConnection() || busy)
			return;
		localGuidance = enabled;
		if (!enabled)
			remoteDisabled = false;
		status = enabled ? Txt.t(R.string.pc_local_selected) : connectionNotice();
		changed.run();
	}

	/** La cancelación retira la espera local y descarta la respuesta; no vuelve a enviar el mensaje. */
	public void cancelPending() {
		if (!busy)
			return;
		generation++;
		busy = false;
		pendingHistory = false;
		client.cancelPending();
		status = Txt.t(R.string.pc_cancelled);
		changed.run();
	}

	/** El permiso del chat es explícito, sólo en memoria y no modifica el perfil ni sus otros datos. */
	public boolean historyContextEnabled() {
		TrainingProfile profile = current.get();
		boolean saved = profile != null && profile.isHistoryConsent();
		// Revocar después en el perfil invalida también un permiso anterior del chat.
		if (Boolean.TRUE.equals(lastProfileHistoryConsent) && !saved)
			conversationHistoryConsent = false;
		lastProfileHistoryConsent = saved;
		return conversationHistoryConsent == null ? saved :
			conversationHistoryConsent;
	}

	public void historyContext(boolean enabled) {
		historyContextEnabled();
		conversationHistoryConsent = enabled;
		if (!enabled && busy && pendingHistory) {
			generation++;
			client.cancelPending();
			busy = false;
			pendingHistory = false;
			status = Txt.t(R.string.pc_context_off);
		}
		changed.run();
	}

	/**
	 * El borrado local revoca primero el contexto para que ninguna respuesta repueble la conversación.
	 */
	public void clearLocalData() {
		String previous = owner;
		activate(null);
		if (previous != null)
			conversations.clear(previous);
	}

	/**
	 * Eventos de edición sin render por tecla: el compositor conserva foco e IME.
	 */
	public void messageInput(String text) {
		composer = text;
	}

	public void endpointInput(String text) {
		endpoint = text;
	}

	public void pairingInput(String text) {
		pairingCode = text;
	}

	public void toggleHistory() {
		showHistory = !showHistory;
		changed.run();
	}

	public void toggleConnection() {
		showConnection = !showConnection;
		changed.run();
	}

	private boolean ready(String text) {
		if (owner == null) {
			status = Txt.t(R.string.pc_sign_in_to_keep);
			changed.run();
			return false;
		}
		if (busy)
			return false;
		if (text == null || text.trim().isEmpty() || text.length() > 2000) {
			status = Txt.t(R.string.pc_message_length);
			changed.run();
			return false;
		}
		status = "";
		composer = "";
		return true;
	}

	private void add(String role, String text) {
		// La explicación que acompaña a una propuesta recién creada va encima de su tarjeta, no debajo.
		if (preview != null && role.equals("assistant") && previewAnchor == messages.size())
			previewAnchor++;
		messages.add(new ConversationStore.Message(role, text));
		int size = 0;
		for (ConversationStore.Message m : messages)
			size += m.text.length();
		while (messages.size() > 48 || size > 16000) {
			size -= messages.remove(0).text.length();
			if (previewAnchor != Integer.MAX_VALUE && previewAnchor > 0)
				previewAnchor--;
		}
	}

	private void addRemote(String text) {
		add("assistant", text);
		messages.set(messages.size() - 1, new ConversationStore.Message("assistant", text, "remote"));
	}

	private void persist() {
		if (owner != null)
			conversations.save(owner,
				new ConversationStore.State(conversationId, messages, fields, preview, base, exercise, previewBase));
	}

	public void sendLocal(String raw) {
		if (!ready(raw))
			return;
		String text = normalize(raw);
		add("user", raw.trim());
		boolean planning = planningTurn(text);
		// Con la IA del móvil la cabecera ya explica el modo: no se repite el aviso bajo cada respuesta.
		status = client.onDevice() ? "" :
			automaticConnection() ? Txt.t(R.string.pc_local_selected_short) : Txt.t(R.string.pc_local_connect);
		if (exercise != null && !planning && !greeting(text)) {
			add("assistant", explanation(exercise));
			persist();
			changed.run();
			return;
		}
		if (exercise != null) {
			generation++;
			exercise = null;
		}
		if (fields.isEmpty() || !Objects.equals(base, current.get())) {
			fields.clear();
			base = current.get();
			if (base != null)
				seed(base);
		}
		String conflict = planning ? conflict(text) : "";
		if (!conflict.isEmpty()) {
			clearPreview();
			status = conflict;
			persist();
			changed.run();
			return;
		}
		if (text.contains("sustitu") || text.contains("cambiar ejercicio")) {
			substituteFirst();
			return;
		}
		if (!planning) {
			String response;
			if (greeting(text))
				response = Txt.t(R.string.pc_hello);
			else if (text.contains("constan") || text.contains("habito") || text.contains("motiv"))
				response = Txt.t(R.string.pc_habit);
			else if (text.contains("cans") || text.contains("descans"))
				response = Txt.t(R.string.pc_rest);
			else if (techniqueReply(raw) != null)
				response = techniqueReply(raw);
			else
				response = Txt.t(R.string.pc_help) +
					(client.onDevice() ? "" : " " + Txt.t(R.string.pc_help_local));
			add("assistant", response);
			persist();
			changed.run();
			return;
		}
		planningAsk = planningDialogue ? (planningAsk + " " + raw).trim() : raw;
		planningDialogue = true;
		parse(text);
		List<String> missing = missing();
		if (!missing.isEmpty()) {
			add("assistant", Txt.t(R.string.pc_missing, String.join(", ", missing)));
			clearPreview();
		} else {
			try {
				TrainingProfile proposed = build();
				setPreview(new RoutinePlanner().create(proposed, catalog, RoutineRequest.parse(planningAsk),
					System.nanoTime()), base);
				planningDialogue = false;
				add("assistant", preview.isEmpty() ? preview.getExplanation() :
					Txt.t(R.string.pc_preview_ready, proposed.getDaysPerWeek(), proposed.getDurationMinutes(),
						AppController.label(proposed.getEnvironment().name())));
			} catch (RuntimeException e) {
				clearPreview();
				status = Txt.t(R.string.pc_check_days);
			}
		}
		persist();
		changed.run();
	}

	private void seed(TrainingProfile p) {
		fields.put("goal", p.getGoal().name());
		fields.put("sport", p.getSportId());
		fields.put("environment", p.getEnvironment().name());
		fields.put("category", p.getCategory().name());
		fields.put("days", String.valueOf(p.getDaysPerWeek()));
		fields.put("duration", String.valueOf(p.getDurationMinutes()));
		fields.put("level", p.getLevel());
		List<String> rs = new ArrayList<>();
		for (TrainingProfile.Resource r : p.getResources())
			rs.add(r.name());
		fields.put("resources", String.join(",", rs));
	}

	/**
	 * Palabras (ya sin tildes) que nombran una disciplina del catálogo; la primera coincidencia la fija.
	 */
	private static final Map<String, String> DISCIPLINES = new LinkedHashMap<>();

	static {
		String[][] words = {{"calistenia", "calisthenics"}, {"crossfit", "functional"}, {"funcional", "functional"},
			{"natacion", "swimming"}, {"nadar", "swimming"}, {"ciclismo", "cycling"}, {"bici", "cycling"},
			{"futbol", "football"}, {"baloncesto", "basketball"}, {"basket", "basketball"}, {"padel", "padel"},
			{"tenis", "tennis"}, {"voleibol", "volleyball"}, {"voley", "volleyball"}, {"boxeo", "combat"},
			{"artes marciales", "combat"}, {"kickboxing", "combat"}, {"muay thai", "combat"}, {"mma", "combat"},
			{"yoga", "yoga"}, {"pilates", "pilates"}, {"senderismo", "hiking"}, {"montana", "hiking"},
			{"escalada", "climbing"}, {"escalar", "climbing"}};
		for (String[] w : words)
			DISCIPLINES.put(w[0], w[1]);
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private static final Pattern PLANNING_WORDS = Pattern.compile("\\b(?:rutinas?|plan(?:es)?|entrenamientos?)\\b");

	private static boolean planningIntent(String text) {
		return PLANNING_WORDS.matcher(text).find();
	}

	private static boolean greeting(String text) {
		return text.matches("\\s*(?:hola|buenos dias|buenas tardes|buenas noches|gracias|que tal)[\\s,!.¿?¡]*.*");
	}

	private boolean planningTurn(String text) {
		boolean namedPlan = planningIntent(text);
		if (namedPlan && Pattern.compile(
			"\\bno\\s+(?:quiero|necesito|prepares?|crees?|hagas?|generes?|me\\s+(?:prepares?|hagas?|crees?))\\b")
			.matcher(text).find()) {
			planningDialogue = false;
			return false;
		}
		// Preguntar qué significa una rutina no autoriza a crearla ni a adjuntar sus datos privados.
		if (namedPlan && Pattern.compile("\\b(?:que\\s+(?:es|son|significa)|por que|definicion|entender|entiendo|" +
			"como\\s+(?:se\\s+)?(?:hace|hacer|crea|crear|prepara|preparar|adapta|adaptar|ajusta|ajustar))\\b")
			.matcher(text).find())
			return false;
		// El verbo debe pedir el plan como objeto: «necesito descansar» o «dime por qué» no lo hacen.
		String planObject = "(?:(?:un|una|el|la|mi|mis|otro|otra|nuevo|nueva|este|esta)\\s+){0,3}" +
			"(?:rutinas?|plan(?:es)?|entrenamientos?)\\b";
		boolean request = Pattern.compile("\\b(?:prepar[ae]\\w*|cre[ae]\\w*|haz\\w*|haces?|disen[ae]\\w*|" +
			"gener[ae]\\w*|ajust[ae]\\w*|modifi[qc]\\w*|quiero|necesito|dame|recomiend[ae]\\w*|propon\\w*|elabor[ae]\\w*)\\s+" +
			planObject).matcher(text).find();
		request |= Pattern.compile("\\b(?:puedes|podrias)\\s+(?:hacer(?:me)?|preparar(?:me)?|crear(?:me)?|darme|" +
			"proponer(?:me)?|disenar(?:me)?)\\s+" + planObject).matcher(text).find();
		request |= Pattern.compile("^\\s*(?:un\\s+|una\\s+)?(?:plan|rutina)\\s+para\\s+mi\\b").matcher(text).find();
		boolean parameters = !text.contains("?") && Pattern.compile(
			"\\b(?:[0-9]+|uno|dos|tres|cuatro|cinco|seis|siete)\\s*(?:dias?|veces|min(?:utos?)?)\\b")
			.matcher(text).find();
		boolean answer = planningDialogue && !text.contains("?") && Pattern.compile(
			"\\b(?:fuerza|resistencia|movilidad|principiante|intermedio|avanzado|casa|gimnasio|sin|material|equipo|minutos|dias)\\b")
			.matcher(text).find();
		return request || (planningDialogue && parameters) || answer;
	}

	private Map<String, Object> conversationalProfile(TrainingProfile profile) {
		Map<String, Object> data = new LinkedHashMap<>(ChatProtocol.profile(profile));
		for (String key : Arrays.asList("experience", "space", "preferences", "limitations", "details"))
			data.remove(key);
		return data;
	}

	private static String conflict(String text) {
		if (text.contains("casa") && text.contains("gimnasio"))
			return Txt.t(R.string.pc_clarify_place);
		if (text.contains("sin material") || text.contains("sin equipo"))
			for (String resource : Arrays.asList("mancuern", "barra", "banda", "esterilla", "banco", "silla", "pared",
				"maquina"))
				if (text.contains(resource) &&
					!Pattern.compile("(?:no (?:tengo|hay|uso|dispongo de)|sin)\\s+(?:\\w+\\s+){0,2}" + resource)
						.matcher(text).find())
					return Txt.t(R.string.pc_clarify_material);
		return "";
	}

	private void parse(String text) {
		String t = text;
		String[] words = {"uno", "dos", "tres", "cuatro", "cinco", "seis", "siete"};
		for (int i = 0; i < words.length; i++)
			t = t.replaceAll("\\b" + words[i] + "\\b", Integer.toString(i + 1));
		Matcher days = Pattern.compile("\\b([0-9]{1,3})\\s*(?:dias?|veces)").matcher(t);
		if (days.find())
			fields.put("days", days.group(1));
		Matcher time = Pattern.compile("\\b([0-9]{1,3})\\s*(?:min(?:utos?)?)\\b").matcher(t);
		if (time.find())
			fields.put("duration", time.group(1));
		if (t.contains("casa"))
			fields.put("environment", "HOME");
		else if (t.contains("gimnasio")) {
			fields.put("environment", "GYM");
			fields.put("category", "GYM");
		} else if (t.contains("exterior") || t.contains("aire libre"))
			fields.put("environment", "OUTDOORS");
		if (t.contains("sin material") || t.contains("sin equipo") || t.contains("peso corporal")) {
			fields.put("category", "NO_EQUIPMENT");
			fields.put("resources", "");
		} else {
			Map<String, String> names =
				StrictJson.map("mancuern", "DUMBBELLS", "barra", "BARBELL", "banda", "BANDS", "esterilla", "MAT",
					"banco", "BENCH", "silla", "CHAIR", "pared", "WALL", "maquina", "MACHINE");
			Set<String> rs = new LinkedHashSet<>();
			boolean negative = false;
			for (Map.Entry<String, String> r : names.entrySet())
				if (t.contains(r.getKey())) {
					boolean negated =
						Pattern.compile("(?:no (?:tengo|hay|uso|dispongo de)|sin)\\s+(?:\\w+\\s+){0,2}" + r.getKey())
							.matcher(t).find();
					if (negated)
						negative = true;
					else
						rs.add(r.getValue());
				}
			if (!rs.isEmpty()) {
				fields.put("resources", String.join(",", rs));
				fields.put("category",
					fields.getOrDefault("environment", "HOME").equals("GYM") ? "GYM" : "HOME_EQUIPMENT");
			} else if (negative)
				fields.remove("resources");
		}
		// El objetivo no cambia la disciplina guardada: solo la elige si aún no hay ninguna.
		if (t.contains("fuerza")) {
			fields.put("goal", "STRENGTH");
			fields.putIfAbsent("sport", "strength");
		} else if (t.contains("movilidad")) {
			fields.put("goal", "MOBILITY");
			fields.putIfAbsent("sport", "strength");
		} else if (t.contains("condicion") || t.contains("estar en forma")) {
			fields.put("goal", "GENERAL_FITNESS");
			fields.putIfAbsent("sport", "strength");
		} else if (t.contains("correr") || t.contains("running")) {
			fields.put("goal", "ENDURANCE");
			fields.put("sport", "running");
		} else if (t.contains("resistencia")) {
			fields.put("goal", "ENDURANCE");
			fields.putIfAbsent("sport", "running");
		}
		for (Map.Entry<String, String> d : DISCIPLINES.entrySet())
			if (t.contains(d.getKey())) {
				fields.put("sport", d.getValue());
				break;
			}
		if (t.contains("principiante") || t.contains("empiezo"))
			fields.put("level", "Principiante");
		else if (t.contains("intermedio"))
			fields.put("level", "Intermedio");
		else if (t.contains("avanzado"))
			fields.put("level", "Avanzado");
	}

	private List<String> missing() {
		List<String> m = new ArrayList<>();
		String[][] keys = {{"days", Txt.t(R.string.pc_missing_days)}, {"duration", Txt.t(R.string.pc_missing_duration)},
			{"environment", Txt.t(R.string.pc_missing_place)}, {"resources", Txt.t(R.string.pc_missing_material)},
			{"goal", Txt.t(R.string.pc_missing_goal)}, {"level", Txt.t(R.string.pc_missing_level)}};
		for (String[] k : keys)
			if (!fields.containsKey(k[0]))
				m.add(k[1]);
		return m;
	}

	private TrainingProfile build() {
		Set<TrainingProfile.Resource> rs = EnumSet.noneOf(TrainingProfile.Resource.class);
		for (String r : fields.getOrDefault("resources", "").split(","))
			if (!r.isEmpty())
				rs.add(TrainingProfile.Resource.valueOf(r));
		TrainingProfile.Builder b = base == null ? new TrainingProfile.Builder() : base.toBuilder();
		String sport = fields.getOrDefault("sport", "strength");
		// Los datos propios de la disciplina guardada no se aplican si en el chat se pide otra.
		if (base != null && !base.getSportId().equals(sport))
			b.details(Collections.emptyMap());
		return b.sportId(sport)
			.goal(TrainingProfile.Goal.valueOf(fields.get("goal")))
			.environment(TrainingProfile.Environment.valueOf(fields.get("environment")))
			.category(TrainingProfile.Category.valueOf(fields.getOrDefault("category", "NO_EQUIPMENT"))).resources(rs)
			.daysPerWeek(Integer.parseInt(fields.get("days"))).durationMinutes(Integer.parseInt(fields.get("duration")))
			.level(fields.get("level")).historyConsent(current.get() != null && current.get().isHistoryConsent())
			.build();
	}

	/**
	 * El perfil parcial mantiene los datos de esta conversación aunque el usuario no autorice enviar mensajes previos.
	 */
	private Map<String, Object> partialProfile() {
		Map<String, Object> data = new LinkedHashMap<>();
		Map<String, String> mapping =
			StrictJson.map("days", "daysPerWeek", "duration", "durationMinutes", "environment", "environment",
				"category", "category", "goal", "goal", "level", "level", "sport", "sportId");
		for (Map.Entry<String, String> k : mapping.entrySet())
			if (fields.containsKey(k.getKey()))
				data.put(k.getValue(), k.getKey().equals("days") || k.getKey().equals("duration") ?
					Integer.parseInt(fields.get(k.getKey())) : fields.get(k.getKey()));
		if (fields.containsKey("resources")) {
			List<String> rs = new ArrayList<>();
			for (String resource : fields.get("resources").split(","))
				if (!resource.isEmpty())
					rs.add(resource);
			data.put("resources", Collections.unmodifiableList(rs));
		}
		return data;
	}

	private boolean sameExercise(Exercise a, Exercise b) {
		return b != null && a.getName().equals(b.getName()) && a.getDescription().equals(b.getDescription()) &&
			a.getMetricType() == b.getMetricType() && a.getRequiredResources().equals(b.getRequiredResources()) &&
			a.getGoals().equals(b.getGoals()) && a.getEnvironments().equals(b.getEnvironments()) &&
			a.getSportIds().equals(b.getSportIds()) && a.getCoverage() == b.getCoverage();
	}

	/**
	 * Conserva el objeto seleccionado, incluso cuando un catálogo futuro reutilice su ID.
	 */
	public void explain(Exercise snapshot) {
		generation++;
		busy = false;
		exercise = Objects.requireNonNull(snapshot);
		clearPreview();
		add("assistant", explanation(snapshot));
		persist();
		changed.run();
	}

	/**
	 * Respuesta de técnica cuando el mensaje nombra un ejercicio del catálogo: el nombre completo lo identifica;
	 * con solo la palabra base («plancha») y varias variantes, se enumeran para que la persona elija.
	 */
	private String techniqueReply(String raw) {
		String text = " " + com.energym.app.catalog.ExerciseSearch.normalize(raw) + " ";
		Exercise named = null;
		for (Exercise e : catalog) {
			String name = com.energym.app.catalog.ExerciseSearch.normalize(e.getName());
			if (!name.isEmpty() && text.contains(" " + name + " ") &&
				(named == null || name.length() > named.getName().length()))
				named = e;
		}
		if (named != null)
			return explanation(named);
		List<Exercise> family = new ArrayList<>();
		for (Exercise e : catalog) {
			String first = com.energym.app.catalog.ExerciseSearch.normalize(e.getName()).split(" ")[0];
			if (first.length() >= 5 && (text.contains(" " + first + " ") || text.contains(" " + first + "s ")))
				family.add(e);
		}
		if (family.isEmpty())
			return null;
		if (family.size() == 1)
			return explanation(family.get(0));
		family.sort(Comparator.<Exercise>comparingInt(e -> e.getRequiredResources().size())
			.thenComparing(Exercise::getName));
		List<String> names = new ArrayList<>();
		for (Exercise e : family.subList(0, Math.min(8, family.size())))
			names.add(e.getName());
		String base = family.get(0).getName().split(" ")[0];
		return Txt.t(R.string.pc_variants, family.size(), base.toLowerCase(Locale.ROOT),
			String.join(", ", names) + (family.size() > names.size() ? " " + Txt.t(R.string.pc_and_more) : ""),
			Collections.min(names, Comparator.comparingInt(String::length)).toLowerCase(Locale.ROOT));
	}

	private String explanation(Exercise e) {
		return Txt.t(R.string.pc_technique, e.getName(), e.getDescription(),
			e.getRequiredResources().isEmpty() ? Txt.t(R.string.app_none) : AppController.labels(e.getRequiredResources()));
	}

	public void reject() {
		if (busy)
			return;
		clearPreview();
		add("assistant", Txt.t(R.string.pc_rejected));
		persist();
		changed.run();
	}

	/**
	 * Compara el perfil leído; el almacén repite la comparación dentro de su transacción.
	 */
	public void accept() {
		if (busy || preview == null)
			return;
		if (!Objects.equals(previewBase, current.get()) || !preview.isValidFor(preview.getProfile())) {
			status = Txt.t(R.string.pc_profile_changed);
			changed.run();
			return;
		}
		busy = true;
		long token = ++generation;
		Routine proposed = preview;
		TrainingProfile expected = previewBase;
		changed.run();
		store.acceptProposal(proposed, expected, new AppStore.Callback<Routine>() {
			public void success(Routine r) {
				if (token != generation)
					return;
				busy = false;
				accepted = r;
				clearPreview();
				base = r.getProfile();
				fields.clear();
				seed(base);
				add("assistant",
					Txt.t(R.string.pc_saved));
				persist();
				onAccepted.accept(r);
				changed.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				status = Txt.t(R.string.pc_save_failed, e.getMessage());
				changed.run();
			}
		});
	}

	public void substituteFirst() {
		substitute(0);
	}

	/**
	 * Cambia el ejercicio de esa posición por una alternativa compatible libre; el aviso queda junto al chat.
	 */
	public void substitute(int index) {
		if (preview == null || preview.isEmpty() || index < 0 || index >= preview.getItems().size()) {
			add("assistant", Txt.t(R.string.pc_swap_needs_proposal));
			persist();
			changed.run();
			return;
		}
		RoutineItem first = preview.getItems().get(index);
		Set<String> seen = swapped.computeIfAbsent(index, k -> new HashSet<>());
		seen.add(first.getExercise().getId());
		if (!replace(index, first, seen)) {
			// Ya se mostraron todas: se vuelve a empezar la ronda sin repetir el actual.
			seen.clear();
			seen.add(first.getExercise().getId());
			if (!replace(index, first, seen)) {
				status = Txt.t(R.string.pc_no_alternative, first.getExercise().getName());
				changed.run();
			}
		}
	}

	private boolean replace(int index, RoutineItem first, Set<String> seen) {
		Set<String> used = new HashSet<>(seen);
		for (RoutineItem i : preview.getItems())
			used.add(i.getExercise().getId());
		for (Exercise e : new CompatibilityEngine().alternatives(first.getExercise(), preview.getProfile(), catalog))
			if (!used.contains(e.getId())) {
				Map<String, String> prescription = new LinkedHashMap<>(first.getPrescription());
				if (e.getMetricType() == MetricType.STRENGTH && Collections.disjoint(e.getRequiredResources(),
					EnumSet.of(TrainingProfile.Resource.DUMBBELLS, TrainingProfile.Resource.BARBELL,
						TrainingProfile.Resource.MACHINE)))
					prescription.put("loadKg", "0");
				try {
					ChatProtocol.validatePrescription(e, prescription, first.getEstimatedMinutes());
				} catch (IllegalArgumentException invalid) {
					continue;
				}
				List<RoutineItem> items = new ArrayList<>(preview.getItems());
				items.set(index, new RoutineItem(e, prescription, first.getEstimatedMinutes(),
					"Sustitución compatible; pendiente de aceptación."));
				preview = new Routine(UUID.randomUUID().toString(), 0, preview.getTitle(), preview.getProfile(), items,
					preview.getExplanation(), preview.getMetadata());
				swapped.get(index).add(e.getId());
				status = Txt.t(R.string.pc_swapped, first.getExercise().getName(), e.getName());
				persist();
				changed.run();
				return true;
			}
		return false;
	}

	/**
	 * «−5 min»: quita ejercicios del final de la propuesta actual (unos 5 minutos, al menos uno) sin tocar el resto.
	 * Siempre deja un ejercicio; la rutina no se sustituye por otra.
	 */
	public void reduceTime() {
		if (preview == null || preview.isEmpty())
			return;
		List<RoutineItem> items = new ArrayList<>(preview.getItems());
		if (items.size() <= 1) {
			status = Txt.t(R.string.pc_one_left);
			changed.run();
			return;
		}
		int before = total(items);
		List<String> removed = new ArrayList<>();
		do
			removed.add(items.remove(items.size() - 1).getExercise().getName());
		while (items.size() > 1 && before - total(items) < 5);
		update(items, Txt.t(R.string.pc_removed, String.join(", ", removed), before, total(items)));
	}

	/**
	 * «+5 min»: añade ejercicios compatibles al final (unos 5 minutos, al menos uno) sin pasar del tiempo por sesión
	 * del perfil ni repetir ejercicios; lo ya propuesto no cambia.
	 */
	public void extendTime() {
		if (preview == null || preview.isEmpty())
			return;
		TrainingProfile profile = preview.getProfile();
		List<RoutineItem> items = new ArrayList<>(preview.getItems());
		int before = total(items), limit = profile.getDurationMinutes();
		List<String> added = new ArrayList<>();
		while (total(items) - before < 5) {
			RoutineItem next = nextExercise(items, profile, limit - total(items));
			if (next == null)
				break;
			// Antes de un bloque final de resistencia, para que siga cerrando la sesión.
			int at = items.size();
			while (at > 0 && items.get(at - 1).getExercise().getMetricType() == MetricType.ENDURANCE)
				at--;
			items.add(at, next);
			added.add(next.getExercise().getName());
		}
		if (added.isEmpty()) {
			status = limit - before < 3 ? Txt.t(R.string.pc_time_full, limit) : Txt.t(R.string.pc_nothing_to_add);
			changed.run();
			return;
		}
		update(items, Txt.t(R.string.pc_added, String.join(", ", added), before, total(items)));
	}

	private void update(List<RoutineItem> items, String message) {
		preview = new Routine(UUID.randomUUID().toString(), 0, preview.getTitle(), preview.getProfile(), items,
			preview.getExplanation(), preview.getMetadata());
		swapped.clear();
		status = message;
		persist();
		changed.run();
	}

	/**
	 * Siguiente ejercicio compatible que no está en la rutina y cabe en «room» minutos, con prescripción segura.
	 */
	private RoutineItem nextExercise(List<RoutineItem> items, TrainingProfile profile, int room) {
		Set<String> used = new HashSet<>();
		for (RoutineItem i : items)
			used.add(i.getExercise().getId());
		CompatibilityEngine engine = new CompatibilityEngine();
		for (Exercise e : catalog) {
			if (used.contains(e.getId()) || !engine.isCompatible(e, profile) ||
				e.getMetricType() == MetricType.ENDURANCE || e.getMetricType() == MetricType.SPORT_PRACTICE)
				continue;
			TrainerPrompt.Prescription p = TrainerPrompt.prescribe(e, Collections.emptyMap(),
				TrainerPrompt.beginner(profile.getLevel()), profile.getGoal(), room);
			if (p != null && p.minutes <= room)
				return new RoutineItem(e, p.values, p.minutes, "Añadido con +5 min; pendiente de aceptación.");
		}
		return null;
	}

	private static int total(List<RoutineItem> items) {
		int t = 0;
		for (RoutineItem i : items)
			t += i.getEstimatedMinutes();
		return t;
	}

	/**
	 * Online usa la cuenta actual; el guiado local se elige explícitamente. Debug vacío conserva pairing.
	 */
	public void send(String text) {
		// «Hazme un plan semanal / empuje tirón pierna…»: un plan de varios días, igual con o sin IA.
		if (weeklyPlan != null && current.get() != null && WeeklyPlan.asksForPlan(text) && ready(text)) {
			add("user", text.trim());
			add("assistant", weeklyPlan.apply(text));
			persist();
			changed.run();
			return;
		}
		if (automaticConnection() && !localGuidance) {
			// La IA del móvil aún no responde (sin soporte o descargando): el aviso promete el guiado local,
			// que tampoco sale del móvil, así que se contesta con él en vez de dejar el mensaje sin enviar.
			if (!remoteActive() && onDevice()) {
				sendLocal(text);
				return;
			}
			if (!remoteActive()) {
				status = connectionNotice();
				changed.run();
				return;
			}
			sendExternal(text);
			return;
		}
		if (!localGuidance && client.connected() && !remoteDisabled)
			sendExternal(text);
		else
			sendLocal(text);
	}

	public boolean remoteActive() {
		return !localGuidance && client.connected() && !remoteDisabled;
	}

	public void sendExternal(String text) {
		if (!ready(text))
			return;
		if (!client.connected()) {
			status = Txt.t(R.string.pc_connect_server);
			changed.run();
			return;
		}
		String normalized = normalize(text);
		boolean planning = planningTurn(normalized);
		if (exercise != null && (planning || greeting(normalized))) {
			generation++;
			exercise = null;
			persist();
		}
		if (exercise != null && !sameExercise(exercise, ChatProtocol.find(catalog, exercise.getId()))) {
			status =
				Txt.t(R.string.pc_historic_version);
			changed.run();
			return;
		}
		TrainingProfile observed = current.get();
		if (fields.isEmpty() || !Objects.equals(base, observed)) {
			fields.clear();
			base = observed;
			if (base != null)
				seed(base);
		}
		String conflict = planning ? conflict(normalized) : "";
		if (!conflict.isEmpty()) {
			status = conflict;
			changed.run();
			return;
		}
		final TrainingProfile proposed;
		final Map<String, Object> data;
		try {
			if (planning) {
				planningDialogue = true;
				parse(normalized);
				Map<String, Object> partial = partialProfile();
				if ((partial.containsKey("daysPerWeek") &&
					((Integer) partial.get("daysPerWeek") < 1 || (Integer) partial.get("daysPerWeek") > 7)) ||
					(partial.containsKey("durationMinutes") &&
						((Integer) partial.get("durationMinutes") < 5 || (Integer) partial.get("durationMinutes") > 240)))
					throw new IllegalArgumentException();
				proposed = missing().isEmpty() ? build() : null;
				data = proposed == null ? partial : ChatProtocol.profile(proposed);
			} else {
				proposed = null;
				data = conversationalProfile(observed);
			}
		} catch (RuntimeException e) {
			status = Txt.t(R.string.pc_clarify_before_ai);
			changed.run();
			return;
		}
		List<ConversationStore.Message> h = new ArrayList<>();
		boolean consent = historyContextEnabled();
		if (consent)
			for (int i = Math.max(0, messages.size() - 12); i < messages.size(); i++) {
				ConversationStore.Message m = messages.get(i);
				h.add(new ConversationStore.Message(m.role, m.text.substring(0, Math.min(1000, m.text.length()))));
			}
		// Memoria del entrenador local: nunca sale del móvil (el protocolo de red no serializa estos mensajes).
		List<ConversationStore.Message> recent = new ArrayList<>();
		if (client.onDevice())
			for (int i = Math.max(0, messages.size() - TrainerPrompt.MAX_RECENT); i < messages.size(); i++)
				recent.add(messages.get(i));
		long requestOrigin = navigationOrigin.getAsLong();
		ChatClient.Request request = new ChatClient.Request(conversationId, UUID.randomUUID().toString(),
			exercise == null ? null : exercise.getId(), text.trim(), proposed, data, consent, h, recent);
		if (client.onDevice())
			request.training = training.get();
		add("user", text.trim());
		persist();
		pendingHistory = consent;
		busy = true;
		long token = ++generation;
		changed.run();
		client.send(request, new ChatClient.Callback() {
			private boolean live() {
				return token == generation &&
					(!request.historyConsent || historyContextEnabled());
			}

			public void success(String json) {
				if (!live()) {
					if (token == generation) {
						busy = false;
						pendingHistory = false;
						status = Txt.t(R.string.pc_consent_revoked);
						changed.run();
					}
					return;
				}
				busy = false;
				pendingHistory = false;
				try {
					ChatProtocol.Reply reply = ChatProtocol.reply(json, request, catalog, catalogVersion);
					if (reply.proposal != null && !Objects.equals(observed, current.get()))
						throw new IllegalArgumentException("Perfil cambiado");
					addRemote(reply.text);
					if (reply.proposal != null) {
						setPreview(reply.proposal.withMetadata(
							new RoutineMetadata(RoutineMetadata.Origin.AI, true)), observed);
						planningDialogue = false;
						base = observed;
					}
					persist();
					if (reply.open && exercise != null)
						command.accept(
							new OpenExercise(exercise, request.conversationId, request.requestId, requestOrigin));
				} catch (RuntimeException e) {
					status = Txt.t(R.string.pc_invalid_reply);
				}
				changed.run();
			}

			public void failure(String code) {
				if (token != generation)
					return;
				busy = false;
				pendingHistory = false;
				if (code.equals("PROVIDER_NOT_CONFIGURED"))
					remoteDisabled = true;
				if (client.onDevice()) {
					status = code.equals("MODEL_BUSY_OR_QUOTA") ?
						Txt.t(R.string.pc_device_busy) : Txt.t(R.string.pc_device_failed);
					changed.run();
					return;
				}
				if (automaticConnection() && code.equals("PROVIDER_NOT_CONFIGURED"))
					status = connectionNotice();
				else if (automaticConnection() && (code.equals("UNAUTHORIZED") || code.equals("SIGN_IN_REQUIRED") ||
					code.equals("AUTHENTICATION_UNAVAILABLE")))
					status = Txt.t(R.string.pc_session_unverified);
				else
					status = code.equals("PROVIDER_NOT_CONFIGURED") ?
					Txt.t(R.string.pc_connector_disabled) : Txt.t(R.string.pc_unreachable, code);
				changed.run();
			}
		});
	}

	public void openCommands(java.util.function.LongSupplier origin, Consumer<OpenExercise> route) {
		navigationOrigin = origin;
		openCommands(route);
	}

	public void openCommands(Consumer<OpenExercise> route) {
		command = route == null ? e -> {
		} : route;
	}

	/**
	 * Hook v1 tipado: Task7 decide si existe un recurso válido para este snapshot exacto.
	 */
	public static final class OpenExercise {
		public final Exercise snapshot;
		public final String conversationId, requestId;
		public final long originRevision;

		public OpenExercise(Exercise e, String c, String r) {
			this(e, c, r, -1);
		}

		public OpenExercise(Exercise e, String c, String r, long origin) {
			snapshot = e;
			conversationId = c;
			requestId = r;
			originRevision = origin;
		}
	}

	public void connect(String endpoint, String code) {
		if (owner == null || busy)
			return;
		this.endpoint = endpoint;
		pairingCode = "";
		busy = true;
		long token = ++generation;
		client.connect(endpoint, code, new ChatClient.Callback() {
			public void success(String json) {
				if (token != generation)
					return;
				busy = false;
				remoteDisabled = false;
				status = Txt.t(R.string.pc_paired);
				changed.run();
			}

			public void failure(String error) {
				if (token != generation)
					return;
				busy = false;
				status = Txt.t(R.string.pc_pair_failed, error);
				changed.run();
			}
		});
		changed.run();
	}

	public void disconnect() {
		generation++;
		busy = false;
		client.disconnect();
		pairingCode = "";
		status = Txt.t(R.string.pc_disconnected);
		changed.run();
	}
}



