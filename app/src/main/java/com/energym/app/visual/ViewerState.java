package com.energym.app.visual;
/** Instantánea restaurable de controles; no guarda objetos nativos ni contexto de cuenta. */
public final class ViewerState {
    public final boolean ready, active, playing, closed;
    public final float playhead, speed, yaw, pitch, zoom;
    public final String error;
    public ViewerState(boolean ready, boolean active, boolean playing, boolean closed, float playhead, float speed, float yaw, float pitch, float zoom, String error) {
        this.ready=ready;this.active=active;this.playing=playing;this.closed=closed;this.playhead=playhead;this.speed=speed;this.yaw=yaw;this.pitch=pitch;this.zoom=zoom;this.error=error;
    }
}
