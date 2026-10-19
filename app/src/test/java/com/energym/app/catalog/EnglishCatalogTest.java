package com.energym.app.catalog;

import com.energym.app.domain.*;
import com.energym.app.i18n.SourceStrings;
import com.energym.app.visual.TestCatalog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Catálogo con la app en inglés: se ve y se busca en inglés, pero lo guardado y la IA siguen en español. */
public class EnglishCatalogTest {
	private CatalogRepository catalog;

	@Before
	public void load() throws Exception {
		catalog = TestCatalog.repository();
	}

	@After
	public void spanish() {
		SourceStrings.use(Locale.forLanguageTag("es-ES"));
	}

	@Test
	public void everyExerciseAndSportHasItsEnglishVersion() {
		SourceStrings.use(Locale.ENGLISH);
		assertEquals(287, catalog.getExercises().size());
		Set<String> spanishWords = new HashSet<>(Arrays.asList("con", "de", "sentadilla", "mancuernas", "barra"));
		for (Exercise e : catalog.getExercises()) {
			assertFalse(e.getId() + " sin nombre en inglés", e.getName().trim().isEmpty());
			assertFalse(e.getId() + " sin técnica en inglés", e.getDescription().equals(e.getSourceDescription()));
			for (String word : ExerciseSearch.normalize(e.getName()).split(" "))
				assertFalse(e.getId() + " sigue en español: " + e.getName(), spanishWords.contains(word));
		}
		for (Sport s : catalog.getSports())
			assertFalse(s.getId() + " sin nombre en inglés", s.getName().equals(s.getSourceName()) &&
				!Arrays.asList("powerlifting", "yoga", "pilates").contains(s.getId()));
		assertEquals("Dumbbell squat", catalog.findExercise("dumbbell-squat").getName());
		assertEquals("Fire hydrant", catalog.findExercise("fire-hydrant").getName());
		assertEquals("Strength", catalog.findSport("strength").getName());
	}

	@Test
	public void englishSearchFindsSquatsAndAlsoTheSpanishName() {
		SourceStrings.use(Locale.ENGLISH);
		ExerciseSearch search = new ExerciseSearch(catalog.getExercises());
		List<Exercise> squats = search.find("squat", ExerciseSearch.Filters.all());
		assertTrue(squats.size() >= 10);
		for (Exercise e : squats.subList(0, 10))
			assertTrue(e.getName(), e.getName().toLowerCase(Locale.ROOT).contains("squat"));
		assertTrue(squats.contains(catalog.findExercise("dumbbell-squat")));
		assertEquals("dumbbell-squat", search.find("dumbbell squat", ExerciseSearch.Filters.all()).get(0).getId());
		assertEquals("fire-hydrant", search.find("fire hydrant", ExerciseSearch.Filters.all()).get(0).getId());
		assertTrue("También en español", search.find("sentadilla con mancuernas", ExerciseSearch.Filters.all())
			.contains(catalog.findExercise("dumbbell-squat")));
		assertTrue("Grupo muscular en inglés", search.find("chest", ExerciseSearch.Filters.all())
			.contains(catalog.findExercise("barbell-bench-press")));
	}

	@Test
	public void savedCopiesAreTranslatedButKeepTheSpanishSource() {
		Exercise e = catalog.findExercise("dumbbell-squat");
		// Copia como la que se guarda en una rutina (solo lleva el español).
		Exercise saved = new Exercise(e.getId(), e.getSourceName(), e.getMetricType(), e.getGoals(),
			e.getEnvironments(), e.getRequiredResources(), e.getSportIds(), e.getCoverage(), e.isReviewed(),
			e.getSourceDescription(), e.getAlternativeIds());
		assertEquals("Sentadilla con mancuernas", saved.getName());
		SourceStrings.use(Locale.ENGLISH);
		assertEquals("Dumbbell squat", saved.getName());
		assertTrue(saved.getDescription().startsWith("Hold dumbbells"));
		assertEquals("Sentadilla con mancuernas", saved.getSourceName());
		// Otro ejercicio con el mismo ID (por ejemplo, uno propio) no toma una traducción ajena.
		Exercise other = new Exercise(e.getId(), "Mi sentadilla", e.getMetricType(), e.getGoals(), e.getEnvironments(),
			e.getRequiredResources(), e.getSportIds(), e.getCoverage(), false, "Mía.", e.getAlternativeIds());
		assertEquals("Mi sentadilla", other.getName());
		assertEquals("Mía.", other.getDescription());
	}
}
