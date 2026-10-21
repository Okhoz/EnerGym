package com.energym.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.RoutineRepository;
import com.energym.app.domain.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Pesos con decimales en español: la rutina, el entreno y la calculadora de discos muestran «92,5»; el campo de peso
 * admite coma y punto, y lo guardado sigue con punto («92.5:3»).
 */
@RunWith(AndroidJUnit4.class)
public final class WeightDecimalCommaTest {
	private static final Pattern DOT = Pattern.compile("\\d\\.\\d");
	private static final String SQUAT = "barbell-back-squat";
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Before
	public void gzclpWithNinetyTwoAndAHalf() throws Exception {
		Units.setLb(context, false);
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
			// Gimnasio con todo el material y tiempo de sobra: la rutina con barra es válida para el perfil.
			TrainingProfile profile = new TrainingProfile.Builder().environment(TrainingProfile.Environment.GYM)
				.category(TrainingProfile.Category.GYM).resources(EnumSet.allOf(TrainingProfile.Resource.class))
				.durationMinutes(120).build();
			db.saveProfile(profile);
			// GZCLP con un 1RM de 120 kg: la sentadilla sale a 92,5 kg.
			Routine r = ClassicPrograms.routine(ClassicPrograms.Program.GZCLP, 0, CatalogRepository.load(context).getExercises(),
				Collections.singletonMap(SQUAT, 120.0), profile);
			assertEquals("92.5", r.getItems().get(0).getPrescription().get("loadKg"));
			assertTrue("Rutina válida para el perfil", r.isValidFor(profile));
			new RoutineRepository(db).saveAccepted(r);
		}
	}

	@After
	public void clear() {
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
		}
	}

	/** El diálogo de discos se lee con WindowInspector (API 29). */
	@Test
	@SdkSuppress(minSdkVersion = 29)
	public void routineSessionAndPlatesShowTheComma() {
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			MainFlowTest.click(s, "Cuenta");
			MainFlowTest.click(s, "Entrar en demo local");
			training(s);
			MainFlowTest.click(s, "Ver ejercicios");
			s.onActivity(a -> {
				View squat = a.getWindow().getDecorView().findViewWithTag("proposal_item_0");
				assertNotNull("Sentadilla en la rutina", squat);
				assertTrue(all(squat), all(squat).contains("5 × 3 · 92,5 kg"));
			});
			training(s);
			MainFlowTest.click(s, "Empezar entreno");
			waitFor(s, a -> a.getWindow().getDecorView().findViewWithTag("track_kg") != null);
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertEquals("92,5", field(root).getText().toString());
				assertTrue(all(root), all(root).contains("Objetivo de hoy: 5 × 3 · 92,5 kg"));
				TextView warmup = root.findViewWithTag("track_warmup");
				assertNotNull("Calentamiento", warmup);
				assertFalse(warmup.getText().toString(), DOT.matcher(warmup.getText()).find());
				// − / + escriben con coma.
				ViewGroup row = (ViewGroup) field(root).getParent();
				row.getChildAt(2).performClick();
				assertEquals("95", field(root).getText().toString());
				row.getChildAt(0).performClick();
				assertEquals("92,5", field(root).getText().toString());
			});
			// El campo acepta punto y coma.
			saveSet(s, "90.5", 1);
			saveSet(s, "92,5", 2);
			s.onActivity(a -> {
				View root = a.getWindow().getDecorView();
				assertEquals(Arrays.asList(new SetLog.Entry(90.5, 3), new SetLog.Entry(92.5, 3)), a.state().app.sessions.sets());
				assertEquals("Lo guardado sigue con punto", "90.5:3;92.5:3",
					a.state().app.sessions.session.getResults().get(SQUAT).get(SetLog.KEY));
				TextView repeat = root.findViewWithTag("track_repeat");
				assertTrue(repeat.getText().toString(), repeat.getText().toString().contains("92,5 kg × 3"));
				assertTrue(all(root), all(root).contains("90,5 kg ×"));
				assertFalse(all(root), DOT.matcher(all(root)).find());
				root.findViewWithTag("track_more").performClick();
			});
			MainFlowTest.await();
			s.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("track_plates").performClick());
			MainFlowTest.await();
			s.onActivity(a -> {
				StringBuilder dialog = new StringBuilder();
				View close = null;
				for (View w : android.view.inspector.WindowInspector.getGlobalWindowViews())
					if (w != a.getWindow().getDecorView() && w.isShown()) {
						dialog.append(all(w));
						close = close != null ? close : MainFlowTest.text(w, "Cerrar");
					}
				String text = dialog.toString();
				// 92,5 = barra de 20 + 36,25 por lado (25 + 10 + 1,25).
				assertTrue(text, text.contains("Discos para 92,5 kg"));
				assertTrue(text, text.contains("25  +  10  +  1,25 kg"));
				assertFalse(text, DOT.matcher(text).find());
				assertNotNull("Botón Cerrar del diálogo", close);
				close.performClick();
			});
			MainFlowTest.await();
		}
	}

	private void saveSet(ActivityScenario<MainActivity> s, String kg, int count) {
		MainFlowTest.edit(s, "track_kg", kg);
		MainFlowTest.click(s, "Guardar serie");
		waitFor(s, a -> !a.state().app.sessions.busy && a.state().app.sessions.sets().size() == count);
	}

	private static void training(ActivityScenario<MainActivity> s) {
		s.onActivity(a -> ((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
			R.id.navigation)).setSelectedItemId(R.id.tab_training));
		MainFlowTest.await();
		MainFlowTest.trainingPane(s, "mine");
	}

	private static void waitFor(ActivityScenario<MainActivity> s, java.util.function.Predicate<MainActivity> ready) {
		boolean[] done = {false};
		for (int i = 0; i < 40 && !done[0]; i++) {
			MainFlowTest.await();
			s.onActivity(a -> done[0] = ready.test(a));
		}
		assertTrue("La pantalla no llegó a su estado", done[0]);
	}

	private static EditText field(View root) {
		EditText kg = root.findViewWithTag("track_kg");
		assertNotNull("Campo de peso", kg);
		return kg;
	}

	/** Todo el texto visible bajo una vista, una línea por TextView. */
	private static String all(View v) {
		StringBuilder b = new StringBuilder();
		if (v instanceof TextView && v.isShown())
			b.append(((TextView) v).getText()).append('\n');
		if (v instanceof ViewGroup)
			for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
				b.append(all(((ViewGroup) v).getChildAt(i)));
		return b.toString();
	}
}
