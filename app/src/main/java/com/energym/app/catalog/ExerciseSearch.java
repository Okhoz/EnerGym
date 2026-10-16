package com.energym.app.catalog;

import com.energym.app.domain.Exercise;
import com.energym.app.domain.TextMatch;
import com.energym.app.domain.TrainingProfile.*;

import java.text.Normalizer;
import java.util.*;

/** Consulta local de biblioteca: metadatos explícitos y relevancia, sin decidir la elegibilidad de rutinas. */
public final class ExerciseSearch {
	public static final class Filters {
		public final String discipline, material;
		public final Environment environment;
		public final Goal goal;

		/** Material vacío: cualquiera; NONE: ningún recurso requerido; otro valor: nombre de Resource. */
		public Filters(String discipline, Environment environment, String material, Goal goal) {
			this.discipline = discipline == null ? "" : discipline;
			this.environment = environment;
			this.material = material == null ? "" : material;
			if (!this.material.isEmpty() && !"NONE".equals(this.material))
				Resource.valueOf(this.material);
			this.goal = goal;
		}

		public static Filters all() {
			return new Filters("", null, "", null);
		}
	}

	private static final Set<String> CONNECTORS = new HashSet<>(Arrays.asList(
		"a", "al", "de", "del", "el", "la", "las", "los", "y", "en", "con", "por", "para", "un", "una"));
	private final List<Exercise> catalog;

	public ExerciseSearch(Collection<Exercise> catalog) {
		this.catalog = new ArrayList<>(Objects.requireNonNull(catalog));
	}

	public static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}

	public List<Exercise> find(String raw, Filters filters) {
		Objects.requireNonNull(filters);
		String query = normalize(raw);
		List<String> terms = new ArrayList<>();
		for (String term : query.split(" "))
			if (term.length() >= 2 && !CONNECTORS.contains(term))
				terms.add(term);
		// Una consulta formada sólo por letras sueltas o conectores no equivale a explorar todo.
		if (!query.isEmpty() && terms.isEmpty())
			return Collections.emptyList();
		Map<Exercise, Integer> scores = new HashMap<>();
		for (Exercise exercise : catalog) {
			if (!matches(exercise, filters))
				continue;
			int score = score(exercise, query, terms);
			if (score >= 0)
				scores.put(exercise, score);
		}
		List<Exercise> result = new ArrayList<>(scores.keySet());
		result.sort(Comparator.<Exercise>comparingInt(scores::get)
			.thenComparing(e -> normalize(e.getName())).thenComparing(Exercise::getId));
		return Collections.unmodifiableList(result);
	}

	private boolean matches(Exercise e, Filters f) {
		return (f.discipline.isEmpty() || e.getSportIds().contains(f.discipline)) &&
			(f.environment == null || e.getEnvironments().contains(f.environment)) &&
			(f.goal == null || e.getGoals().contains(f.goal)) &&
			(f.material.isEmpty() || ("NONE".equals(f.material) ? e.getRequiredResources().isEmpty() :
				e.getRequiredResources().contains(Resource.valueOf(f.material))));
	}

	/**
	 * Relevancia (menor es mejor): primero coincidencias directas en el nombre, luego en alias, material, grupo
	 * muscular y técnica; después sinónimos y erratas. -1 si no coincide.
	 */
	private int score(Exercise e, String query, List<String> terms) {
		if (query.isEmpty())
			return 0;
		String name = normalize(e.getName());
		if (name.equals(query))
			return 0;
		int inName = TextMatch.matchAll(terms, name, 3);
		if (inName <= TextMatch.PREFIX)
			return name.startsWith(query) ? 1 : 2;
		String identifiers = name + " " + normalize(e.getId()) + " " + aliases(e.getId());
		if (TextMatch.matchAll(terms, identifiers, 3) <= TextMatch.PREFIX)
			return 3;
		StringBuilder metadata = new StringBuilder(identifiers);
		for (Resource resource : e.getRequiredResources())
			metadata.append(' ').append(materialWords(resource));
		// El grupo muscular también se busca: «pecho», «gluteos», «espalda»…
		for (com.energym.app.domain.RoutineRequest.Area area : com.energym.app.domain.RoutineRequest.areas(e))
			metadata.append(' ').append(normalize(area.label));
		int inMetadata = TextMatch.matchAll(terms, metadata.toString(), 3);
		if (inMetadata <= TextMatch.PREFIX)
			return 4;
		int inDescription = TextMatch.matchAll(terms, metadata + " " + normalize(e.getDescription()), 3);
		if (inDescription <= TextMatch.PREFIX)
			return 5;
		// Sinónimos y erratas («biseps», «sentadila»), siempre detrás de las coincidencias directas.
		if (inName != TextMatch.NONE)
			return 4 + inName;
		if (inMetadata != TextMatch.NONE)
			return 8 + inMetadata;
		return inDescription == TextMatch.SYNONYM ? 12 : -1;
	}

	/** Nombres habituales de los básicos: apuntan al mismo ID y respetan todos los filtros. */
	private String aliases(String exerciseId) {
		switch (exerciseId) {
			case "barbell-back-squat": return "sentadilla convencional sentadilla clasica sentadilla libre back squat";
			case "barbell-deadlift": return "peso muerto convencional peso muerto clasico conventional deadlift";
			case "barbell-bench-press": return "press plano press banca plano bench press";
			case "barbell-overhead-press": return "press militar de pie overhead press";
			case "barbell-row": return "remo inclinado con barra bent over row";
			case "barbell-romanian-deadlift": return "peso muerto rumano romanian deadlift rdl";
			case "lat-pulldown": return "jalones al pecho polea alta lat pulldown";
			case "seated-cable-row": return "remo sentado polea baja cable row";
			case "leg-press": return "prensa cuadriceps leg press";
			case "leg-extension": return "extension cuadriceps extensiones de piernas leg extension";
			case "leg-curl": return "curl piernas femoral tumbado leg curl";
			case "dumbbell-lateral-raise": return "elevaciones laterales de hombros lateral raise";
			case "barbell-hip-thrust": return "empuje de cadera hip thrust gluteos";
			case "cable-triceps-pushdown": return "triceps polea pushdown";
			case "parallel-bar-dips": return "fondos paralelas dips";
			default: return "";
		}
	}

	private String materialWords(Resource resource) {
		switch (resource) {
			case DUMBBELLS: return "mancuernas dumbbells";
			case BARBELL: return "barra barbell";
			case BANDS: return "bandas elasticas bands";
			case MAT: return "esterilla colchoneta mat";
			case BENCH: return "banco bench";
			case MACHINE: return "maquina machine";
			case CHAIR: return "silla chair";
			case WALL: return "pared wall";
			case SUPPORT: return "apoyo support";
			case BICYCLE: return "bicicleta bicycle";
			case POOL: return "piscina agua pool";
			default: return normalize(resource.name());
		}
	}
}
