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
			trainingPane(scenario, "mine");
			click(scenario, "Una automática ya");
			capture("proposal");
			click(scenario, "Guardar rutina");
			await();
			click(scenario, "Empezar entreno");
			await();
			capture("session");
			scenario.onActivity(a -> {
				ScrollView scroll =
					(ScrollView) a.getWindow().getDecorView().findViewById(R.id.screen_body).getParent();
				scroll.fullScroll(View.FOCUS_DOWN);
			});
			await();
			capture("session-metrics");
			edit(scenario, "track_reps", "8");
			scenario.recreate();
			await();
			scenario.onActivity(a -> assertEquals("8",
				((EditText) a.getWindow().getDecorView().findViewWithTag("track_reps")).getText().toString()));
			int[] count = {0};
			scenario.onActivity(a -> count[0] = a.state().app.sessions.session.getRoutine().getItems().size());
			for (int i = 0; i < count[0]; i++) {
				int index = i;
				scenario.onActivity(a -> a.state().app.sessions.select(index));
				await();
				logCurrent(scenario);
			}
			click(scenario, "Terminar entreno");
			await();
			capture("history");
			// El detalle de cada día está en Progreso › Calendario.
			scenario.onActivity(a -> {
				assertEquals(1, a.state().app.history.size());
				assertEquals(AppController.Screen.HISTORY, a.state().app.screen);
				a.getWindow().getDecorView().findViewWithTag("history_tab_calendar").performClick();
			});
			await();
			scenario.onActivity(a -> assertTrue(textContaining(a.getWindow().getDecorView(), "Peso corporal × 8")));
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
	public void accountConfigurationIsHonestAndDemoSurvivesRotation() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			await();
			click(scenario, "Cuenta");
			scenario.onActivity(a -> {
				boolean missing = a.state().accountStatus ==
					com.energym.app.controller.AccountController.Status.CONFIGURATION_NEEDED;
				assertEquals("La advertencia debe corresponder a la configuración de esta instalación", missing,
					text(a.getWindow().getDecorView(), "Google: configuración pendiente") != null);
				assertNotNull(text(a.getWindow().getDecorView(), a.getString(a.state().user == null ?
					R.string.google_continue : R.string.google_switch_account)));
			});
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
				assertNotNull(text(a.getWindow().getDecorView(), "Enviar mensaje"));
				assertTrue(textContaining(a.getWindow().getDecorView(), "Soy tu asistente"));
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
			click(scenario, "Seguir entrenando");
			logCurrent(scenario);
			assertSavedSquat(context, "8");
			training(scenario);
			click(scenario, "Seguir entrenando");
			await();
			assertSavedSquat(context, "8");
			scenario.onActivity(a -> assertEquals("8",
				((EditText) a.getWindow().getDecorView().findViewWithTag("track_reps")).getText().toString()));
			edit(scenario, "track_reps", "9");
			training(scenario);
			click(scenario, "Seguir entrenando");
			scenario.recreate();
			await();
			scenario.onActivity(a -> assertEquals("9",
				((EditText) a.getWindow().getDecorView().findViewWithTag("track_reps")).getText().toString()));
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
		trainingPane(scenario, "mine");
	}

	/** Fuerza: dos series de 8 a peso corporal con el registro estilo FitNotes; el resto, su formulario. */
	private void logCurrent(ActivityScenario<MainActivity> scenario) {
		boolean[] tracker = {false};
		scenario.onActivity(a -> tracker[0] = a.getWindow().getDecorView().findViewWithTag("track_kg") != null);
		if (tracker[0]) {
			edit(scenario, "track_kg", "0");
			edit(scenario, "track_reps", "8");
			click(scenario, "Guardar serie");
			await();
			click(scenario, "Guardar serie");
		} else {
			fillMetrics(scenario);
			click(scenario, "Guardar ejercicio");
		}
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
		if (root.getContentDescription() != null && value.contentEquals(root.getContentDescription()) &&
			root.isClickable())
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

	/** Abre (si está plegado) un apartado de Cuenta › Ajustes: backup, assistant, reminders, preferences, device. */
	static void setting(ActivityScenario<MainActivity> s, String key) {
		s.onActivity(a -> {
			View v = a.getWindow().getDecorView().findViewWithTag("setting_" + key);
			assertNotNull("Ajuste ausente: " + key, v);
			if ("›".contentEquals(((android.widget.TextView) v.findViewById(com.energym.app.R.id.food_kcal)).getText()))
				v.performClick();
		});
		await();
	}

	static void click(ActivityScenario<MainActivity> s, String label) {
		s.onActivity(a -> {
			// «Cuenta» está en el menú ☰ de arriba a la derecha.
			if ("Cuenta".equals(label)) {
				a.menuSelected(R.id.menu_account);
				return;
			}
			View v = text(a.getWindow().getDecorView(), label);
			assertNotNull("Botón ausente: " + label, v);
			assertTrue("Botón deshabilitado: " + label, v.isEnabled());
			v.performClick();
		});
		await();
	}

	static void trainingPane(ActivityScenario<MainActivity> scenario, String pane) {
		scenario.onActivity(activity -> {
			View tab = activity.getWindow().getDecorView().findViewWithTag("training_tab_" + pane);
			assertNotNull("Vista de Entreno disponible: " + pane, tab);
			assertTrue(tab.isEnabled());
			tab.performClick();
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


