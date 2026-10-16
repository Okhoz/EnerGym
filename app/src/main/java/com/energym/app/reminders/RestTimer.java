package com.energym.app.reminders;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.energym.app.MainActivity;
import com.energym.app.R;

/**
 * Descanso entre series fuera de la app: una notificación con la cuenta atrás (visible con la pantalla bloqueada)
 * y una alarma que vibra y avisa al terminar, aunque el móvil esté apagado o en otra app. Todo local.
 */
public final class RestTimer {
	static final String ACTION_DONE = "com.energym.app.reminders.REST_DONE";
	private static final String CHANNEL_COUNTDOWN = "rest_countdown", CHANNEL_DONE = "rest_done";
	private static final int ID = 1003;
	/** Con la pantalla de entreno a la vista ya avisa ella; la notificación sobraría. */
	private static volatile boolean inFront;

	private RestTimer() {
	}

	public static void inFront(boolean visible) {
		inFront = visible;
	}

	/** Programa el aviso para cuando acabe el descanso (reloj desde el arranque, como el temporizador en pantalla). */
	public static void start(Context c, long endsAtElapsed, String exercise) {
		cancel(c);
		long left = endsAtElapsed - SystemClock.elapsedRealtime();
		if (left <= 0 || !Reminders.canNotify(c))
			return;
		ensureChannels(c);
		NotificationCompat.Builder n = base(c, CHANNEL_COUNTDOWN).setContentTitle("⏱ Descanso")
			.setContentText(exercise == null || exercise.isEmpty() ? "Siguiente serie al acabar" : "Siguiente: " + exercise)
			.setWhen(System.currentTimeMillis() + left).setShowWhen(true).setUsesChronometer(true)
			.setChronometerCountDown(true).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
			.setTimeoutAfter(left + 1000).setCategory(NotificationCompat.CATEGORY_STOPWATCH);
		notify(c, n);
		AlarmManager alarms = c.getSystemService(AlarmManager.class);
		if (alarms == null)
			return;
		PendingIntent done = pending(c, PendingIntent.FLAG_UPDATE_CURRENT);
		// Exacta si Android lo permite; si no, en una ventana corta (el aviso puede llegar unos segundos tarde).
		if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
			alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsed, done);
		else
			alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsed, 10_000, done);
	}

	public static void cancel(Context c) {
		AlarmManager alarms = c.getSystemService(AlarmManager.class);
		PendingIntent old = pending(c, PendingIntent.FLAG_NO_CREATE);
		if (alarms != null && old != null) {
			alarms.cancel(old);
			old.cancel();
		}
		NotificationManagerCompat.from(c).cancel(ID);
	}

	/** Llamado por la alarma: cambia la cuenta atrás por un aviso que vibra. */
	static void finished(Context c) {
		NotificationManagerCompat.from(c).cancel(ID);
		if (inFront || !Reminders.canNotify(c))
			return;
		ensureChannels(c);
		notify(c, base(c, CHANNEL_DONE).setContentTitle("💪 ¡Descanso terminado!")
			.setContentText("A por la siguiente serie.").setAutoCancel(true).setTimeoutAfter(5 * 60_000L)
			.setPriority(NotificationCompat.PRIORITY_HIGH).setVibrate(new long[]{0, 300, 150, 300})
			.setCategory(NotificationCompat.CATEGORY_ALARM));
	}

	private static NotificationCompat.Builder base(Context c, String channel) {
		Intent launch = new Intent(c, MainActivity.class).putExtra(Reminders.EXTRA_OPEN, "SESSION")
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent tap = PendingIntent.getActivity(c, ID, launch,
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		return new NotificationCompat.Builder(c, channel).setSmallIcon(R.drawable.ic_training)
			.setColor(ContextCompat.getColor(c, R.color.gold)).setContentIntent(tap)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
	}

	private static PendingIntent pending(Context c, int flag) {
		Intent intent = new Intent(c, ReminderReceiver.class).setAction(ACTION_DONE);
		return PendingIntent.getBroadcast(c, ID, intent, flag | PendingIntent.FLAG_IMMUTABLE);
	}

	private static void notify(Context c, NotificationCompat.Builder n) {
		try {
			NotificationManagerCompat.from(c).notify(ID, n.build());
		} catch (SecurityException ignored) {
			// El permiso se retiró entre la comprobación y el aviso.
		}
	}

	private static void ensureChannels(Context c) {
		NotificationManager manager = c.getSystemService(NotificationManager.class);
		if (manager == null)
			return;
		NotificationChannel countdown = new NotificationChannel(CHANNEL_COUNTDOWN, "Descanso en curso",
			NotificationManager.IMPORTANCE_LOW);
		countdown.setDescription("Cuenta atrás del descanso entre series.");
		NotificationChannel done = new NotificationChannel(CHANNEL_DONE, "Fin del descanso",
			NotificationManager.IMPORTANCE_HIGH);
		done.setDescription("Vibra y avisa cuando toca la siguiente serie.");
		done.enableVibration(true);
		done.setVibrationPattern(new long[]{0, 300, 150, 300});
		manager.createNotificationChannel(countdown);
		manager.createNotificationChannel(done);
	}
}
