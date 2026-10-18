package com.energym.app.nutrition;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.assistant.OnDeviceModel;
import com.energym.app.assistant.StrictJson;
import com.energym.app.assistant.TrainerPrompt;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CONTROLADOR de la dieta del mes: cada día del mes tiene su menú desde el principio (menú base del móvil, al
 * instante) y la IA del móvil mejora el día que se pida. El plan se va actualizando solo con lo que la persona
 * anota: si cambian su peso, sus preferencias o lo que ha comido, los días siguientes se recalculan, con una
 * compensación suave de calorías y el presupuesto que queda del mes. Todo en el teléfono.
 */
public final class DietPlanner {
	/** Guarda el plan de cada cuenta en el propio móvil. */
	public interface Store {
		String load(String owner);

		void save(String owner, String json);
	}

	/** Menú del día elegido (hoy por defecto). */
	public DietPlan plan;
	public LocalDate selected;
	public boolean busy;
	/** El menú se muestra plegado; se despliega al pedirlo o al crear uno nuevo. */
	public boolean expanded;
	public String status = "";
	/** Explicación del ajuste del día elegido (compensación de calorías, presupuesto restante). */
	public String adjustment = "";
	private final TreeMap<LocalDate, DietPlan> days = new TreeMap<>();
	private final OnDeviceModel model;
	private final Store store;
	private final Executor delivery;
	private final Runnable changed;
	private final Supplier<String> training;
	private final Supplier<LocalDate> clock;
	private final Function<LocalDate, Double> logged;
	private YearMonth month;
	private String owner, signature = "";
	private long generation;
	private int variant;

	public DietPlanner(OnDeviceModel model, Store store, Executor delivery, Runnable changed, Supplier<String> training) {
		this(model, store, delivery, changed, training, LocalDate::now, day -> 0.0);
	}

	public DietPlanner(OnDeviceModel model, Store store, Executor delivery, Runnable changed, Supplier<String> training,
		Supplier<LocalDate> clock, Function<LocalDate, Double> logged) {
		this.model = model;
		this.store = store;
		this.delivery = delivery;
		this.changed = changed;
		this.training = training == null ? () -> "" : training;
		this.clock = clock;
		this.logged = logged == null ? day -> 0.0 : logged;
	}

	public void activate(String owner) {
		if (Objects.equals(this.owner, owner))
			return;
		generation++;
		this.owner = owner;
		busy = false;
		status = "";
		adjustment = "";
		signature = "";
		days.clear();
		month = null;
		selected = clock.get();
		if (owner != null)
			load(store.load(owner));
		plan = days.get(selected);
	}

	// ---- Plan del mes ----

	public YearMonth month() {
		return month == null ? YearMonth.from(clock.get()) : month;
	}

	/** Días del mes con menú, en orden. */
	public SortedMap<LocalDate, DietPlan> days() {
		return Collections.unmodifiableSortedMap(days);
	}

	/** Coste estimado de los menús del mes ya planificados (negativo si no se conoce). */
	public double monthCost() {
		double total = 0;
		for (DietPlan p : days.values()) {
			if (p.cost() < 0)
				return -1;
			total += p.cost();
		}
		return total;
	}

	public void select(LocalDate day) {
		if (day == null || !YearMonth.from(day).equals(month()))
			return;
		selected = day;
		plan = days.get(day);
		status = "";
		changed.run();
	}

	/**
	 * Mantiene el mes al día: crea los menús que falten y rehace los días que vienen (los que no mejoró la IA)
	 * cuando cambian los datos de la persona: perfil, peso, preferencias o lo comido los últimos días.
	 */
	public void sync(BodyProfile body) {
		if (owner == null || body == null || body.targets() == null || busy)
			return;
		LocalDate today = clock.get();
		YearMonth current = YearMonth.from(today);
		String next = current + "|" + NutritionRepositoryKey.of(body) + "|" + recentLogged(today);
		if (next.equals(signature) && current.equals(month) && days.containsKey(today))
			return;
		boolean newMonth = !current.equals(month);
		if (newMonth) {
			days.clear();
			month = current;
			if (selected == null || !YearMonth.from(selected).equals(current))
				selected = today;
		}
		boolean dataChanged = !next.equals(signature);
		signature = next;
		long now = System.currentTimeMillis();
		for (LocalDate day = today; !day.isAfter(current.atEndOfMonth()); day = day.plusDays(1)) {
			DietPlan existing = days.get(day);
			boolean byAi = existing != null && existing.source.startsWith("Creado con IA");
			// Lo que mejoró la IA se respeta; los menús base se rehacen con los datos nuevos.
			if (existing == null || (dataChanged && !byAi && !day.isBefore(today))) {
				BodyProfile.Targets t = adjusted(body, day);
				days.put(day, DietRules.local(body, t, day.getDayOfMonth(), now));
			}
		}
		if (selected == null || !days.containsKey(selected))
			selected = today;
		plan = days.get(selected);
		adjustment = explain(body, selected);
		save();
	}

