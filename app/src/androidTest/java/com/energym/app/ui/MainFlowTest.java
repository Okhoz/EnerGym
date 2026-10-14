package com.energym.app.ui;

import android.view.*;
import android.widget.*;
import android.graphics.Bitmap;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.RoutineRepository;
import com.energym.app.data.SessionRepository;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;

import static org.junit.Assert.*;

/**
 * UI Android real. Eventos de vista directos evitan el inyector Espresso antiguo incompatible con API37.
 */
@RunWith(AndroidJUnit4.class)
public class MainFlowTest {
	@Before
	public void clearDemo() {
		try (EnerGymDatabase db = new EnerGymDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext(),
			EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
		}
	}

	@Test
	public void demoProfileProposalSessionHistoryAndRotation() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			capture("home");
			click(scenario, "Cuenta");
			capture("account");
			click(scenario, "Entrar en demo local");
			await();
			click(scenario, "Configurar mi perfil");
			capture("profile");
			edit(scenario, "preferences", "Texto conservado al rotar");
			scenario.recreate();
			await();
			scenario.onActivity(a -> assertEquals("Texto conservado al rotar",
				((EditText) a.getWindow().getDecorView().findViewWithTag("preferences")).getText().toString()));
			click(scenario, "Guardar perfil");
			await();
			capture("training");
			click(scenario, "Crear propuesta");
			capture("proposal");
			click(scenario, "Aceptar propuesta");
			await();
			click(scenario, "Empezar sesión");
			await();
			capture("session");
			scenario.onActivity(a -> {
				ScrollView scroll =
					(ScrollView) a.getWindow().getDecorView().findViewById(R.id.screen_body).getParent();
				scroll.fullScroll(View.FOCUS_DOWN);
			});
			await();
			capture("session-metrics");
			edit(scenario, "sets", "2");
			edit(scenario, "repetitions", "8");
			scenario.recreate();
			await();
			scenario.onActivity(a -> assertEquals("8",
				((EditText) a.getWindow().getDecorView().findViewWithTag("repetitions")).getText().toString()));
			int[] count = {0};
			scenario.onActivity(a -> count[0] = a.state().app.sessions.session.getRoutine().getItems().size());
			for (int i = 0; i < count[0]; i++) {
				fillMetrics(scenario);
				click(scenario, "Guardar ejercicio");
				await();
				if (i + 1 < count[0])
					click(scenario, "Siguiente ejercicio");
			}
			click(scenario, "Finalizar sesión");
			await();
			capture("history");
			scenario.onActivity(a -> {
				assertEquals(1, a.state().app.history.size());
				assertEquals(AppController.Screen.HISTORY, a.state().app.screen);
				assertNotNull(text(a.getWindow().getDecorView(), "Sesión finalizada"));
				assertTrue(textContaining(a.getWindow().getDecorView(), "Carga (kg; 0 = peso corporal): 0"));
			});
			click(scenario, "Cuenta");
			click(scenario, "Salir de la cuenta / demo");
			await();
			scenario.onActivity(a -> {
				assertNull(a.state().app.owner());
				assertTrue(a.state().app.history.isEmpty());
				assertNull(a.state().app.profile);
			});
		}
	}

	@Test
	public void missingFirebaseConfigurationIsHonestAndDemoSurvivesRotation() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			click(scenario, "Cuenta");
			scenario.onActivity(
				a -> assertNotNull(text(a.getWindow().getDecorView(), "Google: configuración pendiente")));
			click(scenario, "Entrar en demo local");
			scenario.recreate();
			await();
			scenario.onActivity(a -> {
				assertEquals(EnerGymDatabase.DEMO_OWNER_UID, a.state().app.owner());
				assertTrue(a.state().syncText().contains("demo local"));
			});
			scenario.onActivity(
				a -> ((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
					R.id.navigation)).setSelectedItemId(R.id.tab_assistant));
			await();
			capture("assistant");
			scenario.onActivity(a -> {
				assertNotNull(text(a.getWindow().getDecorView(), "Enviar local"));
				assertTrue(textContaining(a.getWindow().getDecorView(), "Guiado local · gratuito"));
			});
		}
	}

	@Test
	public void reopeningCachedDraftRetainsSavedResultsAndUnsavedText() throws Exception {
		android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			TrainingProfile profile = new TrainingProfile.Builder().build();
			db.saveProfile(profile);
			Routine proposed = new RoutinePlanner().create(profile, CatalogRepository.load(context).getExercises());
			long id = new RoutineRepository(db).saveAccepted(proposed);
			new SessionRepository(db).saveDraft(new Session(proposed.withVersionId(id)));
		}
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			click(scenario, "Cuenta");
			click(scenario, "Entrar en demo local");
			training(scenario);
			click(scenario, "Continuar borrador");
			fillMetrics(scenario);
			click(scenario, "Guardar ejercicio");
			await();
			assertSavedSquat(context, "8");
			training(scenario);
			click(scenario, "Continuar borrador");
			await();
			assertSavedSquat(context, "8");
			scenario.onActivity(a -> assertEquals("8",
				((EditText) a.getWindow().getDecorView().findViewWithTag("repetitions")).getText().toString()));
			edit(scenario, "repetitions", "9");
			training(scenario);
			click(scenario, "Continuar borrador");
			scenario.recreate();
			await();
			scenario.onActivity(a -> assertEquals("9",
				((EditText) a.getWindow().getDecorView().findViewWithTag("repetitions")).getText().toString()));
			assertSavedSquat(context, "8");
		}
	}

	private void assertSavedSquat(android.content.Context context, String repetitions) {
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			Session stored = new SessionRepository(db).pending().get(0);
			assertNotNull("Valores guardados perdidos al reabrir", stored.getResults().get("bodyweight-squat"));
			assertEquals(repetitions, stored.getResults().get("bodyweight-squat").get("repetitions"));
		}
	}

	private void training(ActivityScenario<MainActivity> scenario) {
		scenario.onActivity(a -> ((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
			R.id.navigation)).setSelectedItemId(R.id.tab_training));
		await();
	}

	private void fillMetrics(ActivityScenario<MainActivity> s) {
		String[][] values =
			{{"sets", "2"}, {"repetitions", "8"}, {"loadKg", "0"}, {"restSeconds", "30"}, {"durationSeconds", "20"},
				{"timeMinutes", "12,5"}, {"intervals", "1"}, {"distanceKm", "2,8"}, {"blocks", "2"},
				{"durationMinutes", "10"}, {"effort", "6,5"}};
		s.onActivity(a -> {
			for (String[] entry : values) {
				EditText field = a.getWindow().getDecorView().findViewWithTag(entry[0]);
				if (field != null)
					field.setText(entry[1]);
			}
		});
	}

	static View text(View root, String value) {
		if (root instanceof TextView && value.contentEquals(((TextView) root).getText()))
			return root;
		if (root instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) root;
			for (int i = 0; i < group.getChildCount(); i++) {
				View v = text(group.getChildAt(i), value);
				if (v != null)
					return v;
			}
		}
		return null;
	}

	static boolean textContaining(View root, String value) {
		if (root instanceof TextView && ((TextView) root).getText().toString().contains(value))
			return true;
		if (root instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) root;
			for (int i = 0; i < group.getChildCount(); i++)
				if (textContaining(group.getChildAt(i), value))
					return true;
		}
		return false;
	}

	static void click(ActivityScenario<MainActivity> s, String label) {
		s.onActivity(a -> {
			View v = text(a.getWindow().getDecorView(), label);
			assertNotNull("Botón ausente: " + label, v);
			assertTrue("Botón deshabilitado: " + label, v.isEnabled());
			v.performClick();
		});
		await();
	}

	static void edit(ActivityScenario<MainActivity> s, String key, String value) {
		s.onActivity(a -> {
			EditText view = a.getWindow().getDecorView().findViewWithTag(key);
			assertNotNull(key, view);
			view.setText(value);
		});
	}

	static void await() {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		android.os.SystemClock.sleep(250);
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}

	private void capture(String name) throws Exception {
		Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
		File folder =
			new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),
				"task4-screens");
		assertTrue(folder.isDirectory() || folder.mkdirs());
		try (OutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
			assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		}
		bitmap.recycle();
		android.os.ParcelFileDescriptor copied = InstrumentationRegistry.getInstrumentation().getUiAutomation()
			.executeShellCommand(
				"cp " + new File(folder, name + ".png").getAbsolutePath() + " /data/local/tmp/energym-task4-" + name +
					".png");
		try (InputStream result = new android.os.ParcelFileDescriptor.AutoCloseInputStream(copied)) {
			while (result.read() != -1) {
			}
		}
	}
}


