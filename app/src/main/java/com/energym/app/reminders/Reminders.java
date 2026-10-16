package com.energym.app.reminders;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.energym.app.MainActivity;
import com.energym.app.R;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Programa y muestra los recordatorios en el propio móvil (AlarmManager + canales de notificación):
 * sin servidor ni coste. Cada alarma programa la siguiente al sonar; el arranque del móvil las restaura.
 */
public final class Reminders {
	static final String ACTION_TRAINING = "com.energym.app.reminders.TRAINING";
	static final String ACTION_WATER = "com.energym.app.reminders.WATER";
	static final String EXTRA_AT = "at";
	/** Pantalla que abre la notificación en {@link MainActivity}: «TRAINING» o «NUTRITION». */
	public static final String EXTRA_OPEN = "com.energym.app.OPEN";
	private static final String CHANNEL_TRAINING = "training_reminders", CHANNEL_WATER = "water_reminders";
	private static final int ID_TRAINING = 1001, ID_WATER = 1002;
	/** Ventana de entrega: Android puede retrasar el aviso hasta 10 min para ahorrar batería. */
	private static final long WINDOW_MS = 10 * 60_000L;
	private static final String[] WATER_TEXTS = {
		"Un vaso ahora y vas sumando hacia tu objetivo de hoy.",
		"Hidratarte ayuda a rendir y a recuperar. Toca para apuntar un vaso.",
		"¿Has bebido agua en las últimas horas? Apúntalo en Nutrición."};

	private Reminders() {
	}

	private static SharedPreferences prefs(Context c) {
		return c.getSharedPreferences("reminders", Context.MODE_PRIVATE);
	}

	public static ReminderPlan load(Context c) {
		SharedPreferences p = prefs(c);
		ReminderPlan d = ReminderPlan.defaults();
		return new ReminderPlan(p.getBoolean("training", d.training), p.getInt("trainingMinute", d.trainingMinute),
			p.getInt("daysPerWeek", d.daysPerWeek), p.getBoolean("water", d.water), p.getInt("waterHours", d.waterHours));
	}

	static void save(Context c, ReminderPlan plan) {
		prefs(c).edit().putBoolean("training", plan.training).putInt("trainingMinute", plan.trainingMinute)
			.putInt("daysPerWeek", plan.daysPerWeek).putBoolean("water", plan.water).putInt("waterHours", plan.waterHours)
			.apply();
	}

	/** Marca o desmarca el día (aaaa-mm-dd) en que ya se cumplió algo («waterDoneDay», «trainedDay»). */
	static void markDay(Context c, String key, java.time.LocalDate day, boolean done) {
		SharedPreferences p = prefs(c);
		String value = day.toString();
		if (done && !value.equals(p.getString(key, "")))
			p.edit().putString(key, value).apply();
		else if (!done && value.equals(p.getString(key, "")))
			p.edit().remove(key).apply();
	}

	static boolean dayMarked(Context c, String key, java.time.LocalDate day) {
		return day.toString().equals(prefs(c).getString(key, ""));
	}

	/** Sin permiso (Android 13+) o con las notificaciones de la app apagadas, los avisos no se verían. */
	public static boolean canNotify(Context c) {
		if (Build.VERSION.SDK_INT >= 33 &&
			ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
			return false;
		return NotificationManagerCompat.from(c).areNotificationsEnabled();
	}

	/** Deja programado el siguiente aviso de cada tipo según el plan guardado (o los cancela). */
	public static void schedule(Context c) {
		ReminderPlan plan = load(c);
		LocalDateTime now = LocalDateTime.now();
		set(c, ACTION_TRAINING, plan.nextTraining(now));
		set(c, ACTION_WATER, plan.nextWater(now));
	}

	private static void set(Context c, String action, LocalDateTime at) {
		AlarmManager alarms = c.getSystemService(AlarmManager.class);
		if (alarms == null)
			return;
		Intent intent = new Intent(c, ReminderReceiver.class).setAction(action);
		if (at == null) {
			PendingIntent old = PendingIntent.getBroadcast(c, requestCode(action), intent,
				PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
			if (old != null) {
				alarms.cancel(old);
				old.cancel();
			}
			return;
		}
		long millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		intent.putExtra(EXTRA_AT, millis);
		PendingIntent pending = PendingIntent.getBroadcast(c, requestCode(action), intent,
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		// Alarma inexacta: no necesita el permiso de alarmas exactas y respeta la batería.
		alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MS, pending);
	}

	private static int requestCode(String action) {
		return ACTION_TRAINING.equals(action) ? ID_TRAINING : ID_WATER;
	}

	static void ensureChannels(Context c) {
		NotificationManager manager = c.getSystemService(NotificationManager.class);
		if (manager == null)
			return;
		NotificationChannel training = new NotificationChannel(CHANNEL_TRAINING, "Recordatorios de entreno",
			NotificationManager.IMPORTANCE_DEFAULT);
		training.setDescription("Aviso los días que toca entrenar, a la hora que elijas.");
		NotificationChannel water = new NotificationChannel(CHANNEL_WATER, "Recordatorios de agua",
			NotificationManager.IMPORTANCE_DEFAULT);
		water.setDescription("Avisos para beber agua cada pocas horas durante el día.");
		manager.createNotificationChannel(training);
		manager.createNotificationChannel(water);
	}

	static void showTraining(Context c) {
		show(c, CHANNEL_TRAINING, ID_TRAINING, R.drawable.ic_training, "Hoy toca entrenar",
			"Tu sesión te espera. Toca para abrir Entreno.", "TRAINING");
	}

	static void showWater(Context c, int hour) {
		show(c, CHANNEL_WATER, ID_WATER, R.drawable.ic_nutrition, "Hora de beber agua",
			WATER_TEXTS[Math.floorMod(hour, WATER_TEXTS.length)], "NUTRITION");
	}

	private static void show(Context c, String channel, int id, int icon, String title, String text, String open) {
		if (!canNotify(c))
			return;
		ensureChannels(c);
		Intent launch = new Intent(c, MainActivity.class).putExtra(EXTRA_OPEN, open)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent tap = PendingIntent.getActivity(c, id, launch,
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		NotificationCompat.Builder n = new NotificationCompat.Builder(c, channel).setSmallIcon(icon)
			.setContentTitle(title).setContentText(text)
			.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
			.setColor(ContextCompat.getColor(c, R.color.gold)).setContentIntent(tap).setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_REMINDER);
		try {
			NotificationManagerCompat.from(c).notify(id, n.build());
		} catch (SecurityException ignored) {
			// El permiso se retiró entre la comprobación y el aviso.
		}
	}
}
