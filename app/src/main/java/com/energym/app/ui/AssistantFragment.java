package com.energym.app.ui;
import com.energym.app.R;
/** VISTA explícita del módulo pendiente: no simula una respuesta ni una conexión IA. */
public final class AssistantFragment extends ScreenFragment {
    public AssistantFragment(){super(R.layout.fragment_assistant);}
    protected void content(){heading("APRENDE Y AJUSTA","Asistente","Un espacio para resolver dudas sobre tu entrenamiento.");card("Integración pendiente","El asistente conversacional todavía no está conectado. Por ahora puedes crear propuestas locales y consultar la técnica de los ejercicios.");card("Tú decides el contexto","El permiso para usar el historial se configura en Tu perfil. Ningún dato se envía a IA en esta versión.");}
}
