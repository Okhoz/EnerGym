package com.energym.app.sync;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.auth.*;
import com.energym.app.data.*;
import com.energym.app.domain.*;
import com.energym.app.catalog.CatalogRepository;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Integración local Android real: codec org.json/SQLite y rama sin configuración. No autentica en nube. */
@RunWith(AndroidJUnit4.class)
public class FirebaseIntegrationTest {
    private Context context;private EnerGymDatabase db;private String uid;
    @Before public void before(){context=ApplicationProvider.getApplicationContext();uid="firebase-test-"+UUID.randomUUID();db=new EnerGymDatabase(context,uid);}
    @After public void after(){db.clearLocalData();db.close();}
    private SyncRecord record(TrainingProfile p,long revision,long time){return new SyncRecord(SyncRecord.Kind.PROFILE,"profile",uid,SnapshotCodec.encodeProfile(p),revision,time,SyncRecord.State.CLEAN);}
    private Routine routine(){return new Routine("routine-1",45,"Snapshot",new TrainingProfile.Builder().build(),Collections.emptyList(),"Fixture de frontera JSON");}
    private Routine acceptedRoutine() throws Exception {
        TrainingProfile profile=new TrainingProfile.Builder().preferences("Snapshot restaurable").build();db.saveProfile(profile);
        RoutineRepository repository=new RoutineRepository(db);
        long id=repository.saveAccepted(new RoutinePlanner().create(profile,CatalogRepository.load(context).getExercises()));return repository.load(id);
    }
    private SyncRecord cloudRoundTrip(SyncRecord record) {
        return CloudDocumentCodec.decode(uid,record.getKind(),CloudDocumentCodec.documentId(record.getKind(),record.getSyncId()),CloudDocumentCodec.encode(record,1)).record;
    }
    private List<SyncRecord> cloudRecords(){List<SyncRecord> rs=new ArrayList<>();for(SyncRecord r:db.pendingSyncRecords())rs.add(cloudRoundTrip(r));return rs;}
    private void restorePortable(List<SyncRecord> records){
        try {new SqliteSyncStore(db).importRecords(records);}catch(IllegalArgumentException error){fail("El snapshot portátil válido debe restaurarse sin IDs locales ficticios: "+error.getMessage());}
    }
    @Test public void cloudDraftRoundTripRestoresAcceptedSnapshotAndRemainsEditableInEitherOrder() throws Exception {
        Routine original=acceptedRoutine();Session draft=new Session(original);new SessionRepository(db).saveDraft(draft);
        List<SyncRecord> remote=cloudRecords();SyncRecord remoteSession=remote.stream().filter(r->r.getKind()==SyncRecord.Kind.SESSION).findFirst().get();
        assertEquals(0,SnapshotCodec.decodeSession(remoteSession.getPayload()).getRoutine().getVersionId());
        for(int order=0;order<2;order++){
            db.clearLocalData();List<SyncRecord> batch=new ArrayList<>(remote);batch.sort(Comparator.comparingInt(r->r.getKind()==SyncRecord.Kind.SESSION?0:1));
            if(order==1)Collections.reverse(batch);restorePortable(batch);
            SessionRepository repository=new SessionRepository(db);assertEquals(1,repository.pending().size());assertTrue(db.pendingSyncRecords().isEmpty());
            Session restored=repository.pending().get(0);assertTrue(restored.getRoutine().getVersionId()>0);assertNotEquals(original.getVersionId(),restored.getRoutine().getVersionId());
            assertEquals(draft.getSyncId(),restored.getSyncId());assertEquals(original.getItems().get(0).getExercise().getDescription(),restored.getRoutine().getItems().get(0).getExercise().getDescription());
            String exercise=restored.getRoutine().getItems().get(0).getExercise().getId();long localId=repository.saveDraft(restored.withResult(exercise,Collections.singletonMap("repetitions","22")));
            assertEquals("22",repository.load(localId).getResults().get(exercise).get("repetitions"));assertEquals(1,db.pendingSyncRecords().size());
            assertEquals(CloudDocumentCodec.encode(remoteSession,1).get("identity"),CloudDocumentCodec.encode(db.pendingSyncRecords().get(0),2).get("identity"));
        }
    }
    @Test public void cloudFinishedSessionRoundTripRestoresHistoryIdempotently() throws Exception {
        Routine routine=acceptedRoutine();String exercise=routine.getItems().get(0).getExercise().getId();
        SessionRepository repository=new SessionRepository(db);long id=repository.saveDraft(new Session(routine).withResult(exercise,Collections.singletonMap("repetitions","31")));repository.finish(id);
        Session original=repository.history().get(0);List<SyncRecord> remote=cloudRecords();Collections.reverse(remote);db.clearLocalData();restorePortable(remote);restorePortable(remote);
        assertEquals(1,repository.history().size());assertTrue(repository.pending().isEmpty());assertTrue(db.pendingSyncRecords().isEmpty());Session restored=repository.history().get(0);
        assertEquals(original.getSyncId(),restored.getSyncId());assertEquals(original.getCompletedAt(),restored.getCompletedAt());assertEquals("31",restored.getResults().get(exercise).get("repetitions"));
        assertTrue(restored.getRoutine().getVersionId()>0);assertEquals(Session.Status.FINISHED,restored.getStatus());
    }
    @Test public void standaloneCloudSessionMaterializesAcceptedRoutineAndCanBeEdited() throws Exception {
        Session original=new Session(acceptedRoutine());new SessionRepository(db).saveDraft(original);
        SyncRecord remote=cloudRecords().stream().filter(r->r.getKind()==SyncRecord.Kind.SESSION).findFirst().get();db.clearLocalData();restorePortable(Collections.singletonList(remote));
        assertEquals(1,new RoutineRepository(db).versions().size());assertTrue(db.pendingSyncRecords().isEmpty());
        SessionRepository repository=new SessionRepository(db);Session restored=repository.pending().get(0);assertTrue(restored.getRoutine().getVersionId()>0);
        String exercise=restored.getRoutine().getItems().get(0).getExercise().getId();long id=repository.saveDraft(restored.withResult(exercise,Collections.singletonMap("loadKg","3")));
        assertEquals("3",repository.load(id).getResults().get(exercise).get("loadKg"));
    }
    @Test public void profileRoundtripRetainsAllFieldsAndConsent(){
        TrainingProfile p=new TrainingProfile.Builder().preferences("español €").limitations("sin salto").historyConsent(false).build();
        Map<String,Object> document=CloudDocumentCodec.encode(record(p,4,1000),3);
        CloudSyncGateway.RemoteRecord restored=CloudDocumentCodec.decode(uid,SyncRecord.Kind.PROFILE,"current",document);
        assertEquals(p,SnapshotCodec.decodeProfile(restored.record.getPayload()));assertEquals(3,restored.cloudVersion);
    }
    @Test public void routineCloudPayloadExcludesDeviceLocalId(){
        SyncRecord r=new SyncRecord(SyncRecord.Kind.ROUTINE,"routine-1",uid,SnapshotCodec.encodeRoutine(routine()),1,1000,SyncRecord.State.PENDING);
        Map<String,Object> doc=CloudDocumentCodec.encode(r,1);
        assertEquals(0,SnapshotCodec.decodeRoutine((String)doc.get("payload")).getVersionId());
        assertEquals("routines",CloudDocumentCodec.collection(r.getKind()));assertEquals("routine-1",CloudDocumentCodec.documentId(r.getKind(),r.getSyncId()));
    }
    @Test public void sessionIdentityDoesNotDependOnLocalRoutineIdOrFinishing(){
        Session first=new Session("session-1",routine(),Collections.emptyMap(),1000,0,Session.Status.DRAFT);
        Session recovered=first.withRoutineSnapshot(routine().withVersionId(999)).finishedAt(2000);
        Map<String,Object> a=CloudDocumentCodec.encode(new SyncRecord(SyncRecord.Kind.SESSION,"session-1",uid,SnapshotCodec.encodeSession(first),1,1000,SyncRecord.State.PENDING),1);
        Map<String,Object> b=CloudDocumentCodec.encode(new SyncRecord(SyncRecord.Kind.SESSION,"session-1",uid,SnapshotCodec.encodeSession(recovered),2,2000,SyncRecord.State.PENDING),2);
        assertEquals(a.get("identity"),b.get("identity"));assertEquals(false,a.get("terminal"));assertEquals(true,b.get("terminal"));
    }
    @Test public void foreignAndCorruptDocumentsAndLargeSnapshotsRejected(){
        Map<String,Object> doc=CloudDocumentCodec.encode(record(new TrainingProfile.Builder().build(),1,1000),1);
        assertThrows(IllegalArgumentException.class,()->CloudDocumentCodec.decode("bob",SyncRecord.Kind.PROFILE,"current",doc));
        doc.put("terminal",true);assertThrows(IllegalArgumentException.class,()->CloudDocumentCodec.decode(uid,SyncRecord.Kind.PROFILE,"current",doc));
        TrainingProfile huge=new TrainingProfile.Builder().preferences(String.join("",Collections.nCopies(210000,"€"))).build();
        assertThrows(IllegalArgumentException.class,()->CloudDocumentCodec.encode(record(huge,1,1000),1));
    }
    @Test public void latestServerRestoreOverridesCleanLocalDespiteDeviceClockSkew(){
        db.saveProfile(new TrainingProfile.Builder().preferences("old local").build());for(SyncRecord r:db.pendingSyncRecords())db.acknowledge(r.getKind(),r.getSyncId(),r.getRevision());
        new SqliteSyncStore(db).importRecords(Collections.singletonList(record(new TrainingProfile.Builder().preferences("current server").build(),1,1)));
        assertEquals("current server",db.loadProfile().getPreferences());assertTrue(db.pendingSyncRecords().isEmpty());
    }
    @Test public void pendingLocalWinsRealSqliteRestoreAndOlderAckStaysPending(){
        SqliteSyncStore store=new SqliteSyncStore(db);db.saveProfile(new TrainingProfile.Builder().preferences("first").build());SyncRecord older=db.pendingSyncRecords().get(0);
        db.saveProfile(new TrainingProfile.Builder().preferences("latest local").build());store.importRecords(Collections.singletonList(record(new TrainingProfile.Builder().preferences("remote").build(),99,System.currentTimeMillis()+10000)));
        assertEquals("latest local",db.loadProfile().getPreferences());assertFalse(store.acknowledge(older));assertEquals(1,store.pending().size());
    }
    @Test public void absentConfigurationFailsExplicitlyWithoutCloudRequests(){
        int web=context.getResources().getIdentifier("default_web_client_id","string",context.getPackageName());
        Assume.assumeTrue("Esta prueba verifica el build deliberadamente sin configuración",web==0);
        GoogleAccountGateway accounts=new GoogleAccountGateway(context,Runnable::run);assertFalse(accounts.isConfigured());assertNull(accounts.getCurrentUser());
        AtomicReference<Exception> error=new AtomicReference<>();accounts.signInWithGoogleIdToken("not-credentials",new AccountGateway.Callback(){public void onSuccess(AccountUser u){fail("No debe crear usuarios");}public void onFailure(Exception e){error.set(e);}});assertNotNull(error.get());
        error.set(null);new FirebaseCloudGateway(accounts,Runnable::run).restore(uid,new CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>>(){public void onSuccess(List<CloudSyncGateway.RemoteRecord> rs){fail("No hay respaldo configurado");}public void onFailure(Exception e){error.set(e);}});assertNotNull(error.get());
    }
}
