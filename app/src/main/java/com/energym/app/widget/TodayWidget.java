package com.energym.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import com.energym.app.MainActivity;
import com.energym.app.R;

/**
 * Widget «Tu día» para la pantalla de inicio: el entreno que toca, las calorías y el agua de hoy. La app deja el
 * resumen en este móvil cada vez que cambia algo; el widget solo lo pinta (sin red ni cálculos).
 */
public final class TodayWidget extends AppWidgetProvider {
	private static final String PREFS = "today_widget";

	/** Guarda el resumen y repinta los widgets solo si ha cambiado. */
	public static void publish(Context c, String training, String food, String water) {
		SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String day = java.time.LocalDate.now().toString();
		if (training.equals(p.getString("training", null)) && food.equals(p.getString("food", null)) &&
			water.equals(p.getString("water", null)) && day.equals(p.getString("day", null)))
			return;
		p.edit().putString("training", training).putString("food", food).putString("water", water)
			.putString("day", day).apply();
		AppWidgetManager manager = AppWidgetManager.getInstance(c);
		int[] ids = manager.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
		if (ids.length > 0)
			draw(c, manager, ids);
	}

	@Override
	public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
		draw(c, manager, ids);
	}

	private static void draw(Context c, AppWidgetManager manager, int[] ids) {
		SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		// Un resumen de otro día ya no vale: hasta abrir la app se muestra sin cifras.
		boolean today = java.time.LocalDate.now().toString().equals(p.getString("day", ""));
		RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_today);
		views.setTextViewText(R.id.widget_training, p.getString("training", "Abre EnerGym para ver tu entreno"));
		views.setTextViewText(R.id.widget_food, today ? p.getString("food", "") : "🍽 Sin comidas anotadas hoy");
		views.setTextViewText(R.id.widget_water, today ? p.getString("water", "") : "💧 0 ml de agua");
		views.setOnClickPendingIntent(R.id.widget_root, open(c, "TRAINING", 0));
		views.setOnClickPendingIntent(R.id.widget_food, open(c, "NUTRITION", 1));
		views.setOnClickPendingIntent(R.id.widget_water, open(c, "NUTRITION", 2));
		manager.updateAppWidget(ids, views);
	}

	private static PendingIntent open(Context c, String screen, int code) {
		Intent launch = new Intent(c, MainActivity.class).putExtra(com.energym.app.reminders.Reminders.EXTRA_OPEN, screen)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return PendingIntent.getActivity(c, 2000 + code, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}
}
