package com.energym.app.nutrition;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.util.Objects;

/**
 * MODEL: datos físicos declarados por la persona y objetivos diarios estimados.
 * Mifflin-St Jeor es una estimación poblacional; no sustituye a un profesional sanitario o dietista.
 */
public final class BodyProfile {
	public enum Sex {FEMALE, MALE}

	public enum Activity {
		SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), HIGH(1.725), VERY_HIGH(1.9);

		public final double factor;

		Activity(double factor) {
			this.factor = factor;
		}
	}

	public enum Goal {
		LOSE(-0.15, "Déficit calórico", R.string.body_goal_lose), MAINTAIN(0, "Mantenimiento", R.string.body_goal_maintain),
		GAIN(0.10, "Superávit calórico", R.string.body_goal_gain);

		public final double adjustment;
		/** En español para la IA; en pantalla, display(). */
		public final String label;
		private final int text;

		Goal(double adjustment, String label, int text) {
			this.adjustment = adjustment;
			this.label = label;
			this.text = text;
		}

		public String display() {
			return Txt.t(text);
		}
	}

	/** Estilo de alimentación para las dietas creadas con IA. */
	public enum Diet {
		OMNIVORE("Omnívora (de todo)", R.string.diet_omnivore), VEGETARIAN("Vegetariana", R.string.diet_vegetarian),
		VEGAN("Vegana", R.string.diet_vegan), PESCATARIAN("Pescetariana", R.string.diet_pescatarian),
		MEDITERRANEAN("Mediterránea", R.string.diet_mediterranean);

		public final String label;
		private final int text;

		Diet(String label, int text) {
			this.label = label;
			this.text = text;
		}

		public String display() {
			return Txt.t(text);
		}
	}

	/** Tiempo que la persona quiere dedicar a cocinar. */
	public enum Cooking {
		QUICK("Poco: platos de 15 min", R.string.cooking_quick), NORMAL("Normal: hasta 30 min", R.string.cooking_normal),
		ELABORATE("Me gusta cocinar", R.string.cooking_elaborate);

		public final String label;
		private final int text;

		Cooking(String label, int text) {
			this.label = label;
			this.text = text;
		}

		public String display() {
			return Txt.t(text);
		}
	}

	public final Sex sex;
	public final Activity activity;
	public final Goal goal;
	public final int age;
	public final double heightCm, weightKg;
	public final String allergies;
	public final Diet diet;
	public final Cooking cooking;
	public final int mealsPerDay;
	public final String dislikes;
	/** Euros al mes para la comida; 0 si no hay límite. */
	public final double monthlyBudget;
	/** Días medios de un mes, para repartir el presupuesto mensual por día. */
	public static final double DAYS_PER_MONTH = 30.4;

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies) {
		this(sex, age, heightCm, weightKg, activity, goal, allergies, Diet.OMNIVORE, Cooking.NORMAL, 4, "");
	}

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies, Diet diet, Cooking cooking, int mealsPerDay, String dislikes) {
		this(sex, age, heightCm, weightKg, activity, goal, allergies, diet, cooking, mealsPerDay, dislikes, 0);
	}

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies, Diet diet, Cooking cooking, int mealsPerDay, String dislikes, double monthlyBudget) {
		if (!(monthlyBudget >= 0 && monthlyBudget <= 5000))
			throw new IllegalArgumentException(Txt.t(R.string.body_err_budget));
		this.monthlyBudget = monthlyBudget;
		this.diet = diet == null ? Diet.OMNIVORE : diet;
		this.cooking = cooking == null ? Cooking.NORMAL : cooking;
		if (mealsPerDay < 2 || mealsPerDay > 6)
			throw new IllegalArgumentException(Txt.t(R.string.body_err_meals));
		this.mealsPerDay = mealsPerDay;
		this.dislikes = dislikes == null ? "" : dislikes.trim();
		if (this.dislikes.length() > 300)
			throw new IllegalArgumentException(Txt.t(R.string.body_err_dislikes));
		this.sex = Objects.requireNonNull(sex, "sex");
		this.activity = Objects.requireNonNull(activity, "activity");
		this.goal = Objects.requireNonNull(goal, "goal");
		if (age < 16 || age > 100)
			throw new IllegalArgumentException(Txt.t(R.string.body_err_age));
		if (!(heightCm >= 120 && heightCm <= 230))
			throw new IllegalArgumentException(Txt.t(R.string.body_err_height));
		if (!(weightKg >= 30 && weightKg <= 300))
			throw new IllegalArgumentException(Txt.t(R.string.body_err_weight));
		this.age = age;
		this.heightCm = heightCm;
		this.weightKg = weightKg;
		this.allergies = allergies == null ? "" : allergies.trim();
		if (this.allergies.length() > 300)
			throw new IllegalArgumentException(Txt.t(R.string.body_err_allergies));
	}

	/** Parte del presupuesto mensual que toca a cada día; 0 si no hay límite. */
	public double dailyBudget() {
		return monthlyBudget / DAYS_PER_MONTH;
	}

	public BodyProfile withWeight(double kg) {
		return new BodyProfile(sex, age, heightCm, kg, activity, goal, allergies, diet, cooking, mealsPerDay, dislikes, monthlyBudget);
	}

	/**
	 * Metabolismo basal estimado (kcal/día).
	 */
	public double basal() {
		return 10 * weightKg + 6.25 * heightCm - 5 * age + (sex == Sex.MALE ? 5 : -161);
	}

	/**
	 * Gasto diario estimado incluyendo actividad, sin ajuste de objetivo.
	 */
	public double maintenance() {
		return basal() * activity.factor;
	}

	public Targets targets() {
		return estimationIssue().isEmpty() ? calculateTargets() : null;
	}

	/**
	 * Conserva perfiles antiguos de 16–17 años para el diario, sin prescribirles objetivos automáticos.
	 * No ajusta silenciosamente resultados fuera del alcance de esta estimación para adultos.
	 */
	public String estimationIssue() {
		if (age < 18)
			return Txt.t(R.string.body_issue_age);
		Targets value = calculateTargets();
		if (!Double.isFinite(value.kcal) || value.kcal < 1000)
			return Txt.t(R.string.body_issue_low);
		double energy = value.protein * 4 + value.carbs * 4 + value.fat * 9;
		// El redondeo de hidratos puede desplazar hasta 2 kcal; una discrepancia mayor no es un plan válido.
		if (!Double.isFinite(energy) || Math.abs(energy - value.kcal) > 3)
			return Txt.t(R.string.body_issue_split);
		return "";
	}

	private Targets calculateTargets() {
		double kcal = Math.round(maintenance() * (1 + goal.adjustment));
		// Proteína 1,6–2,0 g/kg y grasa ≥ 0,8 g/kg; los hidratos completan las calorías restantes.
		double protein = Math.round(weightKg * (goal == Goal.MAINTAIN ? 1.6 : 2.0));
		double fat = Math.round(Math.max(weightKg * 0.8, kcal * 0.25 / 9));
		double carbs = Math.max(0, Math.round((kcal - protein * 4 - fat * 9) / 4));
		return new Targets(kcal, protein, carbs, fat);
	}

	public static final class Targets {
		public final double kcal, protein, carbs, fat;

		public Targets(double kcal, double protein, double carbs, double fat) {
			this.kcal = kcal;
			this.protein = protein;
			this.carbs = carbs;
			this.fat = fat;
		}
	}
}
