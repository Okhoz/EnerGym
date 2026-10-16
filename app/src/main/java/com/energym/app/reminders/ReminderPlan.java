package com.energym.app.reminders;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * MODELO de recordatorios locales: qué avisos están activos y cuándo toca el siguiente. Sin Android, para probarlo.
 * Los días de entreno se reparten por la semana según los días del plan (3 → lunes, miércoles y viernes).
 */
public final class ReminderPlan {
	/** Franja del día en la que se recuerda beber agua (de 9:00 a 21:00). */
	public static final int WATER_START_HOUR = 9, WATER_END_HOUR = 21;
	public static final int DEFAULT_TRAINING_MINUTE = 18 * 60, DEFAULT_WATER_HOURS = 2;
	private static final DayOfWeek[][] SPREAD = {
		{},
		{DayOfWeek.MONDAY},
		{DayOfWeek.MONDAY, DayOfWeek.THURSDAY},
		{DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY},
		{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY},
		{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY},
		{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
			DayOfWeek.SATURDAY},
		DayOfWeek.values()};
	private static final String[] SHORT = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

	public final boolean training, water;
	/** Minuto del día (0–1439) del aviso de entreno. */
	public final int trainingMinute;
	/** Días de entreno por semana del último plan visto (0 = aún sin plan). */
	public final int daysPerWeek;
	/** Horas entre avisos de agua (1–4). */
	public final int waterHours;

	public ReminderPlan(boolean training, int trainingMinute, int daysPerWeek, boolean water, int waterHours) {
		this.training = training;
		this.trainingMinute = Math.floorMod(trainingMinute, 24 * 60);
		this.daysPerWeek = Math.max(0, Math.min(7, daysPerWeek));
		this.water = water;
		this.waterHours = Math.max(1, Math.min(4, waterHours));
	}

	public static ReminderPlan defaults() {
		return new ReminderPlan(false, DEFAULT_TRAINING_MINUTE, 0, false, DEFAULT_WATER_HOURS);
	}

	public ReminderPlan withTraining(boolean on) {
		return new ReminderPlan(on, trainingMinute, daysPerWeek, water, waterHours);
	}

	public ReminderPlan withTrainingMinute(int minute) {
		return new ReminderPlan(training, minute, daysPerWeek, water, waterHours);
	}

	public ReminderPlan withDaysPerWeek(int days) {
		return new ReminderPlan(training, trainingMinute, days, water, waterHours);
	}

	public ReminderPlan withWater(boolean on) {
		return new ReminderPlan(training, trainingMinute, daysPerWeek, on, waterHours);
	}

	public ReminderPlan withWaterHours(int hours) {
		return new ReminderPlan(training, trainingMinute, daysPerWeek, water, hours);
	}

	public static Set<DayOfWeek> trainingDays(int daysPerWeek) {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (DayOfWeek d : SPREAD[Math.max(0, Math.min(7, daysPerWeek))])
			days.add(d);
		return days;
	}

	/** «lun, mié, vie» o vacío sin plan. */
	public String daysText() {
		StringBuilder out = new StringBuilder();
		for (DayOfWeek d : SPREAD[daysPerWeek])
			out.append(out.length() == 0 ? "" : ", ").append(SHORT[d.getValue() - 1]);
		return out.toString();
	}

	public String trainingTimeText() {
		return String.format(java.util.Locale.ROOT, "%d:%02d", trainingMinute / 60, trainingMinute % 60);
	}

	/** Próximo aviso de entreno estrictamente posterior a {@code now}; null si está apagado o no hay plan. */
	public LocalDateTime nextTraining(LocalDateTime now) {
		if (!training || daysPerWeek == 0)
			return null;
		Set<DayOfWeek> days = trainingDays(daysPerWeek);
		LocalTime time = LocalTime.of(trainingMinute / 60, trainingMinute % 60);
		for (int i = 0; i <= 7; i++) {
			LocalDateTime candidate = LocalDateTime.of(now.toLocalDate().plusDays(i), time);
			if (candidate.isAfter(now) && days.contains(candidate.getDayOfWeek()))
				return candidate;
		}
		return null;
	}

	/** Próximo aviso de agua (9:00, 9:00 + n, … hasta las 21:00) estrictamente posterior a {@code now}. */
	public LocalDateTime nextWater(LocalDateTime now) {
		if (!water)
			return null;
		for (int i = 0; i <= 1; i++) {
			LocalDate day = now.toLocalDate().plusDays(i);
			for (int hour = WATER_START_HOUR; hour <= WATER_END_HOUR; hour += waterHours) {
				LocalDateTime candidate = LocalDateTime.of(day, LocalTime.of(hour, 0));
				if (candidate.isAfter(now))
					return candidate;
			}
		}
		return null;
	}
}
