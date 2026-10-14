package com.energym.app.domain;

import com.energym.app.catalog.CatalogRepository;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.energym.app.domain.TrainingProfile.*;

public class CatalogRepositoryTest {
    @Test public void rejectsDuplicateExerciseIds() {
        Exercise exercise = exercise("squat", "strength", Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> new CatalogRepository(1, sports(), Arrays.asList(exercise, exercise)));
    }
    @Test public void rejectsUndeclaredSportAndDanglingAlternative() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogRepository(1, sports(), Collections.singletonList(
                exercise("squat", "unknown", Collections.emptyList()))));
        assertThrows(IllegalArgumentException.class, () -> new CatalogRepository(1, sports(), Collections.singletonList(
                exercise("squat", "strength", Collections.singletonList("missing")))));
    }
    @Test public void pendingSpecificCoverageCannotContainSpecificExercise() {
        Sport pending = new Sport("strength", "Fuerza", Sport.CoverageStatus.PENDING_VALIDATION,
                Sport.CoverageStatus.EXAMPLE_UNREVIEWED);
        assertThrows(IllegalArgumentException.class, () -> new CatalogRepository(1, Collections.singletonList(pending),
                Collections.singletonList(exercise("squat", "strength", Collections.emptyList()))));
    }
    @Test public void rejectsUnsupportedSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogRepository(99, sports(), Collections.emptyList()));
    }
    @Test public void lookupUsesStableIdAndCollectionsCannotBeModified() {
        CatalogRepository catalog = new CatalogRepository(1, sports(), Collections.singletonList(exercise("squat", "strength", Collections.emptyList())));
        assertNotNull(catalog.findExercise("squat"));
        assertEquals("squat", catalog.findExercise("squat").getId());
        assertNull(catalog.findExercise("not-in-catalog"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.getExercises().clear());
    }
    private List<Sport> sports() {
        return Collections.singletonList(new Sport("strength", "Fuerza", Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
                Sport.CoverageStatus.PENDING_VALIDATION));
    }
    private Exercise exercise(String id, String sportId, List<String> alternatives) {
        return new Exercise(id, id, MetricType.STRENGTH, EnumSet.of(Goal.STRENGTH), EnumSet.of(Environment.HOME),
                Collections.emptySet(), Collections.singleton(sportId), Exercise.Coverage.SPECIFIC, false, "Ejemplo", alternatives);
    }
}
