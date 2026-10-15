package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ManualRoutineControllerTest {
	private final Exercise squat=exercise("squat",MetricType.STRENGTH,Goal.STRENGTH);
	private final Exercise plank=exercise("plank",MetricType.ISOMETRIC,Goal.STRENGTH);
	private final Exercise mobility=exercise("mobility",MetricType.MOBILITY,Goal.MOBILITY);
	private final Store store=new Store();
	private final List<Routine> saved=new ArrayList<>();
	private final ManualRoutineController controller=new ManualRoutineController(store,Arrays.asList(squat,plank,mobility),()->{},saved::add);
	private static Exercise exercise(String id,MetricType type,Goal goal){
		return new Exercise(id,id,type,EnumSet.of(goal),EnumSet.of(Environment.HOME),Collections.emptySet(),
			Collections.singleton("strength"),Exercise.Coverage.SPECIFIC,false,"Técnica",Collections.emptyList());
	}
	private void begin(){controller.activate("uid-a");controller.begin(new TrainingProfile.Builder().build());controller.title("Mi rutina");}
	@Test public void contextIsIndependentAndNothingWritesUntilSave(){
		TrainingProfile global=new TrainingProfile.Builder().build();
		controller.activate("uid-a");controller.begin(global);
		controller.contextInput("durationMinutes","45");
		assertEquals(30,global.getDurationMinutes());assertEquals(45,controller.context().getDurationMinutes());
		assertEquals(0,store.writes);
	}
	@Test public void duplicateUnknownAndIncompatibleExercisesAreRejected(){
		begin();assertTrue(controller.add("squat"));assertFalse(controller.add("squat"));
		assertFalse(controller.add("absent"));assertFalse(controller.add("mobility"));
		assertEquals(1,controller.entries().size());
	}
	@Test public void addedExerciseAndSavedSnapshotAlwaysUseCanonicalCatalogObject(){
		begin();assertFalse(controller.add("invented"));assertTrue(controller.add("squat"));
		assertSame(squat,controller.entries().get(0).exercise);
		controller.save();assertSame(squat,store.value.getItems().get(0).getExercise());
	}
	@Test public void manualEditorRejectsMoreThanTwentyExercises(){
		List<Exercise> exercises=new ArrayList<>();for(int i=0;i<21;i++)exercises.add(exercise("e"+i,MetricType.STRENGTH,Goal.STRENGTH));
		ManualRoutineController c=new ManualRoutineController(store,exercises,()->{},r->{});
		c.activate("uid-a");c.begin(null);for(int i=0;i<20;i++)assertTrue(c.add("e"+i));
		assertFalse(c.add("e20"));assertEquals(20,c.entries().size());assertFalse(c.error.isEmpty());
	}
	@Test public void allMetricTypesUseTheirOwnStrictPrescriptionSchema(){
		for(MetricType type:MetricType.values()){
			Exercise e=exercise(type.name(),type,Goal.STRENGTH);Store own=new Store();
			ManualRoutineController c=new ManualRoutineController(own,Collections.singletonList(e),()->{},r->{});
			c.activate("uid-a");c.begin(null);c.title(type.name());assertTrue(c.add(e.getId()));c.save();
			assertEquals(type.name(),1,own.writes);
			assertFalse(own.value.getItems().get(0).getPrescription().containsKey("effort"));
		}
	}
	@Test public void exercisesHaveTheirOwnMetricSchemaAndBodyweightCannotReceiveExternalLoad(){
		begin();controller.add("squat");controller.add("plank");
		assertTrue(controller.entries().get(1).values.containsKey("durationSeconds"));
		assertFalse(controller.entries().get(1).values.containsKey("repetitions"));
		controller.input(0,"loadKg","20");
		assertEquals("0",controller.entries().get(0).values.get("loadKg"));
		assertFalse(controller.error.isEmpty());
	}
	@Test public void removeAndReorderPreserveExerciseValues(){
		begin();controller.add("squat");controller.input(0,"repetitions","12");controller.add("plank");
		controller.move(1,-1);
		assertEquals("plank",controller.entries().get(0).exercise.getId());
		assertEquals("12",controller.entries().get(1).values.get("repetitions"));
		controller.remove(0);assertEquals("squat",controller.entries().get(0).exercise.getId());
	}
	@Test public void saveIsManualWithExplicitProgressionOffAndNeverWritesGlobalProfile(){
		begin();controller.add("squat");controller.progression(false);controller.save();
		assertEquals(1,store.writes);assertEquals(RoutineMetadata.Origin.MANUAL,store.value.getMetadata().getOrigin());
		assertFalse(store.value.isProgressionEnabled());assertTrue(saved.isEmpty());
		store.callback.success(store.value.withVersionId(7));assertEquals(1,saved.size());
		controller.save();assertEquals(1,store.writes);
	}
	@Test public void strictPrescriptionAndTotalTimeAreValidatedBeforeWriting(){
		begin();controller.add("squat");controller.input(0,"sets","2.5");controller.save();
		assertEquals(0,store.writes);assertFalse(controller.error.isEmpty());
		controller.input(0,"sets","2");controller.input(0,"estimatedMinutes","1");
		controller.input(0,"restSeconds","120");controller.save();assertEquals(0,store.writes);
		controller.input(0,"estimatedMinutes","31");controller.input(0,"restSeconds","60");controller.save();assertEquals(0,store.writes);
	}
	@Test public void changingContextKeepsDraftButRequiresIncompatibleItemsToBeResolved(){
		begin();controller.add("squat");controller.contextInput("goal","MOBILITY");controller.save();
		assertEquals(1,controller.entries().size());assertEquals(0,store.writes);assertFalse(controller.error.isEmpty());
	}
	@Test public void invalidContextTextCannotSilentlyUseThePreviousValidValue(){
		begin();controller.add("squat");controller.contextInput("durationMinutes","");controller.save();assertEquals(0,store.writes);
	}
	@Test public void switchingAccountsDropsDraftAndLateAcceptance(){
		begin();controller.add("squat");controller.save();AppStore.Callback<Routine> old=store.callback;
		controller.activate("uid-b");old.success(store.value.withVersionId(8));
		assertTrue(saved.isEmpty());assertTrue(controller.entries().isEmpty());assertFalse(controller.busy);
	}
	@Test public void editingIsBlockedWhileTheExplicitSaveIsPending(){
		begin();controller.add("squat");controller.save();controller.remove(0);controller.title("Changed");
		assertEquals(1,controller.entries().size());assertEquals("Mi rutina",controller.title());
	}
	static final class Store implements AppStore {
		int writes;Routine value;Callback<Routine> callback;
		public void acceptManual(Routine r,Callback<Routine> cb){writes++;value=r;callback=cb;}
		public void load(Callback<Snapshot> cb){}
		public void profile(TrainingProfile p,Callback<Void> cb){throw new AssertionError("No escribir perfil global");}
		public void accept(Routine r,Callback<Routine> cb){throw new AssertionError("Usar acceptManual");}
		public void draft(Session s,Callback<Long> cb){}
		public void openDraft(String id,Callback<OpenDraft> cb){}
		public void finish(long id,Callback<Void> cb){}
	}
}
