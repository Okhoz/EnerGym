package com.energym.app.visual;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.controller.DisciplineForm;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Los básicos de powerlifting reutilizan los ejercicios y clips canónicos, sin duplicarlos. */
public final class PowerliftingCatalogTest {
	private static final List<String> BASICS = Arrays.asList("barbell-back-squat", "barbell-bench-press",
		"barbell-deadlift", "barbell-sumo-deadlift");

	@Test public void powerliftingSearchFindsEachCanonicalBasicWithItsTechnique() throws Exception {
		CatalogRepository catalog = catalog();
		Sport sport = catalog.findSport("powerlifting");
		assertNotNull("Powerlifting debe ser una disciplina seleccionable", sport);
		assertEquals(Sport.CoverageStatus.EXAMPLE_UNREVIEWED, sport.getSpecificCoverage());
		List<Exercise> found = new ExerciseSearch(catalog.getExercises()).find("",
			new ExerciseSearch.Filters("powerlifting", Environment.GYM, "", Goal.STRENGTH));
		for (String id : BASICS) {
			Exercise canonical = catalog.findExercise(id);
			assertNotNull(id, canonical);
			assertTrue("El filtro Powerlifting incluye " + id, found.contains(canonical));
			assertFalse(id, canonical.getDescription().trim().isEmpty());
			assertFalse("El contenido no adquiere revisión profesional al asociar disciplina", canonical.isReviewed());
		}
	}

	@Test public void powerliftingFormBuildsACompatibleRoutineWithTheBasicMovements() throws Exception {
		CatalogRepository catalog = catalog();
		DisciplineForm form = DisciplineForm.of("powerlifting");
		assertEquals("La disciplina tiene formulario propio, sin fallback a Otra", "powerlifting", form.sportId);
		assertTrue(form.categories.contains(Category.GYM));
		Set<Resource> resources = EnumSet.noneOf(Resource.class);
		resources.addAll(form.askedResources(Category.GYM));
		resources.addAll(form.includedResources);
		TrainingProfile profile = new TrainingProfile.Builder().sportId(form.sportId).category(Category.GYM)
			.environment(form.environments(Category.GYM, Focus.SPECIFIC).get(0)).focus(Focus.SPECIFIC)
			.goal(form.goals(Focus.SPECIFIC, catalog.getExercises()).get(0)).resources(resources).build();
		Routine routine = new RoutinePlanner().create(profile, catalog.getExercises());
		assertTrue(routine.isValidFor(profile));
		Set<String> selected = new HashSet<>();
		for (RoutineItem item : routine.getItems()) selected.add(item.getExercise().getId());
		assertTrue("El contexto del formulario admite los básicos", selected.containsAll(BASICS));
	}

	@Test public void barbellAndBenchRequirementsRemainEnforcedForPowerlifting() throws Exception {
		CatalogRepository catalog = catalog();
		TrainingProfile profile = new TrainingProfile.Builder().sportId("powerlifting").category(Category.GYM)
			.environment(Environment.GYM).resources(EnumSet.of(Resource.BARBELL)).build();
		CompatibilityEngine compatibility = new CompatibilityEngine();
		assertTrue(compatibility.isCompatible(catalog.findExercise("barbell-back-squat"), profile));
		assertTrue(compatibility.isCompatible(catalog.findExercise("barbell-deadlift"), profile));
		assertFalse("Banca necesita también banco", compatibility.isCompatible(catalog.findExercise("barbell-bench-press"), profile));
		TrainingProfile noBarbell = profile.toBuilder().resources(Collections.emptySet()).build();
		for (String id : BASICS) assertFalse(id, compatibility.isCompatible(catalog.findExercise(id), noBarbell));
	}

	@Test public void everyBasicKeepsItsSpecificPackagedSchematicClip() throws Exception {
		CatalogRepository catalog = catalog();
		ExerciseResourcePolicy policy = new ExerciseResourcePolicy(catalog, PackagedManifest.all());
		Map<String, String> clips = new LinkedHashMap<>();
		clips.put("barbell-back-squat", "esq/squat-back");
		clips.put("barbell-bench-press", "esq/bench-press");
		clips.put("barbell-deadlift", "esq/deadlift");
		clips.put("barbell-sumo-deadlift", "esq/sumo-deadlift");
		ResourceRepository repository = PackagedManifest.repository();
		byte[] model = repository.loadVerified(repository.forExercise("barbell-back-squat"));
		ByteBuffer header = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN);
		Map<?, ?> document = (Map<?, ?>) MiniJson.parse(new String(model, 20, header.getInt(12), StandardCharsets.UTF_8));
		Set<String> packaged = new HashSet<>();
		for (Object value : (List<?>) document.get("animations")) packaged.add((String) ((Map<?, ?>) value).get("name"));
		for (Map.Entry<String, String> clip : clips.entrySet()) {
			ResourceManifest resource = PackagedManifest.forExercise(clip.getKey());
			assertTrue(clip.getKey(), policy.available(catalog.findExercise(clip.getKey())));
			assertEquals(clip.getKey(), clip.getValue(), resource.clipName);
			assertTrue(resource.clipName, packaged.contains(resource.clipName));
			assertEquals(ResourceManifest.Kind.SCHEMATIC, resource.kind);
			assertEquals("PATTERN", resource.match);
			assertFalse(resource.professionallyReviewed);
		}
	}

	@Test public void mobileAndAssistantBackendUseTheSamePowerliftingCatalogBytes() throws Exception {
		assertArrayEquals(Files.readAllBytes(Paths.get("src/main/assets/catalog.json")),
			Files.readAllBytes(Paths.get("../backend/src/main/resources/catalog-v2.json")));
	}

	private static CatalogRepository catalog() throws Exception {
		Map<?, ?> root = (Map<?, ?>) MiniJson.parse(new String(
			Files.readAllBytes(Paths.get("src/main/assets/catalog.json")), StandardCharsets.UTF_8));
		List<Sport> sports = new ArrayList<>();
		for (Object value : (List<?>) root.get("sports")) {
			Map<?, ?> row = (Map<?, ?>) value;
			sports.add(new Sport((String) row.get("id"), (String) row.get("name"),
				Sport.CoverageStatus.valueOf((String) row.get("specificCoverage")),
				Sport.CoverageStatus.valueOf((String) row.get("complementaryCoverage"))));
		}
		List<Exercise> exercises = new ArrayList<>();
		for (Object value : (List<?>) root.get("exercises")) {
			Map<?, ?> row = (Map<?, ?>) value;
			exercises.add(new Exercise((String) row.get("id"), (String) row.get("name"),
				MetricType.valueOf((String) row.get("metricType")), enums(row.get("goals"), Goal.class),
				enums(row.get("environments"), Environment.class), enums(row.get("requiredResources"), Resource.class),
				new LinkedHashSet<>(strings(row.get("sportIds"))), Exercise.Coverage.valueOf((String) row.get("coverage")),
				Boolean.TRUE.equals(row.get("reviewed")), (String) row.get("description"), strings(row.get("alternativeIds"))));
		}
		return new CatalogRepository(((Number) root.get("version")).intValue(), sports, exercises);
	}

	private static List<String> strings(Object values) {
		List<String> result = new ArrayList<>();
		for (Object value : (List<?>) values) result.add((String) value);
		return result;
	}

	private static <E extends Enum<E>> Set<E> enums(Object values, Class<E> type) {
		Set<E> result = EnumSet.noneOf(type);
		for (String value : strings(values)) result.add(Enum.valueOf(type, value));
		return result;
	}
}
