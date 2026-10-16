package com.energym.app.ui;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.NutritionRepository;
import com.energym.app.nutrition.FoodEntry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

import static org.junit.Assert.*;

/**
 * El resumen también sirve a quien registra alimentación y peso antes de su primer entrenamiento.
 * La fixture utiliza exclusivamente el propietario de desarrollo, sin escribir en Firebase.
 */
@RunWith(AndroidJUnit4.class)
public final class ProgressNutritionFlowTest {

	@Test
	public void nutritionAndWeightRemainVisibleWithoutFinishedTraining() throws Exception {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		LocalDate today = LocalDate.now();
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
			NutritionRepository repo = new NutritionRepository(db);
			repo.weight(today.minusDays(7), 80);
			repo.weight(today, 79);
			repo.add(FoodEntry.create(today, FoodEntry.Meal.LUNCH, "Comida de prueba", "", FoodEntry.Unit.G,
				100, 250, 10, 30, 10));
			repo.add(FoodEntry.create(today.minusDays(60), FoodEntry.Meal.DINNER, "Comida antigua de prueba", "",
				FoodEntry.Unit.G, 100, 400, 20, 40, 15));
		}
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			for (int i = 0; i < 30; i++) {
				boolean[] ready = {false};
				scenario.onActivity(a -> ready[0] = a.state().app != null && a.state().accounts != null);
				if (ready[0])
					break;
				MainFlowTest.await();
			}
			scenario.onActivity(a -> a.state().accounts.enterDemo());
			boolean[] loaded = {false};
			for (int i = 0; i < 30 && !loaded[0]; i++) {
				MainFlowTest.await();
				scenario.onActivity(a -> loaded[0] = a.state().nutrition.weights.size() == 2 &&
					a.state().nutrition.entriesOn(today).size() == 1);
			}
			assertTrue("La fixture de nutrición debe cargarse desde SQLite", loaded[0]);
			scenario.onActivity(a -> {
				assertTrue(a.state().app.history.isEmpty());
				a.state().app.navigate(AppController.Screen.HISTORY);
			});
			MainFlowTest.await();
			// Progreso va por pestañas: el peso en Cuerpo y la comida del día en Calendario.
			scenario.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("history_tab_body").performClick());
			MainFlowTest.await();
			scenario.onActivity(a -> assertNotNull("La evolución del peso no depende de haber entrenado",
				MainFlowTest.text(a.getWindow().getDecorView(), "Peso corporal")));
			scenario.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("history_tab_calendar").performClick());
			MainFlowTest.await();
			scenario.onActivity(a -> {
				assertNotNull("El calendario no depende de haber entrenado",
					MainFlowTest.text(a.getWindow().getDecorView(), "Calendario"));
				assertTrue("Debe mostrar la comida del día seleccionado", containsText(a.getWindow().getDecorView(),
					"Comida: 250 kcal"));
			});
			LocalDate historical = today.minusDays(60);
			scenario.onActivity(a -> {
				assertEquals(0, a.state().nutrition.totals(historical)[0], 0);
				assertEquals(400, a.state().nutrition.historyCalories(historical), 0);
				assertEquals(2, a.state().nutrition.loggedDays().size());
			});
			long months = java.time.temporal.ChronoUnit.MONTHS.between(
				java.time.YearMonth.from(historical), java.time.YearMonth.from(today));
			for (int i = 0; i < months; i++)
				MainFlowTest.click(scenario, "Mes anterior");
			scenario.onActivity(a -> {
				com.energym.app.ui.charts.MonthCalendarView calendar = findCalendar(a.getWindow().getDecorView());
				assertNotNull(calendar);
				assertEquals("Mes visible antes de pulsar el día", "Calendario de " + historical.getMonthValue() + "/" +
					historical.getYear() + ": 0 días entrenados", calendar.getContentDescription().toString());
				assertTrue("El calendario debe estar medido", calendar.getWidth() > 0 && calendar.getHeight() > 0);
				float density = a.getResources().getDisplayMetrics().density;
				float cellW = calendar.getWidth() / 7f;
				int offset = historical.withDayOfMonth(1).getDayOfWeek().getValue() - 1;
				int index = offset + historical.getDayOfMonth() - 1;
				float x = cellW * (index % 7 + .5f);
				float y = 28 * density + Math.min(cellW, 52 * density) * (index / 7 + .5f);
				long time = android.os.SystemClock.uptimeMillis();
				android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time,
					android.view.MotionEvent.ACTION_DOWN, x, y, 0);
				android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 10,
					android.view.MotionEvent.ACTION_UP, x, y, 0);
				try {
					calendar.dispatchTouchEvent(down);
					calendar.dispatchTouchEvent(up);
				} finally {
					down.recycle();
					up.recycle();
				}
			});
			MainFlowTest.await();
			scenario.onActivity(a -> assertTrue("Debe seleccionarse " + NutritionFragment.label(historical, today),
				containsText(a.getWindow().getDecorView(), NutritionFragment.label(historical, today) + " · sin entreno")));
			scenario.onActivity(a -> assertTrue("El calendario debe conservar la comida anterior a la ventana del diario",
				containsText(a.getWindow().getDecorView(), "Comida: 400 kcal")));
		} finally {
			try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
				db.clearLocalData();
			}
		}
	}

	private static com.energym.app.ui.charts.MonthCalendarView findCalendar(android.view.View view) {
		if (view instanceof com.energym.app.ui.charts.MonthCalendarView)
			return (com.energym.app.ui.charts.MonthCalendarView) view;
		if (view instanceof android.view.ViewGroup) {
			android.view.ViewGroup group = (android.view.ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++) {
				com.energym.app.ui.charts.MonthCalendarView result = findCalendar(group.getChildAt(i));
				if (result != null)
					return result;
			}
		}
		return null;
	}

	private static boolean containsText(android.view.View view, String expected) {
		if (view instanceof android.widget.TextView &&
			((android.widget.TextView) view).getText().toString().contains(expected))
			return true;
		if (view instanceof android.view.ViewGroup) {
			android.view.ViewGroup group = (android.view.ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++)
				if (containsText(group.getChildAt(i), expected))
					return true;
		}
		return false;
	}
}
