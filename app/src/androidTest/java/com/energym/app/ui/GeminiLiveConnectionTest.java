package com.energym.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.assistant.ConversationStore;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.RoutineRepository;
import com.energym.app.data.SnapshotCodec;
import com.energym.app.domain.Routine;
import com.energym.app.domain.TrainingProfile;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

/**
 * Smoke de IA real: omitido salvo autorización explícita y servidor emparejable de desarrollo.
 */
@RunWith(AndroidJUnit4.class)
public class GeminiLiveConnectionTest {
	@Test
	public void remoteProposalRequiresAcceptanceAndPersistsInDemoSqlite() throws Exception {
		Bundle args = InstrumentationRegistry.getArguments();
		String url = args.getString("serverUrl");
		String code = args.getString("pairingCode");
		Assume.assumeTrue("Smoke Gemini real opt-in",
			"true".equals(args.getString("runLiveGemini")) && url != null && !url.trim().isEmpty() && code != null &&
				!code.trim().isEmpty());
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		// Solo fixture demo sintético; no se lee ni modifica ninguna cuenta Google.
		new AssistantFlowTest().clearDemo();
		TrainingProfile baseline =
			new TrainingProfile.Builder().durationMinutes(30).experience("").space("").preferences("").limitations("")
				.historyConsent(false).build();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.saveProfile(baseline);
		}
		String before;
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			before = db.exportSnapshot();
		}
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			try {
				MainFlowTest.await();
				MainFlowTest.click(scenario, "Cuenta");
				boolean[] enter = {false};
				scenario.onActivity(
					a -> enter[0] = MainFlowTest.text(a.getWindow().getDecorView(), "Entrar en demo local") != null);
				if (enter[0])
					MainFlowTest.click(scenario, "Entrar en demo local");
				waitIdle(scenario);
				scenario.onActivity(a -> {
					assertEquals(EnerGymDatabase.DEMO_OWNER_UID, a.state().app.owner());
					assertEquals(baseline, a.state().app.profile);
					((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
						R.id.navigation)).setSelectedItemId(R.id.tab_assistant);
				});
				MainFlowTest.await();
				MainFlowTest.click(scenario, "Cuenta");
				MainFlowTest.click(scenario, "Conectar servidor de IA");
				MainFlowTest.edit(scenario, "assistant_endpoint", url);
				MainFlowTest.edit(scenario, "assistant_pairing", code);
				MainFlowTest.click(scenario, "Conectar");
				waitIdle(scenario);
				scenario.onActivity(a -> {
					assertTrue("Emparejamiento no completado; revisar estado seguro del servidor",
						a.state().assistant.connected());
					assertEquals("", a.state().assistant.pairingCode);
					assertNull(a.state().assistant.preview);
				});
				scenario.onActivity(a -> ((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(R.id.navigation)).setSelectedItemId(R.id.tab_assistant));
				MainFlowTest.await();
				// Una sola pulsación externa; sin guía local ni respuestas prefabricadas.
				MainFlowTest.edit(scenario, "assistant_message",
					"Prepara una rutina de ejemplo genérica: 3 días, 20 min, en casa, sin material, fuerza, principiante. Sin datos personales ni historial.");
				MainFlowTest.click(scenario, "Enviar mensaje");
				waitIdle(scenario);
				Routine[] preview = {null};
				boolean[] remote = {false};
				String[] safeStatus = {"sin estado"};
				scenario.onActivity(a -> {
					for (ConversationStore.Message message : a.state().assistant.messages)
						if ("assistant".equals(message.role) && "remote".equals(message.source))
							remote[0] = true;
					safeStatus[0] = safeStatus(a.state().assistant.status);
				});
				if (!remote[0] && "true".equals(args.getString("captureLiveGemini"))) {
					try {
						capture(context, "gemini-live-failure.png");
					} catch (Exception | AssertionError unavailable) {
						// Una evidencia opcional nunca oculta el estado seguro del fallo principal.
						android.util.Log.i("GeminiLiveSmoke", "Captura diagnóstica no disponible");
					}
				}
				assertTrue("No llegó respuesta remota validada; estado: " + safeStatus[0], remote[0]);
				scenario.onActivity(a -> {
					preview[0] = a.state().assistant.preview;
					assertNotNull("La respuesta remota no produjo una propuesta válida", preview[0]);
					assertTrue(preview[0].isValidFor(preview[0].getProfile()));
					assertEquals(0, preview[0].getVersionId());
					assertEquals(3, preview[0].getProfile().getDaysPerWeek());
					assertEquals(20, preview[0].getProfile().getDurationMinutes());
					assertFalse(preview[0].getProfile().isHistoryConsent());
					assertEquals("", preview[0].getProfile().getExperience());
					assertEquals("", preview[0].getProfile().getSpace());
					assertEquals("", preview[0].getProfile().getPreferences());
					assertEquals("", preview[0].getProfile().getLimitations());
					assertEquals(baseline, a.state().app.profile);
					assertNull(a.state().app.routines.accepted);
					assertTrue(a.state().app.versions.isEmpty());
					assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "Propuesta · sin guardar"));
				});
				try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
					assertEquals("La consulta externa escribió antes de aceptar", before, db.exportSnapshot());
				}
				scenario.onActivity(a -> {
					View accept = MainFlowTest.text(a.getWindow().getDecorView(), "Aceptar");
					assertNotNull(accept);
					accept.requestRectangleOnScreen(new Rect(0, 0, accept.getWidth(), accept.getHeight()), true);
				});
				MainFlowTest.await();
				MainFlowTest.click(scenario, "Aceptar");
				waitIdle(scenario);
				long[] version = {0};
				scenario.onActivity(a -> {
					assertNotNull(a.state().assistant.accepted);
					version[0] = a.state().assistant.accepted.getVersionId();
					assertTrue(version[0] > 0);
					assertNull(a.state().assistant.preview);
				});
				String acceptedSnapshot = SnapshotCodec.encodeRoutine(preview[0].withVersionId(0));
				assertStored(context, preview[0], version[0], acceptedSnapshot);
				scenario.recreate();
				waitIdle(scenario);
				scenario.onActivity(a -> {
					assertEquals(preview[0].getProfile(), a.state().app.profile);
					assertEquals(1, a.state().app.versions.size());
					assertEquals(version[0], a.state().app.versions.get(0).getVersionId());
				});
				assertStored(context, preview[0], version[0], acceptedSnapshot);
				if ("true".equals(args.getString("captureLiveGemini")))
					capture(context, "gemini-live-accepted.png");
			} finally {
				// El cliente elimina inmediatamente su token y solicita revocación best-effort.
				scenario.onActivity(a -> {
					((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
						R.id.navigation)).setSelectedItemId(R.id.tab_assistant);
					if (!a.state().assistant.showConnection)
						a.state().assistant.toggleConnection();
					View disconnect = MainFlowTest.text(a.getWindow().getDecorView(), "Desconectar y revocar");
					if (disconnect != null && disconnect.isEnabled())
						disconnect.performClick();
					else
						a.state().assistant.disconnect();
				});
				scenario.onActivity(a -> assertFalse(a.state().assistant.connected()));
			}
		}
	}

	private static void assertStored(Context context, Routine preview, long version, String snapshot) {
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			assertEquals(preview.getProfile(), db.loadProfile());
			RoutineRepository routines = new RoutineRepository(db);
			assertEquals(1, routines.versions().size());
			Routine stored = routines.load(version);
			assertNotNull(stored);
			assertEquals(preview.getProposalId(), stored.getProposalId());
			assertEquals(snapshot, SnapshotCodec.encodeRoutine(stored.withVersionId(0)));
		}
	}

	private static void waitIdle(ActivityScenario<MainActivity> scenario) {
		long deadline = SystemClock.elapsedRealtime() + 45000;
		boolean[] ready = {false};
		do {
			MainFlowTest.await();
			scenario.onActivity(a -> ready[0] = !a.state().assistant.busy && !a.state().app.loading);
		} while (!ready[0] && SystemClock.elapsedRealtime() < deadline);
		assertTrue("Operación excedió 45 s; sin volcar respuestas ni credenciales", ready[0]);
	}

	/**
	 * Solo códigos enumerados y copy conocido: nunca reproduce payload, endpoint ni secretos.
	 */
	private static String safeStatus(String status) {
		if (status == null || status.isEmpty())
			return "sin estado";
		if (status.equals("Respuesta inválida o desactualizada. No se ha guardado ni abierto ningún ejercicio."))
			return status;
		if (status.startsWith("El conector opcional está desactivado."))
			return "Conector opcional desactivado";
		java.util.regex.Matcher code = java.util.regex.Pattern.compile(
				"^No se pudo completar la consulta \\(([A-Z_]{1,64})\\)\\. Puedes continuar en local o reintentar manualmente\\.$")
			.matcher(status);
		if (code.matches())
			return "No se pudo completar la consulta (" + code.group(1) + ")";
		return "estado no incluido en diagnóstico seguro";
	}

	private static void capture(Context context, String filename) throws Exception {
		Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
		assertNotNull(bitmap);
		try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), filename))) {
			assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		} finally {
			bitmap.recycle();
		}
	}
}