	/**
	 * Objetivo del día con compensación suave: si los últimos días anotados se pasó o se quedó corto, el día de hoy
	 * y el de mañana corrigen la mitad de la diferencia media, como mucho un 10 %. Los demás días, el objetivo base.
	 */
	BodyProfile.Targets adjusted(BodyProfile body, LocalDate day) {
		BodyProfile.Targets t = body.targets();
		LocalDate today = clock.get();
		if (day.isAfter(today.plusDays(1)))
			return t;
		double balance = balance(t.kcal, today);
		double correction = Math.max(-0.1 * t.kcal, Math.min(0.1 * t.kcal, -balance / 2));
		if (Math.abs(correction) < 50)
			return t;
		// La corrección va a hidratos y grasas; la proteína se mantiene.
		double carbs = Math.max(0, t.carbs + correction * 0.7 / 4), fat = Math.max(0, t.fat + correction * 0.3 / 9);
		return new BodyProfile.Targets(Math.round(t.kcal + correction), t.protein, Math.round(carbs), Math.round(fat));
	}

	/** Diferencia media (kcal) entre lo anotado y el objetivo en los 3 días anteriores con registros. */
	private double balance(double target, LocalDate today) {
		double total = 0;
		int count = 0;
		for (int i = 1; i <= 3; i++) {
			double kcal = logged.apply(today.minusDays(i));
			if (kcal > 0) {
				total += kcal - target;
				count++;
			}
		}
		return count == 0 ? 0 : total / count;
	}

	private String recentLogged(LocalDate today) {
		StringBuilder s = new StringBuilder();
		for (int i = 1; i <= 3; i++)
			s.append(Math.round(logged.apply(today.minusDays(i)))).append(',');
		return s.toString();
	}

	/** Presupuesto que toca al día elegido: lo que queda del mes entre los días que faltan. */
	public double dailyBudget(BodyProfile body, LocalDate day) {
		if (body.monthlyBudget <= 0)
			return 0;
		YearMonth m = YearMonth.from(day);
		double spent = 0;
		for (Map.Entry<LocalDate, DietPlan> e : days.entrySet())
			if (e.getKey().isBefore(day) && e.getValue().cost() > 0)
				spent += e.getValue().cost();
		// Los días anteriores sin menú en el plan cuentan con la parte media del presupuesto.
		int before = day.getDayOfMonth() - 1, planned = days.headMap(day).size();
		spent += Math.max(0, before - planned) * body.dailyBudget();
		int left = m.lengthOfMonth() - before;
		return Math.max(body.dailyBudget() * 0.5, (body.monthlyBudget - spent) / Math.max(1, left));
	}

	private String explain(BodyProfile body, LocalDate day) {
		if (day == null)
			return "";
		BodyProfile.Targets base = body.targets(), t = adjusted(body, day);
		List<String> parts = new ArrayList<>();
		long diff = Math.round(t.kcal - base.kcal);
		if (diff != 0)
			parts.add((diff < 0 ? "−" : "+") + Txt.t(diff < 0 ? R.string.dp_over : R.string.dp_under, Math.abs(diff)));
		if (body.monthlyBudget > 0)
			parts.add(Txt.t(R.string.dp_daily_budget, dailyBudget(body, day)));
		return String.join(" · ", parts);
	}

	// ---- Mejorar un día con la IA ----

	/** La IA rehace el menú del día elegido con el objetivo ajustado y el presupuesto que queda. */
	public void create(BodyProfile body) {
		if (owner == null || busy || body == null || body.targets() == null)
			return;
		sync(body);
		LocalDate day = selected == null ? clock.get() : selected;
		BodyProfile.Targets targets = adjusted(body, day);
		double budget = dailyBudget(body, day);
		busy = true;
		status = "";
		variant++;
		long token = generation;
		List<String> previous = new ArrayList<>();
		for (LocalDate d = day.minusDays(2); !d.isAfter(day); d = d.plusDays(1))
			if (days.containsKey(d))
				for (DietPlan.Meal m : days.get(d).meals)
					previous.add(m.dish);
		changed.run();
		if (model == null) {
			finish(token, day, null, body, targets, budget);
			return;
		}
		String prompt = DietRules.prompt(body, targets, training.get(), previous, budget);
		model.status(new OnDeviceModel.Result<Integer>() {
			public void success(Integer state) {
				if (state == null || state != OnDeviceModel.AVAILABLE) {
					finish(token, day, null, body, targets, budget);
					return;
				}
				model.generate(prompt, new OnDeviceModel.Result<String>() {
					public void success(String output) {
						finish(token, day, output, body, targets, budget);
					}

					public void failure(String code) {
						finish(token, day, null, body, targets, budget);
					}
				});
			}

			public void failure(String code) {
				finish(token, day, null, body, targets, budget);
			}
		});
	}

