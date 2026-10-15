package com.energym.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.data.RoutineRepository;
import com.energym.app.data.SessionRepository;
import com.energym.app.domain.Routine;
import com.energym.app.domain.RoutinePlanner;
import com.energym.app.domain.Session;
import com.energym.app.domain.TrainingProfile;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;

import static org.junit.Assert.*;

/**
 * Comprueba destinos, aislamiento de cuenta y contadores procedentes de un borrador persistido.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeNavigationTest {
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	@Test
	public void shortcutsRespectAccountAndKeepNavigationAndRealDraftCount() throws Exception {
		try (EnerGymDatabase db = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
			TrainingProfile profile = new TrainingProfile.Builder().build();
			db.saveProfile(profile);
			Routine routine = new RoutinePlanner().create(profile, CatalogRepository.load(context).getExercises());
			long version = new RoutineRepository(db).saveAccepted(routine);
			new SessionRepository(db).saveDraft(new Session(routine.withVersionId(version)));
		}
		String font = shell("settings get system font_scale").trim();
		try {
			for (String scale : new String[]{"1.0", "1.3"}) {
				shell("settings put system font_scale " + scale);
				try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
					ready(scenario);
					scenario.onActivity(a -> a.state().accounts.signOut());
					MainFlowTest.await();
					home(scenario);
					scenario.onActivity(a -> {
						assertEquals("0", ((TextView) a.findViewById(R.id.home_drafts)).getText().toString());
						assertEquals("0", ((TextView) a.findViewById(R.id.home_sessions)).getText().toString());
					});
					capture("home-signed-out-" + scale);
					click(scenario, R.id.quick_profile);
					scenario.onActivity(a -> assertEquals(Screen.ACCOUNT, a.state().app.screen));
					scenario.onActivity(a -> a.state().accounts.enterDemo());
					MainFlowTest.await();
					home(scenario);
					scenario.onActivity(a -> {
						assertEquals("1", ((TextView) a.findViewById(R.id.home_drafts)).getText().toString());
						assertEquals("0", ((TextView) a.findViewById(R.id.home_sessions)).getText().toString());
					});
					capture("home-demo-" + scale);
					click(scenario, R.id.quick_profile);
					scenario.onActivity(a -> {
						assertEquals(Screen.PROFILE, a.state().app.screen);
						assertEquals(R.id.tab_home,
							((BottomNavigationView) a.findViewById(R.id.navigation)).getSelectedItemId());
					});
					click(scenario, R.id.back);
					scenario.onActivity(a -> assertEquals(Screen.HOME, a.state().app.screen));
					click(scenario, R.id.quick_training);
					scenario.onActivity(a -> {
						assertEquals(Screen.TRAINING, a.state().app.screen);
						assertEquals(R.id.tab_training,
							((BottomNavigationView) a.findViewById(R.id.navigation)).getSelectedItemId());
					});
					home(scenario);
					click(scenario, R.id.quick_assistant);
					scenario.onActivity(a -> {
						assertEquals(Screen.ASSISTANT, a.state().app.screen);
						assertEquals(R.id.tab_assistant,
							((BottomNavigationView) a.findViewById(R.id.navigation)).getSelectedItemId());
						assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Enviar mensaje"));
					});
				}
			}
		} finally {
			shell(font.equals("null") ? "settings delete system font_scale" : "settings put system font_scale " + font);
		}
	}

	@Test
	public void quickActionsAndCountersFit320And360AtLargeFont() {
		InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
			for (int width : new int[]{320, 360}) {
				Configuration config = new Configuration(context.getResources().getConfiguration());
				config.fontScale = 1.3f;
				Context themed =
					new ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_EnerGym);
				View view = LayoutInflater.from(themed).inflate(R.layout.row_home_overview, null, false);
				((TextView) view.findViewById(R.id.home_sessions)).setText("128");
				((TextView) view.findViewById(R.id.home_drafts)).setText("12");
				int w = dp(themed, width - 40);
				view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
				view.layout(0, 0, w, view.getMeasuredHeight());
				inspect(view);
				int previousRight = -1;
				for (int id : new int[]{R.id.quick_profile, R.id.quick_training, R.id.quick_assistant}) {
					View button = view.findViewById(id);
					Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
					((ViewGroup) view).offsetDescendantRectToMyCoords(button, bounds);
					assertTrue(bounds.width() >= dp(themed, 48) && bounds.height() >= dp(themed, 48));
					assertTrue(bounds.left >= 0 && bounds.right <= w);
					assertNotNull(button.getContentDescription());
					if (previousRight >= 0)
						assertTrue(bounds.left - previousRight >= dp(themed, 12));
					previousRight = bounds.right;
				}
			}
		});
	}

	private void inspect(View view) {
		if (view instanceof TextView) {
			TextView text = (TextView) view;
			if (text.getLayout() != null)
				for (int line = 0; line < text.getLayout().getLineCount(); line++) {
					assertEquals(0, text.getLayout().getEllipsisCount(line));
					assertTrue(text.getText().toString(), text.getLayout().getLineMax(line) <=
						text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
				}
		}
		if (view instanceof ViewGroup)
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
				inspect(((ViewGroup) view).getChildAt(i));
	}

	private void ready(ActivityScenario<MainActivity> scenario) {
		boolean[] ready = {false};
		for (int i = 0; i < 80 && !ready[0]; i++) {
			MainFlowTest.await();
			scenario.onActivity(a -> ready[0] = a.state().app != null);
		}
		assertTrue(ready[0]);
	}

	private void home(ActivityScenario<MainActivity> scenario) {
		scenario.onActivity(a -> a.state().app.navigate(Screen.HOME));
		MainFlowTest.await();
	}

	private void click(ActivityScenario<MainActivity> scenario, int id) {
		scenario.onActivity(a -> {
			assertTrue(a.findViewById(id).performClick());
		});
		MainFlowTest.await();
	}

	private int dp(Context c, int value) {
		return Math.round(value * c.getResources().getDisplayMetrics().density);
	}

	private void capture(String name) throws IOException {
		Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
		assertNotNull(bitmap);
		File folder = new File(context.getExternalFilesDir(null), "task13-screens");
		assertTrue(folder.isDirectory() || folder.mkdirs());
		try (OutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
			assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		} finally {
			bitmap.recycle();
		}
	}

	private String shell(String command) throws IOException {
		try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
			InstrumentationRegistry.getInstrumentation().getUiAutomation()
				.executeShellCommand(command)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] bytes = new byte[1024];
			int n;
			while ((n = in.read(bytes)) != -1)
				out.write(bytes, 0, n);
			return out.toString("UTF-8");
		}
	}
}
