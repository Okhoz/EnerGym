package com.energym.app.ui;
import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import android.widget.LinearLayout;
/** VISTA de inicio: resumen del estado y accesos a los eventos del controlador. */
public final class HomeFragment extends ScreenFragment {
    public HomeFragment(){super(R.layout.fragment_home);}
    protected void content(){heading("ENTRENA A TU RITMO","Hoy empieza contigo","Una propuesta según tu entorno. Un registro de lo que realmente haces.");
        LinearLayout next=card("Tu próximo paso",app.owner()==null?"Conecta tu cuenta Google o entra en la demo local para preparar tu primer entrenamiento.":app.profileSummary());
        if(app.owner()==null)action(next,"Elegir cuenta o demo",()->app.navigate(Screen.ACCOUNT));else action(next,app.profile==null?"Configurar mi perfil":"Editar mi perfil",()->app.navigate(Screen.PROFILE));
        LinearLayout training=card("Entrenamiento a tu medida","Gimnasio · casa con material · sin equipamiento · deporte\nDeclara tus recursos: el lugar no los añade automáticamente.");action(training,"Ver entrenamiento",()->app.navigate(Screen.TRAINING));
        card("Tu progreso, con contexto",app.history.size()+" sesiones finalizadas\n"+app.pending.size()+" borradores guardados\n"+state.syncText());
        if(!app.notice.isEmpty())card("Última acción",app.notice);
    }
}
