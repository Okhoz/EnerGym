package com.energym.app.domain;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.energym.app.domain.TrainingProfile.*;

public class CompatibilityEngineTest {
    private final CompatibilityEngine engine = new CompatibilityEngine();

    @Test public void noResourcesRejectsChairWallAndSupportSeparately() {
        TrainingProfile profile = profile("strength", Goal.STRENGTH);
        for (Resource resource : Arrays.asList(Resource.CHAIR, Resource.WALL, Resource.SUPPORT)) {
            assertFalse("Must explicitly declare " + resource, engine.isCompatible(exercise("requires-" + resource,
                    Goal.STRENGTH, Environment.HOME, resource), profile));
        }
    }

    @Test public void dumbbellsDoNotEnableBarbell() {
        assertFalse(engine.isCompatible(exercise("barbell-squat", Goal.STRENGTH, Environment.HOME, Resource.BARBELL),
                profile("strength", Goal.STRENGTH).withResources(EnumSet.of(Resource.DUMBBELLS))));
    }

    @Test public void explicitResourcesPermitExerciseOnlyInItsEnvironment() {
        Exercise chair = exercise("chair-squat", Goal.STRENGTH, Environment.HOME, Resource.CHAIR);
        assertTrue(engine.isCompatible(chair, profile("strength", Goal.STRENGTH).withResources(EnumSet.of(Resource.CHAIR))));
        assertFalse(engine.isCompatible(chair, new TrainingProfile.Builder().environment(Environment.OUTDOORS)
                .resources(EnumSet.of(Resource.CHAIR)).build()));
    }

    @Test public void alternativesPreserveGoalAndExcludeSourceAndIncompatibleEquipment() {
        Exercise source = exercise("barbell", Goal.STRENGTH, Environment.HOME, Resource.BARBELL);
        Exercise compatible = exercise("bodyweight", Goal.STRENGTH, Environment.HOME);
        List<Exercise> result = engine.alternatives(source, profile("strength", Goal.STRENGTH), Arrays.asList(
                source, compatible, exercise("run", Goal.ENDURANCE, Environment.HOME),
                exercise("chair", Goal.STRENGTH, Environment.HOME, Resource.CHAIR)));
        assertEquals(1, result.size());
        assertEquals("bodyweight", result.get(0).getId());
    }

    @Test public void emptyCompatibleCatalogReturnsExplainedEmptyRoutine() {
        Routine routine = new RoutinePlanner().create(profile("strength", Goal.STRENGTH), Collections.singletonList(
                exercise("chair", Goal.STRENGTH, Environment.HOME, Resource.CHAIR)));
        assertTrue(routine.getItems().isEmpty());
        assertFalse(routine.getExplanation().trim().isEmpty());
    }

    @Test public void changingResourcesInvalidatesOldProposalEvenIfStillCompatible() {
        TrainingProfile original = profile("strength", Goal.STRENGTH).withResources(EnumSet.of(Resource.DUMBBELLS));
        Routine routine = new RoutinePlanner().create(original, Collections.singletonList(
                exercise("bodyweight", Goal.STRENGTH, Environment.HOME)));
        assertEquals(1, routine.getItems().size());
        assertTrue(routine.isValidFor(original));
        assertFalse(routine.isValidFor(original.withResources(Collections.emptySet())));
    }

    @Test public void specificSportCannotUseGenericComplementaryExercise() {
        Exercise complementary = new Exercise("squat", "Sentadilla", MetricType.STRENGTH,
                EnumSet.of(Goal.STRENGTH), EnumSet.of(Environment.HOME), Collections.emptySet(),
                Collections.singleton("football"), Exercise.Coverage.COMPLEMENTARY, false, "Ejemplo", Collections.emptyList());
        TrainingProfile specific = profile("football", Goal.STRENGTH);
        assertFalse(engine.isCompatible(complementary, specific));
        assertTrue(engine.isCompatible(complementary, specific.toBuilder().focus(Focus.COMPLEMENTARY).build()));
        Routine empty = new RoutinePlanner().create(specific, Collections.singletonList(complementary));
        assertTrue(empty.getItems().isEmpty());
        assertFalse(empty.getExplanation().isEmpty());
    }

