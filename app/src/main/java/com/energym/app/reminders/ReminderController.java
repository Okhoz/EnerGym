package com.energym.app.reminders;

import android.content.Context;

/**
 * CONTROLADOR de los ajustes de recordatorios: guarda cada cambio, reprograma las alarmas y avisa a la vista.
 * El permiso de notificaciones lo pide la vista justo antes de activar un aviso.
 */
public final class ReminderController {
	private final Context context;
	private final Runnable changed;
	private ReminderPlan plan;

	public ReminderController(Context context, Runnable changed) {
		this.context = context.getApplicationContext();
		this.changed = changed;
		plan = Reminders.load(this.context);
		Reminders.schedule(this.context);
	}

	public ReminderPlan plan() {
		return plan;
	}

	public boolean canNotify() {
		return Reminders.canNotify(context);
	}

	public void setTraining(boolean on) {
		update(plan.withTraining(on));
	}

	public void setTrainingMinute(int minute) {
		update(plan.withTrainingMinute(minute));
	}

	public void setWater(boolean on) {
		update(plan.withWater(on));
	}

	public void setWaterHours(int hours) {
		update(plan.withWaterHours(hours));
	}

	/** Los días de entreno siguen al plan activo; sin cambios no se reprograma nada. */
	public void syncDays(int daysPerWeek) {
		if (daysPerWeek == plan.daysPerWeek)
			return;
		plan = plan.withDaysPerWeek(daysPerWeek);
		Reminders.save(context, plan);
		Reminders.schedule(context);
	}

	/**
	 * Lo ya hecho hoy calla sus avisos hasta mañana: el de agua al cumplir el objetivo y el de entreno al
	 * finalizar una sesión.
	 */
	public void today(java.time.LocalDate day, int waterMl, int waterGoal, boolean trained) {
		Reminders.markDay(context, "waterDoneDay", day, waterGoal > 0 && waterMl >= waterGoal);
		Reminders.markDay(context, "trainedDay", day, trained);
	}

	private void update(ReminderPlan next) {
		plan = next;
		Reminders.save(context, plan);
		if (plan.training || plan.water)
			Reminders.ensureChannels(context);
		Reminders.schedule(context);
		changed.run();
	}
}
