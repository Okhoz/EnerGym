package com.energym.app.health;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.energym.app.domain.Session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;

/**
 * Envía a Health Connect (Android 14 o superior, integrado en el sistema) los entrenos terminados y el peso, para
 * verlos en Samsung Health, Google Fit, etc. Cada registro lleva un id propio, así que reenviarlo no lo duplica.
 */
public final class HealthSync {
	public static final String[] PERMISSIONS = {"android.permission.health.WRITE_EXERCISE",
		"android.permission.health.WRITE_WEIGHT"};

	private HealthSync() {
	}

	/** Health Connect forma parte del sistema desde Android 14. */
	public static boolean available() {
		return Build.VERSION.SDK_INT >= 34;
	}

	private static SharedPreferences prefs(Context c) {
		return c.getSharedPreferences("health_connect", Context.MODE_PRIVATE);
	}

	public static boolean enabled(Context c) {
		return available() && prefs(c).getBoolean("enabled", false) && granted(c);
	}

	public static void setEnabled(Context c, boolean on) {
		prefs(c).edit().putBoolean("enabled", on).apply();
	}

	public static boolean granted(Context c) {
		for (String p : PERMISSIONS)
			if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
				return false;
		return true;
	}

	/** Envía todo (idempotente); {@code done} recibe un texto con el resultado. */
	public static void sync(Context c, List<Session> history, SortedMap<LocalDate, Double> weights, Consumer<String> done) {
		if (!available() || !granted(c)) {
			done.accept(Txt.t(R.string.hs_no_permission));
			return;
		}
		Api34.sync(c.getApplicationContext(), history, weights, done);
	}

	@RequiresApi(34)
	private static final class Api34 {
		static void sync(Context c, List<Session> history, SortedMap<LocalDate, Double> weights, Consumer<String> done) {
			android.health.connect.HealthConnectManager manager =
				c.getSystemService(android.health.connect.HealthConnectManager.class);
			if (manager == null) {
				done.accept(Txt.t(R.string.hs_unavailable));
				return;
			}
			List<android.health.connect.datatypes.Record> records = new ArrayList<>();
			int sessions = 0;
			for (Session s : history) {
				if (s.getStatus() != Session.Status.FINISHED || s.getCompletedAt() <= 0)
					continue;
				long start = s.getStartedAt() > 0 && s.getStartedAt() < s.getCompletedAt() ? s.getStartedAt() :
					s.getCompletedAt() - 60 * 60_000L;
				records.add(new android.health.connect.datatypes.ExerciseSessionRecord.Builder(
					metadata("energym-session-" + s.getSyncId()), Instant.ofEpochMilli(start),
					Instant.ofEpochMilli(s.getCompletedAt()),
					android.health.connect.datatypes.ExerciseSessionType.EXERCISE_SESSION_TYPE_STRENGTH_TRAINING)
					.setTitle(s.getRoutine().getTitle()).build());
				sessions++;
			}
			for (Map.Entry<LocalDate, Double> w : weights.entrySet())
				records.add(new android.health.connect.datatypes.WeightRecord.Builder(
					metadata("energym-weight-" + w.getKey()),
					w.getKey().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant(),
					android.health.connect.datatypes.units.Mass.fromGrams(w.getValue() * 1000)).build());
			if (records.isEmpty()) {
				done.accept(Txt.t(R.string.hs_nothing));
				return;
			}
			int sent = sessions;
			int kilos = weights.size();
			android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
			manager.insertRecords(records, Runnable::run, new android.os.OutcomeReceiver<
				android.health.connect.InsertRecordsResponse, android.health.connect.HealthConnectException>() {
				public void onResult(android.health.connect.InsertRecordsResponse r) {
					main.post(() -> done.accept(Txt.t(R.string.hs_sent, Txt.n(R.plurals.hs_workouts, sent),
						Txt.n(R.plurals.nc_weights, kilos))));
				}

				public void onError(android.health.connect.HealthConnectException e) {
					main.post(() -> done.accept(Txt.t(R.string.hs_rejected, e.getMessage())));
				}
			});
		}

		private static android.health.connect.datatypes.Metadata metadata(String id) {
			return new android.health.connect.datatypes.Metadata.Builder().setClientRecordId(id).setClientRecordVersion(1)
				.build();
		}
	}
}
