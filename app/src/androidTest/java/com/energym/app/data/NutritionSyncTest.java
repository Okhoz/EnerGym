package com.energym.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.nutrition.*;
import com.energym.app.sync.CloudDocumentCodec;
import com.energym.app.sync.SqliteSyncStore;

import org.junit.*;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NutritionSyncTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);
	private Context context;
	private String uid;
	private EnerGymDatabase db;
	private NutritionRepository repo;

	@Before
	public void open() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		uid = "nutrition-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, uid);
		repo = new NutritionRepository(db);
	}

	@After
	public void close() {
		db.clearLocalData();
		db.close();
	}

	private static FoodEntry rice() {
		return FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "Arroz", "8410000000000", FoodEntry.Unit.G, 150, 360, 7, 79, 1);
	}

	private SyncRecord pending(SyncRecord.Kind kind, String id) {
		for (SyncRecord r : db.pendingSyncRecords())
			if (r.getKind() == kind && r.getSyncId().equals(id))
				return r;
		return null;
	}

	private SyncRecord roundtrip(SyncRecord r, long version) {
		return CloudDocumentCodec.decode(uid, r.getKind(), CloudDocumentCodec.documentId(r.getKind(), r.getSyncId()),
			CloudDocumentCodec.encode(r, version)).record;
	}

	@Test
	public void bodyFoodAndWeightBecomePendingRecordsAndRoundtripThroughCloudCodec() {
		BodyProfile body = new BodyProfile(BodyProfile.Sex.MALE, 30, 178, 78, BodyProfile.Activity.MODERATE,
			BodyProfile.Goal.MAINTAIN, "lactosa");
		repo.saveBody(body);
		FoodEntry rice = rice();
		repo.add(rice);
		repo.weight(DAY, 78.4);
		assertEquals(3, db.pendingSyncRecords().size());
		assertEquals("lactosa", repo.body().allergies);
		assertEquals(540, repo.food(DAY).get(0).kcal(), 0.001);
		assertEquals(78.4, repo.weights().get(DAY), 0);
		assertEquals("current", CloudDocumentCodec.documentId(SyncRecord.Kind.BODY, NutritionRepository.BODY_ID));
		assertEquals("weights", CloudDocumentCodec.collection(SyncRecord.Kind.WEIGHT));
		Map<String, Object> food = CloudDocumentCodec.encode(pending(SyncRecord.Kind.FOOD, rice.id), 1);
		assertEquals(false, food.get("terminal"));
		assertTrue(((String) food.get("identity")).matches("[a-f0-9]{64}"));
		for (SyncRecord r : db.pendingSyncRecords())
			assertEquals(r.getPayload(), roundtrip(r, 1).getPayload());
	}

	@Test
	public void removalWritesTerminalTombstoneWithStableIdentityThatImportCannotResurrect() {
		FoodEntry rice = rice();
		repo.add(rice);
		SyncRecord active = pending(SyncRecord.Kind.FOOD, rice.id);
		Map<String, Object> before = CloudDocumentCodec.encode(active, 1);
		repo.remove(rice.id);
		assertTrue(repo.food(DAY).isEmpty());
		SyncRecord tomb = pending(SyncRecord.Kind.FOOD, rice.id);
		Map<String, Object> after = CloudDocumentCodec.encode(tomb, 2);
		assertEquals(before.get("identity"), after.get("identity"));
		assertEquals(true, after.get("terminal"));
		db.acknowledge(tomb.getKind(), tomb.getSyncId(), tomb.getRevision());
		// Una copia remota anterior y activa, incluso más reciente, no resucita el alimento.
		SyncRecord stale = new SyncRecord(SyncRecord.Kind.FOOD, rice.id, uid, active.getPayload(), active.getRevision(),
			tomb.getUpdatedAt() + 1000, SyncRecord.State.CLEAN);
		new SqliteSyncStore(db).importRecords(Collections.singletonList(stale));
		assertTrue(repo.food(DAY).isEmpty());
		repo.remove(rice.id);
		assertTrue(db.pendingSyncRecords().isEmpty());
	}

	@Test
	public void restoreImportsRemoteNutritionAndRejectsMismatchedIds() {
		FoodEntry rice = rice();
		long now = System.currentTimeMillis();
		List<SyncRecord> remote = Arrays.asList(
			new SyncRecord(SyncRecord.Kind.FOOD, rice.id, uid, NutritionRepository.encodeFood(rice), 1, now,
				SyncRecord.State.CLEAN),
			new SyncRecord(SyncRecord.Kind.WEIGHT, DAY.toString(), uid, NutritionRepository.encodeWeight(DAY, 77),
				2, now, SyncRecord.State.CLEAN));
		new SqliteSyncStore(db).importRecords(remote);
		assertEquals(1, repo.food(DAY).size());
		assertEquals(77, repo.weights().get(DAY), 0);
		assertTrue(db.pendingSyncRecords().isEmpty());
		SyncRecord wrongDay = new SyncRecord(SyncRecord.Kind.WEIGHT, "2026-10-16", uid,
			NutritionRepository.encodeWeight(DAY, 77), 3, now, SyncRecord.State.CLEAN);
		assertThrows(IllegalArgumentException.class,
			() -> new SqliteSyncStore(db).importRecords(Collections.singletonList(wrongDay)));
		assertThrows(IllegalArgumentException.class, () -> NutritionRepository.encodeWeight(DAY, 10));
	}

	@Test
	public void legacyLocalDiaryIsCopiedOnceAndRemoved() {
		SQLiteDatabase legacy = context.openOrCreateDatabase("energym_nutrition.db", Context.MODE_PRIVATE, null);
		try {
			legacy.execSQL("CREATE TABLE IF NOT EXISTS body (owner_uid TEXT PRIMARY KEY, sex TEXT, age INTEGER, " +
				"height_cm REAL, weight_kg REAL, activity TEXT, goal TEXT, allergies TEXT)");
			legacy.execSQL("CREATE TABLE IF NOT EXISTS food (id TEXT PRIMARY KEY, owner_uid TEXT, day TEXT, meal TEXT, " +
				"name TEXT, barcode TEXT, unit TEXT, amount REAL, kcal100 REAL, protein100 REAL, carbs100 REAL, " +
				"fat100 REAL, created_at INTEGER)");
			legacy.execSQL("CREATE TABLE IF NOT EXISTS weight (owner_uid TEXT, day TEXT, kg REAL, PRIMARY KEY(owner_uid, day))");
			ContentValues food = new ContentValues();
			food.put("id", UUID.randomUUID().toString());
			food.put("owner_uid", uid);
			food.put("day", DAY.toString());
			food.put("meal", "LUNCH");
			food.put("name", "Lentejas");
			food.put("barcode", "");
			food.put("unit", "G");
			food.put("amount", 200);
			food.put("kcal100", 116);
			food.put("protein100", 9);
			food.put("carbs100", 20);
			food.put("fat100", 0.4);
			food.put("created_at", System.currentTimeMillis());
			legacy.insertOrThrow("food", null, food);
			ContentValues weight = new ContentValues();
			weight.put("owner_uid", uid);
			weight.put("day", DAY.toString());
			weight.put("kg", 80.0);
			legacy.insertOrThrow("weight", null, weight);
		} finally {
			legacy.close();
		}
		LegacyNutritionDatabase.Data data = LegacyNutritionDatabase.read(context, uid);
		assertEquals(1, data.food.size());
		for (FoodEntry e : data.food)
			repo.add(e);
		for (Map.Entry<LocalDate, Double> w : data.weights.entrySet())
			repo.weight(w.getKey(), w.getValue());
		LegacyNutritionDatabase.clear(context, uid);
		assertTrue(LegacyNutritionDatabase.read(context, uid).empty());
		assertEquals("Lentejas", repo.food(DAY).get(0).name);
		assertEquals(80, repo.weights().get(DAY), 0);
		assertEquals(2, db.pendingSyncRecords().size());
	}
}
