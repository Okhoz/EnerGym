package com.energym.app.ui;
import com.energym.app.R;
import com.energym.app.controller.AppController.InputField;
import android.widget.LinearLayout;
/** VISTA del perfil: cada campo remite texto/selección; construcción y validación pertenecen al controlador. */
public final class ProfileFragment extends ScreenFragment {
    public ProfileFragment(){super(R.layout.fragment_profile);}
    protected void content(){heading("PERSONALIZA","Tu perfil","Elige lo que tienes y el tiempo disponible. Tus textos se guardan, pero el plan local aún no los interpreta.");
        LinearLayout fields=card("Preferencias y contexto","Los recursos son independientes del lugar. Selecciona solo los disponibles.");
        for(InputField f:app.profileFields())if(f.bool)check(fields,f);else if(!f.values.isEmpty())choice(fields,f.key,f.label,f.values,f.labels,f.value,v->app.profileInput(f.key,v));else textField(fields,f.key,f.label,f.value,f.number,f.number,v->app.profileInput(f.key,v));
        action(rows,"Guardar perfil",!app.profileSaving(),app::saveProfile);
    }
}
