package com.energym.app.ui;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;
import java.text.DateFormat;
import java.util.Date;
import android.widget.LinearLayout;
/** VISTA histórica: representa snapshots guardados, jamás sustituye resultados por la prescripción. */
public final class HistoryFragment extends ScreenFragment {
    public HistoryFragment(){super(R.layout.fragment_history);}
    protected void content(){heading("LO QUE HAS HECHO","Tu historial","Resultados realizados, con el perfil y los ejercicios de aquel entrenamiento.");
        if(app.history.isEmpty())card("Aún sin sesiones finalizadas","Guarda todos los ejercicios y finaliza tu primera sesión. Los borradores no cuentan como completados.");
        for(Session s:app.history){StringBuilder body=new StringBuilder(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,new java.util.Locale("es","ES")).format(new Date(s.getCompletedAt())));body.append("\n").append(s.getRoutine().getProfile().getDurationMinutes()).append(" min disponibles · ").append(AppController.label(s.getRoutine().getProfile().getGoal().name()));for(RoutineItem item:s.getRoutine().getItems()){body.append("\n\n").append(item.getExercise().getName()).append("\n").append(AppController.metrics(item.getExercise().getMetricType(),s.getResults().getOrDefault(item.getExercise().getId(),java.util.Collections.emptyMap())));}card("Sesión finalizada",body.toString());}
        for(Session draft:app.pending){LinearLayout row=card("Borrador pendiente",draft.getRoutine().getTitle()+" · "+draft.getResults().size()+" ejercicios guardados");action(row,"Continuar borrador",!app.sessions.busy,()->app.resume(draft));}
    }
}