	/**
	 * Valida la respuesta de la IA (formato, alimentos prohibidos, calorías, presupuesto); si no sirve, menú base
	 * local. Se entrega en el hilo de la interfaz.
	 */
	private void finish(long token, LocalDate day, String output, BodyProfile body, BodyProfile.Targets targets,
		double budget) {
		long now = System.currentTimeMillis();
		DietPlan ai = parse(output, model == null ? "" : model.label(), now);
		String note = "";
		if (ai != null && ai.mentionsAny(DietRules.banned(body))) {
			ai = null;
			note = Txt.t(R.string.dp_banned_food);
		} else if (ai != null && (ai = ai.scaledTo(targets.kcal)) == null)
			note = Txt.t(R.string.dp_kcal_mismatch);
		else if (ai != null && budget > 0 && ai.cost() > budget * 1.25) {
			ai = null;
			note = Txt.t(R.string.dp_over_budget);
		} else if (ai == null && output != null)
			note = Txt.t(R.string.dp_invalid);
		else if (ai == null)
			note = Txt.t(R.string.dp_not_ready);
		DietPlan result = ai != null ? ai : DietRules.local(body, targets, variant + day.getDayOfMonth(), now);
		if (note.isEmpty() && budget > 0 && result.cost() > budget * 1.05)
			note = Txt.t(R.string.dp_budget_short);
		String message = note;
		delivery.execute(() -> {
			if (token != generation)
				return;
			busy = false;
			expanded = true;
			if (month == null)
				month = YearMonth.from(day);
			days.put(day, result);
			if (day.equals(selected) || selected == null) {
				selected = day;
				plan = result;
			}
			status = message;
			adjustment = explain(body, day);
			save();
			changed.run();
		});
	}

	static DietPlan parse(String output, String label, long now) {
		if (output == null)
			return null;
		Map<String, Object> json = TrainerPrompt.extractJson(output);
		return DietPlan.decode(json, "Creado con IA" + (label == null || label.isEmpty() ? "" : " (" + label + ")"), now);
	}

	// ---- Guardado: {"mes":"2026-10","dias":{"2026-10-16":{menú},…}}; un menú suelto antiguo pasa a hoy ----

	private void save() {
		if (owner == null)
			return;
		Map<String, Object> list = new LinkedHashMap<>();
		for (Map.Entry<LocalDate, DietPlan> e : days.entrySet())
			list.put(e.getKey().toString(), StrictJson.parse(e.getValue().encode()));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("mes", month().toString());
		root.put("dias", list);
		store.save(owner, StrictJson.write(root));
	}

	private void load(String json) {
		if (json == null || json.isEmpty())
			return;
		try {
			Object value = StrictJson.parse(json);
			if (!(value instanceof Map))
				return;
			Map<?, ?> root = (Map<?, ?>) value;
			if (root.get("dias") instanceof Map) {
				month = YearMonth.parse((String) root.get("mes"));
				for (Map.Entry<?, ?> e : ((Map<?, ?>) root.get("dias")).entrySet()) {
					DietPlan p = e.getValue() instanceof Map ? DietPlan.decode(cast(e.getValue()), "", 0) : null;
					if (p != null)
						days.put(LocalDate.parse((String) e.getKey()), p);
				}
			} else {
				DietPlan single = DietPlan.decode(json);
				if (single != null) {
					month = YearMonth.from(clock.get());
					days.put(clock.get(), single);
				}
			}
		} catch (RuntimeException ignored) {
			days.clear();
			month = null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object value) {
		return (Map<String, Object>) value;
	}

	/** Firma de los datos que cambian el plan (perfil, preferencias y peso). */
	private static final class NutritionRepositoryKey {
		static String of(BodyProfile b) {
			return b.sex + "/" + b.age + "/" + b.heightCm + "/" + b.weightKg + "/" + b.activity + "/" + b.goal + "/" +
				b.allergies + "/" + b.diet + "/" + b.cooking + "/" + b.mealsPerDay + "/" + b.dislikes + "/" +
				b.monthlyBudget;
		}
	}
}
