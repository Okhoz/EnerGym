package com.energym.app.visual;

import com.energym.app.controller.DisciplineForm;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Cruza cada formulario de disciplina con el catálogo empaquetado (en este paquete por el lector MiniJson):
 * ninguna combinación de objetivo y cobertura que el formulario ofrezca puede quedarse sin ejercicios.
 */
public class DisciplineCatalogTest {
	@Test
	public void everyCatalogSportHasItsOwnForm() throws Exception {
		for (Map<?, ?> s : rows("sports"))
			assertTrue((String) s.get("id"), DisciplineForm.has((String) s.get("id")));
	}

	@Test
	public void everyOfferedGoalAndFocusHasExercises() throws Exception {
		List<Exercise> exercises = exercises();
		CompatibilityEngine engine = new CompatibilityEngine();
		for (Map<?, ?> s : rows("sports")) {
			String sportId = (String) s.get("id");
			DisciplineForm form = DisciplineForm.of(sportId);
			// Una cobertura pendiente de validación se ofrece igual: la propuesta explica que aún no hay contenido.
			String pending = "PENDING_VALIDATION";
			for (Focus focus : form.focuses)
				for (Goal goal : form.goals(focus, exercises)) {
					if (pending.equals(s.get(focus == Focus.SPECIFIC ? "specificCoverage" : "complementaryCoverage")))
						continue;
					boolean found = false;
					for (Category category : form.categories)
						for (Environment environment : form.environments(category, focus)) {
							Set<Resource> resources = EnumSet.noneOf(Resource.class);
							resources.addAll(form.askedResources(category));
							resources.addAll(form.includedResources);
							TrainingProfile p = new TrainingProfile.Builder().sportId(sportId).goal(goal)
								.focus(focus).category(category).environment(environment).resources(resources).build();
							for (Exercise e : exercises)
								found |= engine.isCompatible(e, p);
						}
					assertTrue(sportId + " " + goal + " " + focus, found);
				}
		}
	}

	@Test
	public void unknownSportFallsBackToGenericForm() {
		assertEquals("other", DisciplineForm.of("no-existe").sportId);
	}

	private static List<Map<?, ?>> rows(String key) throws Exception {
		Map<?, ?> catalog = (Map<?, ?>) MiniJson.parse(
			new String(Files.readAllBytes(Paths.get("src/main/assets/catalog.json")), StandardCharsets.UTF_8));
		List<Map<?, ?>> result = new ArrayList<>();
		for (Object row : (List<?>) catalog.get(key))
			result.add((Map<?, ?>) row);
		return result;
	}

	private static List<Exercise> exercises() throws Exception {
		List<Exercise> result = new ArrayList<>();
		for (Map<?, ?> e : rows("exercises"))
			result.add(new Exercise((String) e.get("id"), (String) e.get("name"),
				MetricType.valueOf((String) e.get("metricType")), enums(e.get("goals"), Goal.class),
				enums(e.get("environments"), Environment.class), enums(e.get("requiredResources"), Resource.class),
				new LinkedHashSet<>(strings(e.get("sportIds"))),
				Exercise.Coverage.valueOf((String) e.get("coverage")), false, "Ejemplo", Collections.emptyList()));
		return result;
	}

	private static List<String> strings(Object values) {
		List<String> result = new ArrayList<>();
		for (Object v : (List<?>) values)
			result.add((String) v);
		return result;
	}

	private static <E extends Enum<E>> Set<E> enums(Object values, Class<E> type) {
		Set<E> result = EnumSet.noneOf(type);
		for (String v : strings(values))
			result.add(Enum.valueOf(type, v));
		return result;
	}
}
