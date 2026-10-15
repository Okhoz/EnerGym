package com.energym.app.nutrition;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * MODEL Android: diario de nutrición y peso por cuenta, en su propia base local. Todas las consultas
 * filtran por owner_uid; el borrado local de la cuenta también elimina estos datos.
 */
public final class SqliteNutritionStore extends SQLiteOpenHelper implements NutritionStore {
	private final Executor worker, main;

	public SqliteNutritionStore(Context context, Executor worker, Executor main) {
		super(context.getApplicationContext(), "energym_nutrition.db", null, 1);
		this.worker = worker;
		this.main = main;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE body (owner_uid TEXT PRIMARY KEY, sex TEXT NOT NULL, age INTEGER NOT NULL, " +
			"height_cm REAL NOT NULL, weight_kg REAL NOT NULL, activity TEXT NOT NULL, goal TEXT NOT NULL, " +
			"allergies TEXT NOT NULL)");
		db.execSQL("CREATE TABLE food (id TEXT PRIMARY KEY, owner_uid TEXT NOT NULL, day TEXT NOT NULL, " +
			"meal TEXT NOT NULL, name TEXT NOT NULL, barcode TEXT NOT NULL, unit TEXT NOT NULL, amount REAL NOT NULL, " +
			"kcal100 REAL NOT NULL, protein100 REAL NOT NULL, carbs100 REAL NOT NULL, fat100 REAL NOT NULL, " +
			"created_at INTEGER NOT NULL)");
		db.execSQL("CREATE INDEX food_owner_day ON food(owner_uid, day)");
		db.execSQL("CREATE TABLE weight (owner_uid TEXT NOT NULL, day TEXT NOT NULL, kg REAL NOT NULL, " +
			"PRIMARY KEY(owner_uid, day))");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		throw new IllegalStateException("Migración de nutrición no implementada: " + oldVersion + " → " + newVersion);
	}

	private interface Work<T> {
		T run(SQLiteDatabase db);
	}

	private <T> void run(Work<T> work, Callback<T> callback) {
		worker.execute(() -> {
			try {
				T value = work.run(getWritableDatabase());
				main.execute(() -> callback.success(value));
			} catch (RuntimeException e) {
				main.execute(() -> callback.failure(e));
			}
		});
	}

	public void load(String owner, LocalDate from, Callback<Snapshot> callback) {
		run(db -> {
			BodyProfile body = null;
			try (Cursor c = db.query("body", null, "owner_uid=?", new String[]{owner}, null, null, null)) {
				if (c.moveToFirst())
					body = new BodyProfile(BodyProfile.Sex.valueOf(text(c, "sex")), c.getInt(c.getColumnIndexOrThrow("age")),
						real(c, "height_cm"), real(c, "weight_kg"), BodyProfile.Activity.valueOf(text(c, "activity")),
						BodyProfile.Goal.valueOf(text(c, "goal")), text(c, "allergies"));
			}
			List<FoodEntry> entries = new ArrayList<>();
			try (Cursor c = db.query("food", null, "owner_uid=? AND day>=?", new String[]{owner, from.toString()},
				null, null, "day, created_at")) {
				while (c.moveToNext())
					entries.add(new FoodEntry(text(c, "id"), LocalDate.parse(text(c, "day")),
						FoodEntry.Meal.valueOf(text(c, "meal")), text(c, "name"), text(c, "barcode"),
						FoodEntry.Unit.valueOf(text(c, "unit")), real(c, "amount"), real(c, "kcal100"),
						real(c, "protein100"), real(c, "carbs100"), real(c, "fat100"),
						c.getLong(c.getColumnIndexOrThrow("created_at"))));
			}
			SortedMap<LocalDate, Double> weights = new TreeMap<>();
			try (Cursor c = db.query("weight", null, "owner_uid=?", new String[]{owner}, null, null, "day")) {
				while (c.moveToNext())
					weights.put(LocalDate.parse(text(c, "day")), real(c, "kg"));
			}
			return new Snapshot(body, entries, weights);
		}, callback);
	}

	public void saveBody(String owner, BodyProfile b, Callback<Void> callback) {
		run(db -> {
			ContentValues v = new ContentValues();
			v.put("owner_uid", owner);
			v.put("sex", b.sex.name());
			v.put("age", b.age);
			v.put("height_cm", b.heightCm);
			v.put("weight_kg", b.weightKg);
			v.put("activity", b.activity.name());
			v.put("goal", b.goal.name());
			v.put("allergies", b.allergies);
			db.insertWithOnConflict("body", null, v, SQLiteDatabase.CONFLICT_REPLACE);
			return null;
		}, callback);
	}

	public void add(String owner, FoodEntry e, Callback<Void> callback) {
		run(db -> {
			ContentValues v = new ContentValues();
			v.put("id", e.id);
			v.put("owner_uid", owner);
			v.put("day", e.day.toString());
			v.put("meal", e.meal.name());
			v.put("name", e.name);
			v.put("barcode", e.barcode);
			v.put("unit", e.unit.name());
			v.put("amount", e.amount);
			v.put("kcal100", e.kcal100);
			v.put("protein100", e.protein100);
			v.put("carbs100", e.carbs100);
			v.put("fat100", e.fat100);
			v.put("created_at", e.createdAt);
			db.insertOrThrow("food", null, v);
			return null;
		}, callback);
	}

	public void remove(String owner, String id, Callback<Void> callback) {
		run(db -> {
			db.delete("food", "owner_uid=? AND id=?", new String[]{owner, id});
			return null;
		}, callback);
	}

	public void weight(String owner, LocalDate day, double kg, Callback<Void> callback) {
		run(db -> {
			ContentValues v = new ContentValues();
			v.put("owner_uid", owner);
			v.put("day", day.toString());
			v.put("kg", kg);
			db.insertWithOnConflict("weight", null, v, SQLiteDatabase.CONFLICT_REPLACE);
			return null;
		}, callback);
	}

	/**
	 * Borrado local de una cuenta: mismo alcance que el borrado de entrenamientos.
	 */
	public void clear(String owner, Callback<Void> callback) {
		run(db -> {
			db.beginTransaction();
			try {
				for (String table : new String[]{"body", "food", "weight"})
					db.delete(table, "owner_uid=?", new String[]{owner});
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
			return null;
		}, callback);
	}

	private static String text(Cursor c, String column) {
		return c.getString(c.getColumnIndexOrThrow(column));
	}

	private static double real(Cursor c, String column) {
		return c.getDouble(c.getColumnIndexOrThrow(column));
	}
}
