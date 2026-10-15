package com.energym.app.catalog;

import com.energym.app.domain.Exercise;
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

	private int score(Exercise e, String query, List<String> terms) {
		if (query.isEmpty())
			return 0;
		String name = normalize(e.getName());
		if (name.equals(query))
			return 0;
		if (name.startsWith(query) && all(terms, name))
			return 1;
		if (all(terms, name))
			return 2;
		String identifiers = name + " " + normalize(e.getId());
		if (all(terms, identifiers))
			return 3;
		StringBuilder metadata = new StringBuilder(identifiers);
		for (Resource resource : e.getRequiredResources())
			metadata.append(' ').append(materialWords(resource));
		if (all(terms, metadata.toString()))
			return 4;
		return all(terms, metadata + " " + normalize(e.getDescription())) ? 5 : -1;
	}

	private boolean all(List<String> terms, String text) {
		List<String> words = Arrays.asList(text.split(" "));
		for (String term : terms) {
			boolean found = false;
			for (String word : words)
				if (word.equals(term) || (term.length() >= 3 && word.startsWith(term))) {
					found = true;
					break;
				}
			if (!found)
				return false;
		}
		return true;
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
