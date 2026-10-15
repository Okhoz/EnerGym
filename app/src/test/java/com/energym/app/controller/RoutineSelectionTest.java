package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RoutineSelectionTest {
	private final TrainingProfile profile = new TrainingProfile.Builder().build();
	private final Exercise exercise = new Exercise("squat", "Sentadilla", MetricType.STRENGTH,
		EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
		Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
		"Técnica", Collections.emptyList());
	private Routine routine(String id, long version) {
		Map<String,String> p = new LinkedHashMap<>();
		p.put("sets","2"); p.put("repetitions","8"); p.put("loadKg","0"); p.put("restSeconds","60");
		return new Routine(id, version, id, profile, Collections.singletonList(new RoutineItem(exercise,p,5,"Manual")),"Ejemplo");
	}
	private final Store store = new Store();
	private AppController app() {
		Sport sport = new Sport("strength","Fuerza",Sport.CoverageStatus.EXAMPLE_UNREVIEWED,Sport.CoverageStatus.PENDING_VALIDATION);
		AppController app = new AppController(store,new CatalogRepository(1,Collections.singletonList(sport),Collections.singletonList(exercise)));
		app.activate("uid-a");
		return app;
	}
	@Test public void reloadKeepsSelectedProposalWhenNewerVersionsArrive() {
		Routine chosen=routine("chosen",1), newer=routine("newer",2);
		store.routines=Collections.singletonList(chosen);
		AppController app=app();
		app.selectRoutine("chosen");
		store.routines=Arrays.asList(newer,chosen.withVersionId(10));
		app.reload();
		assertEquals("chosen",app.routines.accepted.getProposalId());
		assertEquals(10,app.routines.accepted.getVersionId());
	}
	@Test public void missingSelectedRoutineDoesNotSilentlyChooseAnotherWhileRestoring() {
		Routine chosen=routine("chosen",1), other=routine("other",2);
		store.routines=Collections.singletonList(chosen);
		AppController app=app();
		app.selectRoutine("chosen");
		store.routines=Collections.singletonList(other);
		app.reload();
		assertNull(app.routines.accepted);
		store.routines=Arrays.asList(other,chosen.withVersionId(99));
		app.reload();
		assertEquals("chosen",app.routines.accepted.getProposalId());
	}
	@Test public void automaticDefaultAdoptsTheLatestRestoredOrderingUntilTheUserChooses() {
		Routine old=routine("old",99), latest=routine("latest",2);
		store.routines=Collections.singletonList(old);AppController app=app();
		store.routines=Arrays.asList(latest,old);app.reload();
		assertSame(latest,app.routines.accepted);
		app.startSession();assertSame(latest,store.session.getRoutine());
	}
	@Test public void missingAutomaticDefaultAdoptsTheAvailableRoutine() {
		Routine old=routine("old",1), available=routine("available",2);
		store.routines=Collections.singletonList(old);AppController app=app();
		store.routines=Collections.singletonList(available);app.reload();
		assertSame(available,app.routines.accepted);
	}
	@Test public void directRoutineControllerAcceptanceBecomesExplicitBeforeLaterReloads() {
		Routine old=routine("old",1), proposal=routine("accepted",0), accepted=proposal.withVersionId(2);
		store.routines=Collections.singletonList(old);AppController app=app();
		app.routines.preview=proposal;app.routines.accept();assertNotNull(store.accepting);
		store.accepting.success(accepted);
		store.routines=Arrays.asList(old,accepted);app.reload();
		assertSame(accepted,app.routines.accepted);
		store.routines=Collections.singletonList(old);app.reload();assertNull(app.routines.accepted);
		store.routines=Arrays.asList(old,accepted.withVersionId(7));app.reload();
		assertEquals("accepted",app.routines.accepted.getProposalId());assertEquals(7,app.routines.accepted.getVersionId());
	}
	@Test public void accountChangeClearsThePreviousExplicitSelection() {
		Routine first=routine("first",1), chosen=routine("chosen",2), otherAccount=routine("other-account",3);
		store.routines=Arrays.asList(first,chosen);AppController app=app();app.selectRoutine("chosen");
		store.routines=Collections.singletonList(otherAccount);app.activate("uid-b");
		assertSame(otherAccount,app.routines.accepted);
	}
	@Test public void startingSessionUsesTheChosenSnapshotEvenWhenGlobalProfileChanges() {
		Routine chosen=routine("chosen",1);
		store.routines=Collections.singletonList(chosen);
		AppController app=app();
		app.profile=profile.toBuilder().goal(TrainingProfile.Goal.MOBILITY).build();
		app.startSession();
		assertSame(chosen,store.session.getRoutine());
	}
	@Test public void explicitSelectionControlsSessionAndProgressionEvenAfterReload() {
		Routine first=routine("first",1), chosen=routine("chosen",2).withMetadata(
			new RoutineMetadata(RoutineMetadata.Origin.MANUAL,false));
		store.routines=Arrays.asList(first,chosen);AppController app=app();
		app.selectRoutine("chosen");app.reload();assertSame(chosen,app.routines.accepted);
		assertNull(app.progression());app.startSession();assertSame(chosen,store.session.getRoutine());
	}
	@Test public void acceptingANewRoutineSelectsItBeforeTheRefresh() {
		Routine first=routine("first",1), newer=routine("newer",2);
		store.routines=Collections.singletonList(first);AppController app=app();
		store.routines=Arrays.asList(first,newer);app.routineAccepted(newer);
		assertSame(newer,app.routines.accepted);
	}
	@Test public void existingDraftOfAnotherRoutineCannotBePresentedAsTheSelectedSession() {
		Routine first=routine("first",1), chosen=routine("chosen",2);
		store.routines=Arrays.asList(first,chosen);AppController app=app();app.startSession();
		app.navigate(AppController.Screen.TRAINING);app.selectRoutine("chosen");app.startSession();
		assertSame(first,store.session.getRoutine());assertEquals(AppController.Screen.TRAINING,app.screen);
		assertFalse(app.error.isEmpty());
	}
	@Test public void openingSelectedRoutineDoesNotDiscardAnUnacceptedProposal() {
		Routine accepted=routine("accepted",1), preview=routine("pending",0);
		store.routines=Collections.singletonList(accepted);AppController app=app();
		app.routines.preview=preview;app.viewSelectedRoutine();assertSame(preview,app.routines.preview);
		assertEquals(AppController.Screen.HOME,app.screen);
	}
	static class Store implements AppStore {
		List<Routine> routines=Collections.emptyList(); Session session; Callback<Routine> accepting;
		public void load(Callback<Snapshot> cb){cb.success(new Snapshot(new TrainingProfile.Builder().build(),routines,Collections.emptyList(),Collections.emptyList()));}
		public void profile(TrainingProfile p,Callback<Void> cb){throw new AssertionError("No cambiar perfil global");}
		public void accept(Routine r,Callback<Routine> cb){accepting=cb;}
		public void draft(Session s,Callback<Long> cb){session=s;cb.success(7L);}
		public void openDraft(String id,Callback<OpenDraft> cb){}
		public void finish(long id,Callback<Void> cb){}
	}
}
