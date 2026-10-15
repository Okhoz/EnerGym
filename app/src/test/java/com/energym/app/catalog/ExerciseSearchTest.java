package com.energym.app.catalog;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ExerciseSearchTest {
	private Exercise exercise(String id, String name, String description, String sport,
		Environment environment, Goal goal, Resource... material) {
		return new Exercise(id, name, MetricType.STRENGTH, EnumSet.of(goal), EnumSet.of(environment),
			new LinkedHashSet<>(Arrays.asList(material)), Collections.singleton(sport), Exercise.Coverage.SPECIFIC,
			false, description, Collections.emptyList());
	}

	private final Exercise push = exercise("push-up", "Flexión estándar", "Apoya las manos y flexiona los codos.",
		"calisthenics", Environment.HOME, Goal.STRENGTH);
	private final Exercise band = exercise("band-press", "Press con banda", "Extiende los brazos para una flexión controlada.",
		"strength", Environment.HOME, Goal.STRENGTH, Resource.BANDS);
	private final Exercise gym = exercise("bench-press", "Press de banca", "Flexión estándar descrita como referencia.",
		"strength", Environment.GYM, Goal.STRENGTH, Resource.BARBELL, Resource.BENCH);
	private final Exercise mobility = exercise("band-mobility", "Movilidad de hombro", "Eleva los brazos con control.",
		"calisthenics", Environment.HOME, Goal.MOBILITY, Resource.BANDS);
	private final List<Exercise> catalog = Arrays.asList(gym, mobility, band, push);

	private List<Exercise> find(String query, ExerciseSearch.Filters filters) {
		return new ExerciseSearch(catalog).find(query, filters);
	}

	private List<String> ids(List<Exercise> result) {
		List<String> ids = new ArrayList<>();
		for (Exercise e : result)
			ids.add(e.getId());
		return ids;
	}

	@Test public void normalizesAccentsCaseAndRepeatedWhitespace() {
		assertEquals("flexion estandar", ExerciseSearch.normalize("  FLEXIÓN \t eSTÁNDAR\n"));
		assertEquals("push-up", find("  FLEXIÓN \t esTÁN ", ExerciseSearch.Filters.all()).get(0).getId());
	}

	@Test public void allPartialWordsMustMatchWordStarts() {
		assertEquals(Arrays.asList("push-up", "bench-press"), ids(find("flex est", ExerciseSearch.Filters.all())));
		assertTrue(find("exion", ExerciseSearch.Filters.all()).isEmpty());
		assertTrue(find("flex imposible", ExerciseSearch.Filters.all()).isEmpty());
	}

	@Test public void shortWordsDoNotAccidentallyMatchEveryDescription() {
		assertTrue(find("p", ExerciseSearch.Filters.all()).isEmpty());
		assertTrue(find("pr", ExerciseSearch.Filters.all()).isEmpty());
		assertTrue(find("de la", ExerciseSearch.Filters.all()).isEmpty());
		assertEquals(Collections.singletonList("push-up"), ids(find("flexión de manos", ExerciseSearch.Filters.all())));
	}

	@Test public void exactNamePrecedesDescriptionEvenWhenCatalogIsReversed() {
		assertEquals(Arrays.asList("push-up", "bench-press"), ids(find("flexion estandar", ExerciseSearch.Filters.all())));
	}

	@Test public void materialNamesAreSearchableFromActualMetadata() {
		assertEquals(Collections.singletonList("bench-press"), ids(find("barra", ExerciseSearch.Filters.all())));
	}

	@Test public void emptyQueryExploresTheWholeCatalogInStableNameOrder() {
		assertEquals(Arrays.asList("push-up", "band-mobility", "band-press", "bench-press"),
			ids(find("", ExerciseSearch.Filters.all())));
	}

	@Test public void filtersCombineDisciplineEnvironmentMaterialAndGoal() {
		ExerciseSearch.Filters filters = new ExerciseSearch.Filters("calisthenics", Environment.HOME, "BANDS", Goal.MOBILITY);
		assertEquals(Collections.singletonList("band-mobility"), ids(find("homb", filters)));
		assertTrue(find("homb", new ExerciseSearch.Filters("strength", Environment.HOME, "BANDS", Goal.MOBILITY)).isEmpty());
	}

	@Test public void withoutMaterialDoesNotIncludeExercisesRequiringFurnitureOrBands() {
		assertEquals(Collections.singletonList("push-up"),
			ids(find("", new ExerciseSearch.Filters("", null, "NONE", null))));
	}

	@Test public void materialFilterMeansRequiredMaterialAndPreservesOtherRequirements() {
		assertEquals(Collections.singletonList("bench-press"),
			ids(find("", new ExerciseSearch.Filters("", null, "BENCH", null))));
		assertEquals(EnumSet.of(Resource.BARBELL, Resource.BENCH), gym.getRequiredResources());
	}

	@Test public void browsingDoesNotHideUnreviewedOrProfileIncompatibleExercises() {
		assertFalse(gym.isReviewed());
		assertTrue(find("", ExerciseSearch.Filters.all()).contains(gym));
	}

	@Test public void unknownDisciplineAndNoMatchesReturnEmptyWithoutFallback() {
		assertTrue(find("", new ExerciseSearch.Filters("absent", null, "", null)).isEmpty());
		assertTrue(find("zapatillainexistente", ExerciseSearch.Filters.all()).isEmpty());
	}
}
