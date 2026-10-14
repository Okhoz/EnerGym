package com.energym.app.visual;

import com.energym.app.controller.AppController.Screen;
import com.energym.app.domain.Exercise;
import java.util.*;
import java.util.function.Consumer;

/** Autoriza una sola ventana pendiente; revoca antes de solicitar su cierre remoto. */
public final class VisualizationController {
	public static final class Request {
		public final String lease, exerciseId;
		private Request(String id) { lease = UUID.randomUUID().toString(); exerciseId = id; }
	}
	public interface Leases { void publish(Request request); void revoke(String lease); }
	private final ExerciseResourcePolicy policy;
	private final Leases leases;
	private final Runnable close;
	private Consumer<Request> launch;
	private String owner, originOwner, lastConversation, lastRequest;
	private long revision, originRevision;
	private Screen screen, originScreen;
	private Exercise selected, origin;
	private Request pending;
	private boolean valid;
	public VisualizationController(ExerciseResourcePolicy policy, Leases leases, Runnable close) {
		this.policy = policy; this.leases = leases; this.close = close;
	}
	public void bind(Consumer<Request> launch) { this.launch = launch; }
	public void context(String owner, long revision, Screen screen, Exercise selected) {
		this.owner = owner; this.revision = revision; this.screen = screen; this.selected = selected;
		if (valid && (!Objects.equals(originOwner, owner) || originRevision != revision || originScreen != screen || !ExerciseResourcePolicy.same(origin, selected))) revoke();
	}
	public boolean open(Exercise exercise) {
		if (screen != Screen.EXERCISE) return false;
		return openCurrent(exercise);
	}
	public boolean assistant(Exercise exercise, Exercise explanation, String conversation, String request, long originRevision) {
		if (screen != Screen.ASSISTANT || originRevision != revision || conversation == null || request == null
			|| conversation.isEmpty() || request.isEmpty() || !ExerciseResourcePolicy.same(exercise, explanation)
			|| (conversation.equals(lastConversation) && request.equals(lastRequest))) return false;
		lastConversation = conversation; lastRequest = request;
		return openCurrent(exercise);
	}
	private boolean openCurrent(Exercise exercise) {
		if (owner == null || launch == null || pending != null || !policy.available(exercise) || !ExerciseResourcePolicy.same(exercise, selected)) return false;
		originOwner = owner; originRevision = revision; originScreen = screen; origin = exercise;
		pending = new Request(exercise.getId()); valid = true; leases.publish(pending);
		try { launch.accept(pending); }
		catch (RuntimeException e) { result(pending.lease); return false; }
		return true;
	}
	public void revoke() {
		if (!valid) return;
		valid = false; leases.revoke(pending.lease); close.run();
	}
	public void result(String lease) {
		if (pending == null || !pending.lease.equals(lease)) return;
		if (valid) { valid = false; leases.revoke(pending.lease); }
		pending = null; origin = null; originOwner = null;
	}
}