    @Test public void plannerKeepsEnduranceMetricsAndStaysWithinAvailableTime() {
        Exercise run = new Exercise("run", "Carrera", MetricType.ENDURANCE, EnumSet.of(Goal.ENDURANCE),
                EnumSet.of(Environment.OUTDOORS), Collections.emptySet(), Collections.singleton("running"),
                Exercise.Coverage.SPECIFIC, false, "Ejemplo", Collections.emptyList());
        TrainingProfile profile = new TrainingProfile.Builder().sportId("running").goal(Goal.ENDURANCE)
                .environment(Environment.OUTDOORS).durationMinutes(15).build();
        Routine routine = new RoutinePlanner().create(profile, Collections.singletonList(run));
        assertEquals(1, routine.getItems().size());
        RoutineItem item = routine.getItems().get(0);
        assertEquals(MetricType.ENDURANCE, item.getExercise().getMetricType());
        assertTrue(item.getEstimatedMinutes() <= 15);
        assertTrue(item.getPrescription().containsKey("timeMinutes"));
        assertFalse(item.getPrescription().containsKey("repetitions"));
    }

    @Test public void callerResourceMutationCannotChangeProposalSnapshot() {
        Set<Resource> resources = EnumSet.of(Resource.CHAIR);
        TrainingProfile original = profile("strength", Goal.STRENGTH).withResources(resources);
        Routine routine = new RoutinePlanner().create(original, Collections.singletonList(
                exercise("chair", Goal.STRENGTH, Environment.HOME, Resource.CHAIR)));
        resources.clear();
        assertEquals(1, routine.getItems().size());
        assertTrue(original.getResources().contains(Resource.CHAIR));
        assertTrue(routine.isValidFor(original));
    }

    @Test public void plannerDeduplicatesStableIds() {
        Exercise bodyweight = exercise("bodyweight", Goal.STRENGTH, Environment.HOME);
        Routine routine = new RoutinePlanner().create(profile("strength", Goal.STRENGTH), Arrays.asList(bodyweight, bodyweight));
        assertEquals(1, routine.getItems().size());
    }

    @Test public void bandsAndMatAreExplicitResourcesRatherThanImplicitAccessories() {
        for (String name : Arrays.asList("BANDS", "MAT")) {
            assertTrue("Resource must be selectable: " + name,
                    Arrays.stream(Resource.values()).anyMatch(value -> value.name().equals(name)));
            Resource resource = Resource.valueOf(name);
            Exercise exercise = exercise("requires-" + name, Goal.STRENGTH, Environment.HOME, resource);
            assertFalse(engine.isCompatible(exercise, profile("strength", Goal.STRENGTH)));
            assertTrue(engine.isCompatible(exercise, profile("strength", Goal.STRENGTH).withResources(EnumSet.of(resource))));
        }
    }

    @Test public void materialCategoryUsesDeclaredEquipmentBeforeBodyweightWithinTimeBudget() {
        Exercise bodyweight = exercise("bodyweight", Goal.STRENGTH, Environment.HOME);
        Exercise dumbbells = exercise("dumbbells", Goal.STRENGTH, Environment.HOME, Resource.DUMBBELLS);
        TrainingProfile homeWithMaterial = new TrainingProfile.Builder().category(Category.HOME_EQUIPMENT)
                .durationMinutes(5).resources(EnumSet.of(Resource.DUMBBELLS)).build();
        Routine routine = new RoutinePlanner().create(homeWithMaterial, Arrays.asList(bodyweight, dumbbells));
        assertEquals(1, routine.getItems().size());
        assertEquals("dumbbells", routine.getItems().get(0).getExercise().getId());
        assertTrue(routine.getEstimatedMinutes() <= 5);
    }

    private TrainingProfile profile(String sportId, Goal goal) {
        return new TrainingProfile.Builder().sportId(sportId).goal(goal).environment(Environment.HOME).build();
    }

    private Exercise exercise(String id, Goal goal, Environment environment, Resource... resources) {
        return new Exercise(id, id, MetricType.STRENGTH, EnumSet.of(goal), EnumSet.of(environment),
                new HashSet<>(Arrays.asList(resources)), Collections.singleton("strength"),
                Exercise.Coverage.SPECIFIC, false, "Ejemplo sin revisión profesional", Collections.emptyList());
    }
}
