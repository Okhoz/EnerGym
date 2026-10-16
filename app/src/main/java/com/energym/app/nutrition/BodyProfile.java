package com.energym.app.nutrition;

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
		LOSE(-0.15, "Déficit calórico"), MAINTAIN(0, "Mantenimiento"), GAIN(0.10, "Superávit calórico");

		public final double adjustment;
		public final String label;

		Goal(double adjustment, String label) {
			this.adjustment = adjustment;
			this.label = label;
		}
	}

	/** Estilo de alimentación para las dietas creadas con IA. */
	public enum Diet {
		OMNIVORE("Omnívora (de todo)"), VEGETARIAN("Vegetariana"), VEGAN("Vegana"), PESCATARIAN("Pescetariana"),
		MEDITERRANEAN("Mediterránea");

		public final String label;

		Diet(String label) {
			this.label = label;
		}
	}

	/** Tiempo que la persona quiere dedicar a cocinar. */
	public enum Cooking {
		QUICK("Poco: platos de 15 min"), NORMAL("Normal: hasta 30 min"), ELABORATE("Me gusta cocinar");

		public final String label;

		Cooking(String label) {
			this.label = label;
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
	/** Euros al día para la comida; 0 si no hay límite. */
	public final double budget;

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies) {
		this(sex, age, heightCm, weightKg, activity, goal, allergies, Diet.OMNIVORE, Cooking.NORMAL, 4, "");
	}

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies, Diet diet, Cooking cooking, int mealsPerDay, String dislikes) {
		this(sex, age, heightCm, weightKg, activity, goal, allergies, diet, cooking, mealsPerDay, dislikes, 0);
	}

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies, Diet diet, Cooking cooking, int mealsPerDay, String dislikes, double budget) {
		if (!(budget >= 0 && budget <= 200))
			throw new IllegalArgumentException("Presupuesto entre 0 y 200 € al día.");
		this.budget = budget;
		this.diet = diet == null ? Diet.OMNIVORE : diet;
		this.cooking = cooking == null ? Cooking.NORMAL : cooking;
		if (mealsPerDay < 2 || mealsPerDay > 6)
			throw new IllegalArgumentException("Comidas al día entre 2 y 6.");
		this.mealsPerDay = mealsPerDay;
		this.dislikes = dislikes == null ? "" : dislikes.trim();
		if (this.dislikes.length() > 300)
			throw new IllegalArgumentException("Alimentos que no tomas: máximo 300 caracteres.");
		this.sex = Objects.requireNonNull(sex, "sex");
		this.activity = Objects.requireNonNull(activity, "activity");
		this.goal = Objects.requireNonNull(goal, "goal");
		if (age < 16 || age > 100)
			throw new IllegalArgumentException("Edad entre 16 y 100 años.");
		if (!(heightCm >= 120 && heightCm <= 230))
			throw new IllegalArgumentException("Altura entre 120 y 230 cm.");
		if (!(weightKg >= 30 && weightKg <= 300))
			throw new IllegalArgumentException("Peso entre 30 y 300 kg.");
		this.age = age;
		this.heightCm = heightCm;
		this.weightKg = weightKg;
		this.allergies = allergies == null ? "" : allergies.trim();
		if (this.allergies.length() > 300)
			throw new IllegalArgumentException("Alergias: máximo 300 caracteres.");
	}

	public BodyProfile withWeight(double kg) {
		return new BodyProfile(sex, age, heightCm, kg, activity, goal, allergies, diet, cooking, mealsPerDay, dislikes, budget);
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
			return "El cálculo automático de objetivos está disponible a partir de los 18 años.";
		Targets value = calculateTargets();
		if (!Double.isFinite(value.kcal) || value.kcal < 1000)
			return "El cálculo resulta demasiado bajo. Revisa tus datos con un profesional antes de fijar un objetivo.";
		double energy = value.protein * 4 + value.carbs * 4 + value.fat * 9;
		// El redondeo de hidratos puede desplazar hasta 2 kcal; una discrepancia mayor no es un plan válido.
		if (!Double.isFinite(energy) || Math.abs(energy - value.kcal) > 3)
			return "No se puede calcular un reparto coherente con estos datos. Revisa tu perfil con un profesional.";
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
