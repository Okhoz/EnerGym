package com.energym.app.sync;

import com.energym.app.auth.*;
import com.energym.app.data.SyncRecord;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.Executor;

/** MODEL Firestore Spark: lecturas SERVER paginadas y una transacción por registro pendiente.
 * Sin listeners persistentes ni caché en disco; SQLite es la única cola offline por cuenta. */
public final class FirebaseCloudGateway implements CloudSyncGateway {
    private static final int PAGE_SIZE=100,MAX_RESTORE_RECORDS=2000;
    private final AccountGateway accounts;
    private final FirebaseFirestore firestore;
    private final Executor worker;
    public FirebaseCloudGateway(GoogleAccountGateway accounts,Executor worker) {
        this.accounts=accounts;this.worker=java.util.Objects.requireNonNull(worker);
        firestore=accounts.isConfigured()?FirebaseFirestore.getInstance():null;
        if(firestore!=null)firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build());
    }
    private void requireOwner(String uid) {
        AccountUser current=accounts.getCurrentUser();
        if(firestore==null)throw new IllegalStateException("Firebase no configurado");
        if(current==null||!current.getUid().equals(uid))throw new IllegalStateException("La cuenta ya no está autenticada");
    }
    public void restore(String owner,Callback<List<RemoteRecord>> callback) {
        worker.execute(()->{try {requireOwner(owner);readPage(owner,SyncRecord.Kind.values(),0,null,new ArrayList<>(),callback);}catch(Exception e){callback.onFailure(e);}});
    }
    private void readPage(String owner,SyncRecord.Kind[] kinds,int index,DocumentSnapshot after,List<RemoteRecord> result,Callback<List<RemoteRecord>> callback) {
        try {
            requireOwner(owner);
            if(index==kinds.length){callback.onSuccess(Collections.unmodifiableList(result));return;}
            SyncRecord.Kind kind=kinds[index];
            if(kind==SyncRecord.Kind.PROFILE) {
                firestore.collection("users").document(owner).collection("profile").document("current").get(Source.SERVER).addOnCompleteListener(worker,task->{
                    try {
                        requireOwner(owner);
                        if(!task.isSuccessful())throw task.getException()==null?new IllegalStateException("Perfil remoto no disponible"):task.getException();
                        DocumentSnapshot doc=task.getResult();
                        if(doc.exists())result.add(CloudDocumentCodec.decode(owner,kind,doc.getId(),doc.getData()));
                        readPage(owner,kinds,index+1,null,result,callback);
                    }catch(Exception e){callback.onFailure(e);}
                });
                return;
            }
            Query query=firestore.collection("users").document(owner).collection(CloudDocumentCodec.collection(kind)).orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
            if(after!=null)query=query.startAfter(after);
            query.get(Source.SERVER).addOnCompleteListener(worker,task->{
                try {
                    requireOwner(owner);
                    if(!task.isSuccessful())throw task.getException()==null?new IllegalStateException("Restauración fallida"):task.getException();
                    List<DocumentSnapshot> docs=task.getResult().getDocuments();
                    for(DocumentSnapshot doc:docs){result.add(CloudDocumentCodec.decode(owner,kind,doc.getId(),doc.getData()));if(result.size()>MAX_RESTORE_RECORDS)throw new IllegalStateException("Respaldo supera 2000 registros; no se importó un lote parcial");}
                    if(docs.size()==PAGE_SIZE)readPage(owner,kinds,index,docs.get(docs.size()-1),result,callback);
                    else readPage(owner,kinds,index+1,null,result,callback);
                }catch(Exception e){callback.onFailure(e);}
            });
        }catch(Exception e){callback.onFailure(e);}
    }
    public void upload(SyncRecord record,long expectedVersion,Callback<UploadResult> callback) {
        worker.execute(()->{try {
            requireOwner(record.getOwnerUid());Map<String,Object> incoming=CloudDocumentCodec.encode(record,1);
            DocumentReference reference=firestore.collection("users").document(record.getOwnerUid()).collection(CloudDocumentCodec.collection(record.getKind()))
                    .document(CloudDocumentCodec.documentId(record.getKind(),record.getSyncId()));
            firestore.runTransaction(transaction->{
                requireOwner(record.getOwnerUid());DocumentSnapshot previous=transaction.get(reference);
                long version=0,updatedAt=0;
                if(previous.exists()) {
                    RemoteRecord old=CloudDocumentCodec.decode(record.getOwnerUid(),record.getKind(),reference.getId(),previous.getData());version=old.cloudVersion;updatedAt=old.record.getUpdatedAt();
                    Map<String,Object> prior=CloudDocumentCodec.encode(old.record,version);
                    if(incoming.get("payload").equals(prior.get("payload")))return new UploadResult(true,version); // Reintento idempotente sin otra escritura.
                    if(record.getKind()==SyncRecord.Kind.ROUTINE || !incoming.get("identity").equals(prior.get("identity")) || Boolean.TRUE.equals(prior.get("terminal")))return new UploadResult(false,version);
                }
                if(version!=expectedVersion)return new UploadResult(false,version);
                incoming.put("cloudVersion",version+1);incoming.put("updatedAt",Math.max(record.getUpdatedAt(),updatedAt+1));
                requireOwner(record.getOwnerUid());transaction.set(reference,incoming);return new UploadResult(true,version+1);
            }).addOnCompleteListener(worker,task->{
                try {requireOwner(record.getOwnerUid());if(task.isSuccessful())callback.onSuccess(task.getResult());else callback.onFailure(task.getException()==null?new IllegalStateException("Subida fallida"):task.getException());}
                catch(Exception e){callback.onFailure(e);}
            });
        }catch(Exception e){callback.onFailure(e);}});
    }
}
