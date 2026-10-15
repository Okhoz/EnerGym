package com.energym.app.sync;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.controller.AppState;
import com.energym.app.controller.SyncController;
import com.energym.app.data.SyncRecord;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

/**
 * Probe temporal opt-in: nunca obtiene credenciales, siembra fixtures, importa datos ni borra la app.
 * inventory no abre Activity; snapshot usa la identidad y el controlador existentes.
 * La salida contiene exclusivamente metadatos y huellas, nunca payloads, UID, tokens o correos.
 */
@RunWith(AndroidJUnit4.class)
public final class FirebaseLiveRestoreProbeTest {
	private static final long TIMEOUT_MS = 45_000;
	private static final int PAGE_SIZE = 100, MAX_RECORDS = 10_000;
	private String stage = "arguments";

	@Test
	public void captureEvidence() {
		Bundle arguments = InstrumentationRegistry.getArguments();
		Assume.assumeTrue("Probe remoto opt-in: runLiveRestore=true",
			"true".equals(arguments.getString("runLiveRestore")));
		JSONObject evidence = new JSONObject();
		String mode = arguments.getString("mode", "inventory");
		String label = arguments.getString("evidenceLabel", mode);
		try {
			require(label.matches("[A-Za-z0-9_-]{1,48}"), "INVALID_LABEL");
			require("inventory".equals(mode) || "freshInstall".equals(mode) || "snapshot".equals(mode), "INVALID_MODE");
			require(!arguments.containsKey("refresh") || "true".equals(arguments.getString("refresh")) ||
				"false".equals(arguments.getString("refresh")), "INVALID_REFRESH");
			evidence.put("schemaVersion", 1).put("mode", mode).put("label", label);
			evidence.put("validated", false).put("restorationProven", false);
			Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
			if ("inventory".equals(mode) || "freshInstall".equals(mode)) {
				require(!"true".equals(arguments.getString("refresh")), "INVENTORY_REFRESH_FORBIDDEN");
				stage = "inventory";
				inventory(context, evidence);
				if ("freshInstall".equals(mode)) {
					stage = "fresh-install";
					boolean fresh = evidence.getBoolean("emptyLocalInventory") && !evidence.getBoolean("authenticated");
					evidence.put("fresh", fresh).put("validated", fresh);
					require(fresh, "INSTALLATION_NOT_EMPTY_OR_AUTHENTICATED");
				}
			} else {
				stage = "snapshot";
				snapshot(evidence, "true".equals(arguments.getString("refresh")));
			}
			stage = "save";
			publish(context, label, evidence);
		} catch (Exception | AssertionError error) {
			try {
				evidence.put("validated", false).put("restorationProven", false);
				evidence.put("failedStage", stage).put("failureType", safeFailure(error));
				publish(InstrumentationRegistry.getInstrumentation().getTargetContext(),
					label.matches("[A-Za-z0-9_-]{1,48}") ? label : "invalid-arguments", evidence);
			} catch (Exception ignored) {
				// No imprimir causas: el SDK puede incluir rutas o identificadores de cuenta.
			}
			fail("LIVE_RESTORE_PROBE_FAILED stage=" + stage + " type=" + safeFailure(error));
		}
	}

