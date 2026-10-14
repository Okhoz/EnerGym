package com.energym.app.controller;
import com.energym.app.domain.*;
import java.util.*;
/** Puerto MVC asíncrono: el adaptador Android ejecuta SQLite fuera de la vista. */
public interface AppStore {
    interface Callback<T> { void success(T value); void failure(Exception error); }
    final class Snapshot {
        public final TrainingProfile profile; public final List<Routine> routines; public final List<Session> pending,history;
        public Snapshot(TrainingProfile p,List<Routine> r,List<Session> d,List<Session> h){profile=p;routines=r;pending=d;history=h;}
    }
    /** Identidad local y snapshot leídos del scope actual; nunca se obtienen escribiendo un borrador cacheado. */
    final class OpenDraft {
        public final long id;public final Session session;
        public OpenDraft(long id,Session session){this.id=id;this.session=session;}
    }
    void load(Callback<Snapshot> callback);
    void profile(TrainingProfile value,Callback<Void> callback);
    void accept(Routine value,Callback<Routine> callback);
    void draft(Session value,Callback<Long> callback);
    void openDraft(String syncId,Callback<OpenDraft> callback);
    void finish(long id,Callback<Void> callback);
}
