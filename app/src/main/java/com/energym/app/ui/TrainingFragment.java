package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.domain.Session;

import android.widget.LinearLayout;

/**
 * VISTA de preparación: presenta filtros y rutinas aceptadas sin generarlas por sí misma.
 */
public final class TrainingFragment extends ScreenFragment {
    public TrainingFragment() {
        super(R.layout.fragment_training);
    }

    protected void content() {
        heading("TU PLAN", "Entrenamiento", "Revisa la propuesta antes de aceptarla. Puedes sustituir ejercicios compatibles.");
        LinearLayout movement = card("Referencias de movimiento", "Vista previa técnica sin revisión profesional. Consulta primero la ejecución escrita; la animación disponible corresponde a su variante exacta.");
		for (com.energym.app.domain.Exercise exercise : app.availableAnimations()) action(movement, exercise.getName(), () -> app.openExercise(exercise));
        LinearLayout profile = card("Tus filtros", app.profileSummary());
        action(profile, app.profile == null ? "Configurar mi perfil" : "Editar perfil", () -> app.navigate(Screen.PROFILE));
        action(profile, "Crear propuesta", !app.loading && !app.profileSaving() && !app.routines.busy, app::createRoutine);
        if (app.routines.preview != null) {
            LinearLayout p = card("Propuesta sin aceptar", app.routines.preview.getItems().size() + " ejercicios · " + app.routines.preview.getEstimatedMinutes() + " min estimados");
            action(p, "Revisar propuesta", () -> app.navigate(Screen.ROUTINE));
        }
        if (app.routines.accepted != null) {
            LinearLayout accepted = card("Rutina aceptada", app.routines.accepted.getTitle() + "\n" + app.routines.accepted.getItems().size() + " ejercicios · versión " + app.routines.accepted.getVersionId());
            action(accepted, "Empezar sesión", !app.sessions.busy, app::startSession);
        }
        for (Session draft : app.pending) {
            LinearLayout row = card("Borrador recuperable", draft.getRoutine().getTitle() + "\n" + draft.getResults().size() + " ejercicios registrados");
            action(row, "Continuar borrador", !app.sessions.busy, () -> app.resume(draft));
        }
        if (app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT) {
            LinearLayout active = card("Sesión abierta", "Los valores escritos se conservan al cambiar de pantalla o rotar. Usa Guardar ejercicio para persistirlos.");
            action(active, "Volver a mi sesión", () -> app.navigate(Screen.SESSION));
        }
        card("Alcance de la propuesta", "Ejemplos locales sin revisión profesional. El plan usa disciplina, objetivo, entorno, recursos y tiempo; no interpreta aún tus textos ni crea un calendario semanal.");
    }
}
