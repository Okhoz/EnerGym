package com.energym.app.nutrition;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Lectura única de la base local anterior (energym_nutrition.db, solo dispositivo). Sus datos se trasladan a
 * los registros sincronizables de la cuenta y se eliminan aquí; cuando queda vacía se borra el archivo.
 */
public final class LegacyNutritionDatabase {
	private static final String NAME = "energym_nutrition.db";

	private LegacyNutritionDatabase() {
	}

	public static final class Data {
		public final BodyProfile body;
		public final List<FoodEntry> food;
		public final SortedMap<LocalDate, Double> weights;

		Data(BodyProfile body, List<FoodEntry> food, SortedMap<LocalDate, Double> weights) {
			this.body = body;
			this.food = food;
			this.weights = weights;
		}

		public boolean empty() {
			return body == null && food.isEmpty() && weights.isEmpty();
		}
	}

	/**
	 * Lee los datos de la cuenta sin borrarlos; llamar a {@link #clear} después de copiarlos con éxito.
	 * Las filas que ya no superan la validación actual se descartan en lugar de bloquear la migración.
	 */
	public static Data read(Context context, String owner) {
		File file = context.getDatabasePath(NAME);
		if (!file.isFile())
			return new Data(null, Collections.emptyList(), new TreeMap<>());
		try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
			BodyProfile body = null;
			try (Cursor c = db.query("body", null, "owner_uid=?", new String[]{owner}, null, null, null)) {
				if (c.moveToFirst())
					try {
						body = new BodyProfile(BodyProfile.Sex.valueOf(text(c, "sex")),
							c.getInt(c.getColumnIndexOrThrow("age")), real(c, "height_cm"), real(c, "weight_kg"),
							BodyProfile.Activity.valueOf(text(c, "activity")), BodyProfile.Goal.valueOf(text(c, "goal")),
							text(c, "allergies"));
					} catch (RuntimeException ignored) {
					}
			}
			List<FoodEntry> food = new ArrayList<>();
			try (Cursor c = db.query("food", null, "owner_uid=?", new String[]{owner}, null, null, "day, created_at")) {
				while (c.moveToNext())
					try {
						food.add(new FoodEntry(text(c, "id"), LocalDate.parse(text(c, "day")),
							FoodEntry.Meal.valueOf(text(c, "meal")), text(c, "name"), text(c, "barcode"),
							FoodEntry.Unit.valueOf(text(c, "unit")), real(c, "amount"), real(c, "kcal100"),
							real(c, "protein100"), real(c, "carbs100"), real(c, "fat100"),
							c.getLong(c.getColumnIndexOrThrow("created_at"))));
					} catch (RuntimeException ignored) {
					}
			}
			SortedMap<LocalDate, Double> weights = new TreeMap<>();
			try (Cursor c = db.query("weight", null, "owner_uid=?", new String[]{owner}, null, null, "day")) {
				while (c.moveToNext()) {
					double kg = real(c, "kg");
					if (kg >= 30 && kg <= 300)
						weights.put(LocalDate.parse(text(c, "day")), kg);
				}
			}
			return new Data(body, food, weights);
		} catch (SQLiteException e) {
			return new Data(null, Collections.emptyList(), new TreeMap<>());
		}
	}

	/**
	 * Borra los datos de la cuenta y elimina el archivo si ya no contiene datos de ninguna cuenta.
	 */
	public static void clear(Context context, String owner) {
		File file = context.getDatabasePath(NAME);
		if (!file.isFile())
			return;
		boolean empty;
		try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
			db.beginTransaction();
			try {
				for (String table : new String[]{"body", "food", "weight"})
					db.delete(table, "owner_uid=?", new String[]{owner});
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
			empty = count(db, "body") + count(db, "food") + count(db, "weight") == 0;
		}
		if (empty)
			context.deleteDatabase(NAME);
	}

	private static long count(SQLiteDatabase db, String table) {
		return DatabaseUtils.queryNumEntries(db, table);
	}

	private static String text(Cursor c, String column) {
		return c.getString(c.getColumnIndexOrThrow(column));
	}

	private static double real(Cursor c, String column) {
		return c.getDouble(c.getColumnIndexOrThrow(column));
	}
}
