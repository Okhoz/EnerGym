package com.energym.app.nutrition;

import com.energym.app.assistant.OnDeviceModel;
import com.energym.app.assistant.TrainerPrompt;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * CONTROLADOR de la dieta personalizada: pide el menú a la IA del móvil con los datos del formulario y, si la IA
 * no está lista o responde algo no válido o con un alimento prohibido, usa el menú base local. Todo en el móvil.
 */
public final class DietPlanner {
	/** Guarda el último menú de cada cuenta en el propio móvil. */
	public interface Store {
		String load(String owner);

		void save(String owner, String json);
	}

	public DietPlan plan;
	public boolean busy;
	/** El menú se muestra plegado; se despliega al pedirlo o al crear uno nuevo. */
	public boolean expanded;
	public String status = "";
	private final OnDeviceModel model;
	private final Store store;
	private final Executor delivery;
	private final Runnable changed;
	private final Supplier<String> training;
	private String owner;
	private long generation;
	private int variant;

	public DietPlanner(OnDeviceModel model, Store store, Executor delivery, Runnable changed, Supplier<String> training) {
		this.model = model;
		this.store = store;
		this.delivery = delivery;
		this.changed = changed;
		this.training = training == null ? () -> "" : training;
	}

	public void activate(String owner) {
		if (Objects.equals(this.owner, owner))
			return;
		generation++;
		this.owner = owner;
		busy = false;
		status = "";
		plan = owner == null ? null : DietPlan.decode(store.load(owner));
	}

	/** Crea (o vuelve a crear) el menú del día con los datos físicos y preferencias guardados. */
	public void create(BodyProfile body) {
		BodyProfile.Targets targets = body == null ? null : body.targets();
		if (owner == null || busy || targets == null)
			return;
		busy = true;
		status = "";
		variant++;
		long token = generation;
		List<String> previous = new ArrayList<>();
		if (plan != null)
			for (DietPlan.Meal m : plan.meals)
				previous.add(m.dish);
		changed.run();
		if (model == null) {
			finish(token, null, body, targets);
			return;
		}
		String prompt = DietRules.prompt(body, targets, training.get(), previous);
		model.status(new OnDeviceModel.Result<Integer>() {
			public void success(Integer state) {
				if (state == null || state != OnDeviceModel.AVAILABLE) {
					finish(token, null, body, targets);
					return;
				}
				model.generate(prompt, new OnDeviceModel.Result<String>() {
					public void success(String output) {
						finish(token, output, body, targets);
					}

					public void failure(String code) {
						finish(token, null, body, targets);
					}
				});
			}

			public void failure(String code) {
				finish(token, null, body, targets);
			}
		});
	}

	/**
	 * Valida la respuesta de la IA (formato, alimentos prohibidos) y cuadra las raciones con el objetivo; si no
	 * sirve, menú base local. Se entrega en el hilo de la interfaz.
	 */
	private void finish(long token, String output, BodyProfile body, BodyProfile.Targets targets) {
		long now = System.currentTimeMillis();
		DietPlan ai = parse(output, model == null ? "" : model.label(), now);
		String note = "";
		if (ai != null && ai.mentionsAny(DietRules.banned(body))) {
			ai = null;
			note = "La IA propuso un alimento que evitas; te dejo un menú base seguro.";
		} else if (ai != null && (ai = ai.scaledTo(targets.kcal)) == null)
			note = "El menú de la IA no cuadraba con tus calorías; te dejo un menú base con tus datos.";
		else if (ai != null && body.budget > 0 && ai.cost() > body.budget * 1.25) {
			ai = null;
			note = "El menú de la IA superaba tu presupuesto; te dejo un menú económico con tus datos.";
		} else if (ai == null && output != null)
			note = "La IA no devolvió un menú válido; te dejo un menú base con tus datos.";
		else if (ai == null)
			note = "La IA del móvil aún no está lista; te dejo un menú base con tus datos.";
		DietPlan result = ai != null ? ai : DietRules.local(body, targets, variant, now);
		if (note.isEmpty() && body.budget > 0 && result.cost() > body.budget * 1.05)
			note = "Con " + NutritionController.format(body.budget) + " € no llega para todo: este es el menú más " +
				"económico que cuadra con tus calorías.";
		String message = note;
		delivery.execute(() -> {
			if (token != generation)
				return;
			busy = false;
			expanded = true;
			plan = result;
			status = message;
			store.save(owner, result.encode());
			changed.run();
		});
	}

	static DietPlan parse(String output, String label, long now) {
		if (output == null)
			return null;
		Map<String, Object> json = TrainerPrompt.extractJson(output);
		return DietPlan.decode(json, "Creado con IA" + (label == null || label.isEmpty() ? "" : " (" + label + ")"), now);
	}
}
