package com.energym.app.data;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.sync.CloudDocumentCodec;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;

import static org.junit.Assert.*;

/** Extras (medidas, comidas guardadas, ejercicios propios y superseries) como registros sincronizables. */
@RunWith(AndroidJUnit4.class)
public class ExtrasSyncTest {
	private String uid;
	private EnerGymDatabase db;

	@Before
	public void open() {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		uid = "extras-" + UUID.randomUUID();
		db = new EnerGymDatabase(context, uid);
	}

	@After
	public void close() {
		db.clearLocalData();
		db.close();
	}

	@Test
	public void extrasRoundTripThroughTheCloudCodecAndLastWriteWins() {
		ExtrasRepository repo = new ExtrasRepository(db);
		repo.put("measures", "all", "2026-10-16;waist=82.0\n");
		repo.put("measures", "all", "2026-10-16;waist=81.5\n");
		repo.put("supersets", "a1b2-c3", "0,2");
		List<ExtrasRepository.Extra> all = repo.all();
		assertEquals(2, all.size());
		SyncRecord record = null;
		for (SyncRecord r : db.pendingSyncRecords())
			if (r.getKind() == SyncRecord.Kind.EXTRA && r.getSyncId().equals("measures_all"))
				record = r;
		assertNotNull(record);
		assertEquals("extras", CloudDocumentCodec.collection(SyncRecord.Kind.EXTRA));
		Map<String, Object> doc = CloudDocumentCodec.encode(record, 1);
		assertEquals("", doc.get("identity"));
		assertEquals(false, doc.get("terminal"));
		SyncRecord back = CloudDocumentCodec.decode(uid, SyncRecord.Kind.EXTRA, "measures_all", doc).record;
		assertTrue(back.getPayload().contains("81.5"));
	}

	@Test
	public void unknownTypesAndMismatchedIdsAreRejected() {
		ExtrasRepository repo = new ExtrasRepository(db);
		try {
			repo.put("photos", "all", "x");
			fail("Las fotos no se suben");
		} catch (IllegalArgumentException expected) {
		}
		try {
			ExtrasRepository.canonical("meals_all", "{\"type\":\"measures\",\"key\":\"all\",\"data\":\"\"}");
			fail("El id debe corresponder a su tipo");
		} catch (IllegalArgumentException expected) {
		}
		try {
			ExtrasRepository.canonical("meals_all", "{\"type\":\"meals\",\"key\":\"all\",\"data\":\"\",\"extra\":1}");
			fail("Sin campos de más");
		} catch (IllegalArgumentException expected) {
		}
	}
}
