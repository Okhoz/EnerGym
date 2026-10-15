package com.energym.app.assistant;

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
				previewBase = s.previewBase;
				base = s.base;
				exercise = s.exercise;
			}
			if (messages.isEmpty())
				add("assistant",
					"¡Hola! Soy tu asistente de entrenamiento. Podemos hablar de tus dudas, técnica, hábitos y cómo te va entrenando. Si quieres una rutina, pídemela y la revisamos antes de guardarla.");
		}
		changed.run();
	}

	/**
	 * Vincula cada propuesta al perfil vigente en su creación; resembrar el diálogo nunca cambia este CAS.
	 */
	private void setPreview(Routine value, TrainingProfile expected) {
		preview = value;
		previewBase = expected;
	}

	private void clearPreview() {
		preview = null;
		previewBase = null;
	}

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
				return "Tu móvil no trae Gemini Nano, pero puede usar " + model + " sin internet. Descárgalo una vez " +
					"(gratis) y tu entrenador usará IA en el propio móvil. Mientras, responde el guiado local.";
			if (unavailable.equals("MODEL_DOWNLOAD_FAILED"))
				return "No se pudo completar la descarga de " + model + ". Comprueba la conexión y vuelve a intentarlo; " +
					"mientras, responde el guiado local.";
			if (unavailable.equals("MODEL_DOWNLOADING"))
				return "Preparando tu entrenador IA: descargando " + model + " (2,6 GB, solo la primera vez" +
					(progress >= 0 ? ", " + progress + " %" : "") + "). Mientras tanto te responde el guiado local.";
			if (unavailable.equals("MODEL_CHECKING"))
				return "Comprobando qué IA puede usar tu móvil…";
			if (unavailable.equals("MODEL_UNAVAILABLE"))
				return "Este móvil no puede ejecutar la IA en el dispositivo. Tu entrenador responde con el guiado " +
					"local, sin IA y sin coste.";
			return "Tu entrenador usa " + model + " en el propio móvil: sin internet, sin servidor y sin coste.";
		}
		if (unavailable.equals("BACKEND_NOT_CONFIGURED"))
			return "La IA online aún no está disponible en esta versión. Puedes elegir el guiado local, que funciona sin IA.";
		if (unavailable.equals("CONFIGURACION_INVALIDA"))
			return "La configuración de la IA online no es válida. Puedes seguir usando las funciones locales.";
		if (unavailable.equals("SIGN_IN_REQUIRED"))
			return "Vuelve a iniciar sesión con Google para usar la IA online con tu cuenta.";
		if (remoteDisabled)
			return "La IA online no está activada en el servidor. Puedes reintentar más tarde o elegir el guiado local.";
		return "La IA online usa tu cuenta de Google. No necesitas conectar un ordenador ni introducir códigos.";
	}

	/** Cambiar de modo es una elección explícita; no envía mensajes ni reintenta una petición. */
	public void selectLocalGuidance(boolean enabled) {
		if (!automaticConnection() || busy)
			return;
		localGuidance = enabled;
		if (!enabled)
			remoteDisabled = false;
		status = enabled ? "Guiado local seleccionado: respuestas por reglas, sin IA." : connectionNotice();
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
		status = "Solicitud cancelada en el chat. Cualquier respuesta pendiente se descartará.";
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
			status = "Contexto desactivado. Se ha descartado la respuesta pendiente.";
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
			status = "Entra en demo local o conecta tu cuenta para conservar esta conversación.";
			changed.run();
			return false;
		}
		if (busy)
			return false;
		if (text == null || text.trim().isEmpty() || text.length() > 2000) {
			status = "Escribe un mensaje de 1 a 2000 caracteres.";
			changed.run();
			return false;
		}
		status = "";
		composer = "";
		return true;
	}

	private void add(String role, String text) {
		messages.add(new ConversationStore.Message(role, text));
		int size = 0;
		for (ConversationStore.Message m : messages)
			size += m.text.length();
		while (messages.size() > 48 || size > 16000) {
			size -= messages.remove(0).text.length();
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
		status = client.onDevice() && !localGuidance ? connectionNotice() :
			automaticConnection() ? "Guiado local seleccionado · respuestas por reglas, sin IA." :
			"Guiado local · sin IA. Conecta tu servidor en Cuenta para conversar con la IA.";
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
				response = "¡Hola! Podemos hablar de tus entrenamientos. ¿Qué te gustaría mejorar o qué duda tienes hoy?";
			else if (text.contains("constan") || text.contains("habito") || text.contains("motiv"))
				response = "Para ganar constancia, prueba una meta pequeña que puedas repetir y reserva un momento concreto. Si un día no sale, retoma en la siguiente ocasión sin intentar compensarlo todo. ¿Qué suele dificultarte empezar?";
			else if (text.contains("cans") || text.contains("descans"))
				response = "El descanso también forma parte del entrenamiento. Ajusta la sesión a cómo te encuentres y evita forzar si estás muy cansado. Si hay dolor o malestar persistente, consulta a un profesional sanitario.";
			else
				response = "Puedo orientarte con la técnica del catálogo y preparar una rutina si me la pides. En este modo local uso respuestas guiadas; para conversar con IA, elige el modo online cuando esté disponible.";
			add("assistant", response);
			persist();
			changed.run();
			return;
		}
		planningDialogue = true;
		parse(text);
		List<String> missing = missing();
		if (!missing.isEmpty()) {
			add("assistant", "Para preparar la propuesta me falta: " + String.join(", ", missing) +
				". Por ejemplo: fuerza y nivel principiante.");
			clearPreview();
		} else {
			try {
				TrainingProfile proposed = build();
				setPreview(new RoutinePlanner().create(proposed, catalog), base);
				planningDialogue = false;
				add("assistant", preview.isEmpty() ? preview.getExplanation() :
					"He preparado una vista previa: " + proposed.getDaysPerWeek() + " días por semana, " +
						proposed.getDurationMinutes() + " min disponibles, " +
						AppController.label(proposed.getEnvironment().name()) +
						". Revisa los ejercicios, el perfil y los recursos. Solo guardaré al aceptar. La rutina es un ejemplo por sesión; no crea un calendario semanal.");
			} catch (RuntimeException e) {
				clearPreview();
				status = "Revisa los días (1–7), tiempo (5–240 min) y el material disponible.";
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
			return "Aclara el lugar: ¿entrenarás en casa o en el gimnasio?";
		if (text.contains("sin material") || text.contains("sin equipo"))
			for (String resource : Arrays.asList("mancuern", "barra", "banda", "esterilla", "banco", "silla", "pared",
				"maquina"))
				if (text.contains(resource) &&
					!Pattern.compile("(?:no (?:tengo|hay|uso|dispongo de)|sin)\\s+(?:\\w+\\s+){0,2}" + resource)
						.matcher(text).find())
					return "Aclara el material: indicas sin material y también un recurso. ¿Cuál estará disponible para esta rutina?";
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
		String[][] keys = {{"days", "días por semana"}, {"duration", "minutos por sesión"}, {"environment", "lugar"},
			{"resources", "material (o sin material)"}, {"goal", "objetivo"}, {"level", "nivel"}};
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

	private String explanation(Exercise e) {
		return "Técnica de " + e.getName() + "\n" + e.getDescription() + "\nMaterial de esta versión: " +
			(e.getRequiredResources().isEmpty() ? "ninguno" : AppController.labels(e.getRequiredResources())) +
			". Practica con control; detente si aparece dolor. Es una explicación guiada por reglas del snapshot seleccionado.";
	}

	public void reject() {
		if (busy)
			return;
		clearPreview();
		add("assistant", "Propuesta rechazada. Tu perfil, rutina aceptada y sesiones guardadas se conservan.");
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
			status = "El perfil o sus recursos cambiaron. Solicita una nueva propuesta antes de aceptar.";
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
					"Perfil y propuesta aceptados. La nueva rutina está disponible en Entrenamiento; las sesiones anteriores conservan sus snapshots.");
				persist();
				onAccepted.accept(r);
				changed.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				status = "No se guardó la propuesta: " + e.getMessage();
				changed.run();
			}
		});
	}

	public void substituteFirst() {
		if (preview == null || preview.isEmpty()) {
			add("assistant", "Primero prepara una propuesta para sustituir un ejercicio.");
			persist();
			changed.run();
			return;
		}
		RoutineItem first = preview.getItems().get(0);
		Set<String> used = new HashSet<>();
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
				items.set(0, new RoutineItem(e, prescription, first.getEstimatedMinutes(),
					"Sustitución compatible del primer ejercicio; pendiente de aceptación."));
				preview = new Routine(UUID.randomUUID().toString(), 0, preview.getTitle(), preview.getProfile(), items,
					preview.getExplanation(), preview.getMetadata());
				add("assistant",
					"He sustituido " + first.getExercise().getName() + " por " + e.getName() + " en la vista previa.");
				persist();
				changed.run();
				return;
			}
		add("assistant", "No hay una alternativa compatible libre para el primer ejercicio de esta propuesta.");
		persist();
		changed.run();
	}

	public void reduceTime() {
		if (preview != null)
			sendLocal("Ajusta la rutina a " + Math.max(5, preview.getProfile().getDurationMinutes() - 5) + " min");
	}

	/**
	 * Online usa la cuenta actual; el guiado local se elige explícitamente. Debug vacío conserva pairing.
	 */
	public void send(String text) {
		if (automaticConnection() && !localGuidance) {
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
			status = "Conecta el servidor de IA en Cuenta para usar la IA.";
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
				"Esta versión histórica del ejercicio difiere del catálogo vigente del servidor. Consulta su explicación guiada original o selecciona un ejercicio actual antes de enviar a IA.";
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
			status = "Aclara los días (1–7), tiempo (5–240 min) y material antes de enviar a IA.";
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
						status = "Respuesta descartada: revocaste el consentimiento.";
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
					status = "Respuesta inválida o desactualizada. No se ha guardado ni abierto ningún ejercicio.";
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
						"La IA de tu móvil está ocupada o ha llegado a su límite por ahora. Inténtalo en un momento." :
						"La IA de tu móvil no ha podido responder. Vuelve a enviarlo o usa el guiado local.";
					changed.run();
					return;
				}
				if (automaticConnection() && code.equals("PROVIDER_NOT_CONFIGURED"))
					status = connectionNotice();
				else if (automaticConnection() && (code.equals("UNAUTHORIZED") || code.equals("SIGN_IN_REQUIRED") ||
					code.equals("AUTHENTICATION_UNAVAILABLE")))
					status = "No se pudo verificar tu sesión. Vuelve a iniciar sesión con Google y envía el mensaje de nuevo.";
				else
					status = code.equals("PROVIDER_NOT_CONFIGURED") ?
					"El conector opcional está desactivado en el servidor. Vuelve a enviar tu mensaje y te responderá el asistente guiado local." :
					"No he podido contactar con la IA (" + code + "). Inténtalo de nuevo en un momento.";
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
				status = "Servidor de IA emparejado. El chat responderá con IA.";
				changed.run();
			}

			public void failure(String error) {
				if (token != generation)
					return;
				busy = false;
				status = "No se conectó: " + error + ". El chat seguirá con el asistente guiado local.";
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
		status = "Servidor desconectado y revocación solicitada. El chat sigue con el asistente guiado local.";
		changed.run();
	}
}



