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
		LOSE(-0.15), MAINTAIN(0), GAIN(0.10);

		public final double adjustment;

		Goal(double adjustment) {
			this.adjustment = adjustment;
		}
	}

	public final Sex sex;
	public final Activity activity;
	public final Goal goal;
	public final int age;
	public final double heightCm, weightKg;
	public final String allergies;

	public BodyProfile(Sex sex, int age, double heightCm, double weightKg, Activity activity, Goal goal,
		String allergies) {
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
		return new BodyProfile(sex, age, heightCm, kg, activity, goal, allergies);
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