	private void inventory(Context context, JSONObject evidence) throws Exception {
		File file = context.getDatabasePath("energym.db");
		evidence.put("databasePresent", file.isFile());
		Map<String, JSONObject> owners = new TreeMap<>();
		long total = 0, pending = 0;
		if (file.isFile()) {
			try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
				SQLiteDatabase.OPEN_READONLY);
				Cursor rows = db.rawQuery("SELECT owner_uid,state,kind,COUNT(*) FROM records " +
					"GROUP BY owner_uid,state,kind ORDER BY owner_uid,state,kind", null)) {
				while (rows.moveToNext()) {
					String ownerHash = hash(rows.getString(0));
					String state = rows.getString(1);
					String kind = rows.getString(2);
					require("CLEAN".equals(state) || "PENDING".equals(state), "INVALID_LOCAL_STATE");
					SyncRecord.Kind.valueOf(kind);
					long count = rows.getLong(3);
					JSONObject owner = owners.get(ownerHash);
					if (owner == null) {
						owner = new JSONObject().put("uidHash", ownerHash).put("countsByKind", new JSONObject())
							.put("countsByState", new JSONObject());
						owners.put(ownerHash, owner);
					}
					increment(owner.getJSONObject("countsByKind"), kind, count);
					increment(owner.getJSONObject("countsByState"), state, count);
					total += count;
					if ("PENDING".equals(state))
						pending += count;
				}
			}
		}
		JSONArray ownerCounts = new JSONArray();
		for (JSONObject owner : owners.values())
			ownerCounts.put(owner);
		File legacy = context.getDatabasePath("energym_nutrition.db");
		File conversations = new File(context.getNoBackupFilesDir(), "conversations");
		long[] conversationFiles = fileCounts(conversations);
		require(FirebaseApp.initializeApp(context) != null, "FIREBASE_CONFIGURATION_REQUIRED");
		FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
		evidence.put("owners", ownerCounts).put("totalRecords", total).put("pendingRecords", pending);
		evidence.put("legacyDatabasePresent", legacy.isFile()).put("legacyDatabaseBytes", legacy.length());
		evidence.put("conversationFileCount", conversationFiles[0]).put("conversationBytes", conversationFiles[1]);
		evidence.put("authenticated", user != null).put("syncStatus", "NOT_OBSERVED");
		if (user != null)
			evidence.put("uidHash", hash(user.getUid()));
		evidence.put("emptyLocalInventory", total == 0 && !legacy.isFile() && conversationFiles[0] == 0);
		evidence.put("unbackedLocalDataMayExist", pending > 0 || legacy.isFile() || conversationFiles[0] > 0);
		evidence.put("eligibleRestoreBaseline", false);
		evidence.put("validationScope", "INVENTORY_READ_ONLY");
		evidence.put("validated", true);
		// validated valida el inventario, no autoriza desinstalar ni acredita restauración.
	}

	private void snapshot(JSONObject evidence, boolean refresh) throws Exception {
		long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			stage = "wait-app-state";
			SessionView session = waitReady(scenario, deadline);
			evidence.put("uidHash", hash(session.uid)).put("ownerHash", hash(session.owner));
			evidence.put("authenticated", true).put("refreshRequested", refresh);
			evidence.put("syncStatus", session.status).put("pendingRecords", -1);
			evidence.put("validationScope", "LOCAL_SERVER_PARITY");
			if (refresh)
				scenario.onActivity(a -> a.state().sync.requestSync());
			stage = "wait-sync";
			waitForSync(scenario, session.uid, deadline);
			FirebaseFirestore firestore = FirebaseFirestore.getInstance();
			require("firestore.googleapis.com".equals(firestore.getFirestoreSettings().getHost()) &&
				firestore.getFirestoreSettings().isSslEnabled(), "REAL_SERVER_REQUIRED");
			evidence.put("projectHash", hash(FirebaseApp.getInstance().getOptions().getProjectId()));
			stage = "local-before";
			Summary localBefore = local(scenario, session.uid, deadline);
			stage = "server-before";
			Summary serverBefore = server(firestore, session.uid, deadline);
			stage = "local-after";
			Summary localAfter = local(scenario, session.uid, deadline);
			stage = "server-after";
			Summary serverAfter = server(firestore, session.uid, deadline);
			stage = "identity-after";
			SessionView end = session(scenario);
			requireIdentity(end, session.uid);
			evidence.put("syncStatus", end.status).put("pendingRecords", localAfter.pending);
			evidence.put("localBefore", localBefore.json()).put("localAfter", localAfter.json());
			evidence.put("serverBefore", serverBefore.json()).put("serverAfter", serverAfter.json());
			boolean localStable = localBefore.digest().equals(localAfter.digest());
			boolean serverStable = serverBefore.digest().equals(serverAfter.digest()) &&
				serverBefore.versionDigest().equals(serverAfter.versionDigest());
			boolean sameData = localAfter.digest().equals(serverAfter.digest());
			boolean valid = localStable && serverStable && sameData && localBefore.pending == 0 &&
				localAfter.pending == 0 && "SYNCED".equals(end.status);
			evidence.put("localStable", localStable).put("serverStable", serverStable);
			evidence.put("matchesServer", sameData).put("sourceServerVerified", true);
			evidence.put("validated", valid).put("eligibleRestoreBaseline", valid && localAfter.rows.size() > 0);
			evidence.put("coverage", localAfter.rows.isEmpty() ? "EMPTY_NO_RESTORE_PROOF" : "SYNC_RECORDS_ONLY");
			evidence.put("conversationCloudBackupClaimed", false);
			require(valid, "SNAPSHOT_NOT_STABLE_OR_NOT_BACKED_UP");
		}
	}

	private SessionView waitReady(ActivityScenario<MainActivity> scenario, long deadline) throws Exception {
		while (remaining(deadline) > 0) {
			SessionView view = session(scenario);
			if (view.ready && view.uid != null && view.owner != null) {
				requireIdentity(view, view.uid);
				return view;
			}
			SystemClock.sleep(Math.min(100, remaining(deadline)));
		}
		throw new TimeoutException();
	}

	private void waitForSync(ActivityScenario<MainActivity> scenario, String uid, long deadline) throws Exception {
		while (remaining(deadline) > 0) {
			SessionView view = session(scenario);
			requireIdentity(view, uid);
			if ("SYNCED".equals(view.status))
				return;
			require(!"ERROR".equals(view.status) && !"CONFLICT".equals(view.status), "SYNC_NOT_CONFIRMED");
			SystemClock.sleep(Math.min(100, remaining(deadline)));
		}
		throw new TimeoutException();
	}

	private SessionView session(ActivityScenario<MainActivity> scenario) {
		AtomicReference<SessionView> result = new AtomicReference<>();
		scenario.onActivity(a -> {
			AppState state = a.state();
			FirebaseUser firebase = FirebaseAuth.getInstance().getCurrentUser();
			boolean google = false;
			if (firebase != null && !firebase.isAnonymous())
				for (UserInfo provider : firebase.getProviderData())
					google |= "google.com".equals(provider.getProviderId());
			result.set(new SessionView(state.app != null && state.sync != null,
				firebase == null ? null : firebase.getUid(), state.user == null ? null : state.user.getUid(),
				state.app == null ? null : state.app.owner(), state.syncStatus.name(), google));
		});
		return result.get();
	}

	private static void requireIdentity(SessionView session, String uid) {
		require(session.ready && session.google && uid.equals(session.uid) && uid.equals(session.presentedUid) &&
			uid.equals(session.owner), "AUTHENTICATED_OWNER_MISMATCH");
	}

	private Summary local(ActivityScenario<MainActivity> scenario, String uid, long deadline) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Summary> result = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		scenario.onActivity(a -> a.state().sync.withDatabase(db -> {
			require(uid.equals(db.getOwnerUid()), "LOCAL_OWNER_CHANGED");
			JSONObject snapshot = new JSONObject(db.exportSnapshot());
			require(uid.equals(snapshot.getString("ownerUid")), "LOCAL_SNAPSHOT_OWNER_MISMATCH");
			Summary summary = new Summary();
			JSONArray records = snapshot.getJSONArray("records");
			for (int i = 0; i < records.length(); i++) {
				JSONObject row = records.getJSONObject(i);
				require(uid.equals(row.getString("ownerUid")), "LOCAL_RECORD_OWNER_MISMATCH");
				SyncRecord record = new SyncRecord(SyncRecord.Kind.valueOf(row.getString("kind")),
					row.getString("syncId"), uid, row.getJSONObject("payload").toString(), 1, 1, SyncRecord.State.CLEAN);
				summary.add(record, null);
			}
			for (SyncRecord record : db.pendingSyncRecords()) {
				summary.pending++;
				increment(summary.pendingByKind, record.getKind().name(), 1);
			}
			return summary;
		}, new SyncController.DataCallback<Summary>() {
			public void onSuccess(Summary value) {
				result.set(value);
				done.countDown();
			}

			public void onFailure(Exception error) {
				failure.set(error);
				done.countDown();
			}
		}));
		require(done.await(remaining(deadline), TimeUnit.MILLISECONDS), "LOCAL_READ_TIMEOUT");
		if (failure.get() != null)
			throw failure.get();
		require(result.get() != null, "LOCAL_RESULT_MISSING");
		return result.get();
	}

	private Summary server(FirebaseFirestore firestore, String uid, long deadline) throws Exception {
		Summary summary = new Summary();
		for (SyncRecord.Kind kind : SyncRecord.Kind.values()) {
			CollectionReference collection = firestore.collection("users").document(uid)
				.collection(CloudDocumentCodec.collection(kind));
			if (CloudDocumentCodec.singleton(kind)) {
				DocumentSnapshot document = Tasks.await(collection.document("current").get(Source.SERVER),
					remaining(deadline), TimeUnit.MILLISECONDS);
				verifyServerMetadata(document.getMetadata(), uid);
				if (document.exists())
					addRemote(summary, uid, kind, document);
			} else {
				DocumentSnapshot after = null;
				while (true) {
					Query query = collection.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
					if (after != null)
						query = query.startAfter(after);
					QuerySnapshot page = Tasks.await(query.get(Source.SERVER), remaining(deadline), TimeUnit.MILLISECONDS);
					verifyServerMetadata(page.getMetadata(), uid);
					List<DocumentSnapshot> documents = page.getDocuments();
					for (DocumentSnapshot document : documents) {
						verifyServerMetadata(document.getMetadata(), uid);
						addRemote(summary, uid, kind, document);
					}
					if (documents.size() < PAGE_SIZE)
						break;
					after = documents.get(documents.size() - 1);
				}
			}
		}
		return summary;
	}

	private static void addRemote(Summary summary, String uid, SyncRecord.Kind kind, DocumentSnapshot document)
		throws Exception {
		CloudSyncGateway.RemoteRecord remote = CloudDocumentCodec.decode(uid, kind, document.getId(), document.getData());
		summary.add(remote.record, remote.cloudVersion);
		require(summary.rows.size() <= MAX_RECORDS, "REMOTE_RECORD_LIMIT");
	}

	private static void verifyServerMetadata(SnapshotMetadata metadata, String uid) {
		require(!metadata.isFromCache() && !metadata.hasPendingWrites(), "SERVER_METADATA_REQUIRED");
		FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
		require(current != null && uid.equals(current.getUid()), "SERVER_OWNER_CHANGED");
	}

	private static final class SessionView {
		final boolean ready, google;
		final String uid, presentedUid, owner, status;

		SessionView(boolean ready, String uid, String presentedUid, String owner, String status, boolean google) {
			this.ready = ready;
			this.uid = uid;
			this.presentedUid = presentedUid;
			this.owner = owner;
			this.status = status;
			this.google = google;
		}
	}

	private static final class Summary {
		final SortedMap<String, String> rows = new TreeMap<>(), versions = new TreeMap<>();
		final JSONObject countsByKind = new JSONObject(), pendingByKind = new JSONObject();
		final SortedMap<String, SortedMap<String, String>> kinds = new TreeMap<>();
		int pending;

		void add(SyncRecord record, Long cloudVersion) throws Exception {
			String key = record.getKind().name() + ":" + record.getSyncId();
			String payload = (String) CloudDocumentCodec.encode(record, 1).get("payload");
			String rowDigest = hash(record.getKind().name() + "\n" + record.getSyncId() + "\n" +
				canonical(new JSONObject(payload)));
			require(rows.put(key, rowDigest) == null, "DUPLICATE_RECORD");
			kinds.computeIfAbsent(record.getKind().name(), k -> new TreeMap<>()).put(key, rowDigest);
			increment(countsByKind, record.getKind().name(), 1);
			if (cloudVersion != null)
				versions.put(key, hash(key + "\n" + cloudVersion));
		}

		String digest() throws Exception {
			return hash(String.join("\n", rows.values()));
		}

		String versionDigest() throws Exception {
			return hash(String.join("\n", versions.values()));
		}

		JSONObject json() throws Exception {
			JSONObject digests = new JSONObject();
			for (Map.Entry<String, SortedMap<String, String>> kind : kinds.entrySet())
				digests.put(kind.getKey(), hash(String.join("\n", kind.getValue().values())));
			JSONObject result = new JSONObject().put("totalRecords", rows.size()).put("countsByKind", countsByKind)
				.put("digestAll", digest()).put("digestsByKind", digests).put("pendingRecords", pending)
				.put("pendingByKind", pendingByKind);
			if (!versions.isEmpty())
				result.put("cloudVersionDigest", versionDigest());
			return result;
		}
	}

	private static String canonical(Object value) throws Exception {
		if (value == null || value == JSONObject.NULL)
			return "null";
		if (value instanceof JSONObject) {
			JSONObject object = (JSONObject) value;
			List<String> keys = new ArrayList<>();
			Iterator<String> iterator = object.keys();
			while (iterator.hasNext())
				keys.add(iterator.next());
			Collections.sort(keys);
			List<String> fields = new ArrayList<>();
			for (String key : keys)
				fields.add(JSONObject.quote(key) + ":" + canonical(object.get(key)));
			return "{" + String.join(",", fields) + "}";
		}
		if (value instanceof JSONArray) {
			JSONArray array = (JSONArray) value;
			List<String> items = new ArrayList<>();
			for (int i = 0; i < array.length(); i++)
				items.add(canonical(array.get(i)));
			return "[" + String.join(",", items) + "]";
		}
		if (value instanceof Number)
			return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
		if (value instanceof Boolean)
			return value.toString();
		require(value instanceof String, "INVALID_JSON_VALUE");
		return JSONObject.quote((String) value);
	}

	private static long[] fileCounts(File file) {
		if (!file.exists())
			return new long[]{0, 0};
		if (file.isFile())
			return new long[]{1, file.length()};
		File[] children = file.listFiles();
		require(children != null, "LOCAL_FILES_UNREADABLE");
		long count = 0, bytes = 0;
		for (File child : children) {
			long[] part = fileCounts(child);
			count += part[0];
			bytes += part[1];
		}
		return new long[]{count, bytes};
	}

	private static void increment(JSONObject counts, String key, long value) throws Exception {
		counts.put(key, counts.optLong(key, 0) + value);
	}

	private static String hash(String text) throws Exception {
		byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder();
		for (byte value : bytes)
			hex.append(String.format(Locale.ROOT, "%02x", value & 255));
		return hex.toString();
	}

	private static long remaining(long deadline) throws TimeoutException {
		long value = deadline - SystemClock.elapsedRealtime();
		if (value <= 0)
			throw new TimeoutException();
		return value;
	}

	private static void require(boolean condition, String code) {
		if (!condition)
			throw new ProbeFailure(code);
	}

	private static final class ProbeFailure extends IllegalStateException {
		final String code;

		ProbeFailure(String code) {
			super(code);
			this.code = code;
		}
	}

	private static String safeFailure(Throwable error) {
		if (error instanceof ProbeFailure)
			return ((ProbeFailure) error).code;
		if (error instanceof FirebaseFirestoreException)
			return "FIRESTORE_" + ((FirebaseFirestoreException) error).getCode().name();
		if (error instanceof TimeoutException)
			return "TIMEOUT";
		return error.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "");
	}

	private static void publish(Context context, String label, JSONObject evidence) throws Exception {
		File base = context.getExternalFilesDir(null);
		require(base != null, "EVIDENCE_DIRECTORY_UNAVAILABLE");
		File folder = new File(base, "restore-evidence");
		require(folder.isDirectory() || folder.mkdirs(), "EVIDENCE_DIRECTORY_UNAVAILABLE");
		String name = label + "-" + System.currentTimeMillis() + ".json";
		byte[] bytes = evidence.toString(2).getBytes(StandardCharsets.UTF_8);
		try (FileOutputStream output = new FileOutputStream(new File(folder, name))) {
			output.write(bytes);
		}
		Bundle report = new Bundle();
		report.putString("stream", "ENERGYM_RESTORE_EVIDENCE " + evidence.toString() + "\n");
		report.putString("evidenceFile", "restore-evidence/" + name);
		InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
	}
}
