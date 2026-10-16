package com.energym.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Recibe las alarmas de recordatorio (muestra el aviso y programa el siguiente) y, tras reiniciar el móvil,
 * actualizar la app o cambiar la hora, vuelve a programarlas.
 */
public final class ReminderReceiver extends BroadcastReceiver {
	/** Un aviso que Android entrega con mucho retraso (móvil en reposo profundo) ya no tiene sentido. */
	private static final long LATE_TRAINING_MS = 3 * 3600_000L, LATE_WATER_MS = 3600_000L;

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (RestTimer.ACTION_DONE.equals(action)) {
			RestTimer.finished(context);
			return;
		}
		ReminderPlan plan = Reminders.load(context);
		long late = System.currentTimeMillis() - intent.getLongExtra(Reminders.EXTRA_AT, System.currentTimeMillis());
		java.time.LocalDate today = java.time.LocalDate.now();
		// Ya entrenado o con el agua del día cumplida, no se insiste.
		if (Reminders.ACTION_TRAINING.equals(action) && plan.training && late < LATE_TRAINING_MS &&
			!Reminders.dayMarked(context, "trainedDay", today))
			Reminders.showTraining(context);
		else if (Reminders.ACTION_WATER.equals(action) && plan.water && late < LATE_WATER_MS &&
			!Reminders.dayMarked(context, "waterDoneDay", today))
			Reminders.showWater(context, java.time.LocalTime.now().getHour());
		Reminders.schedule(context);
	}
}
