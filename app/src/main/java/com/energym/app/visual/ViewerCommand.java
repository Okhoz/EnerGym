package com.energym.app.visual;

/**
 * Orden tipada y correlacionada; nunca ejecuta texto o código del asistente.
 */
public final class ViewerCommand {
	public enum Action {PLAY, PAUSE, SPEED, VIEW, MARKER, ROTATE, ZOOM, REGION, LAYER}

	public final String exerciseId, target;
	public final Action action;
	public final float amount, secondary;

	public ViewerCommand(String exerciseId, Action action, String target, float amount, float secondary) {
		this.exerciseId = exerciseId;
		this.action = action;
		this.target = target;
		this.amount = amount;
		this.secondary = secondary;
	}
}
