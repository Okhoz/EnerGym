package com.energym.app.visual;
/** Las listas del recurso y la correlación se comprueban antes de cambiar el motor. */
public final class ViewerCommandValidator {
    /** Rechaza valores extraños, capacidades inventadas y ejercicio ajeno antes de tocar JNI. */
    public boolean validate(ViewerCommand c, ResourceManifest r) {
        if (c == null || r == null || c.action == null || !r.exerciseId.equals(c.exerciseId) || c.target == null
                || !Float.isFinite(c.amount) || !Float.isFinite(c.secondary)) return false;
        String capability = c.action.name().toLowerCase(java.util.Locale.ROOT);
        if (!r.capabilities.contains(capability)) return false;
        switch (c.action) {
            case VIEW: return r.views.contains(c.target) && c.amount == 0 && c.secondary == 0;
            case MARKER: return r.markers.containsKey(c.target) && c.amount == 0 && c.secondary == 0;
            case REGION: return r.regions.contains(c.target) && c.amount == 0 && c.secondary == 0;
            case LAYER: return false; // No hay mallas anatómicas separadas en el recurso inspeccionado.
            case SPEED: return c.target.isEmpty() && r.speeds.contains(c.amount) && c.secondary == 0;
            case ROTATE: return c.target.isEmpty() && Math.abs(c.amount) <= 180 && Math.abs(c.secondary) <= 90;
            case ZOOM: return c.target.isEmpty() && c.amount >= .5f && c.amount <= 2f && c.secondary == 0;
            default: return c.target.isEmpty() && c.amount == 0 && c.secondary == 0;
        }
    }
}
