package com.energym.app.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Mide vistas Android y controles del proceso privado; captureOnly conserva evidencia del baseline.
 */
@RunWith(AndroidJUnit4.class)
public final class ProfessionalSpacingTest {
	private final UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	private final List<String> problems = new ArrayList<>();
	private final StringBuilder measurements = new StringBuilder();
	private final boolean captureOnly = "true".equals(InstrumentationRegistry.getArguments().getString("captureOnly"));
	private final String phase =
		captureOnly ? "before" : "after-" + InstrumentationRegistry.getArguments().getString("evidenceRun", "current");

	@Test
	public void rootActionsAndImeHaveSpaceAtNormalAndLargeFont() throws Exception {
		String font = shell("settings get system font_scale").trim();
		String keyboard = shell("settings get secure show_ime_with_hard_keyboard").trim();
		try {
			for (String scale : new String[]{"1.0", "1.3"}) {
				shell("settings put system font_scale " + scale);
				shell("settings put secure show_ime_with_hard_keyboard 1");
				try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
					ready(scenario);
					scenario.onActivity(a -> a.state().accounts.enterDemo());
					MainFlowTest.await();
					for (AppController.Screen screen : new AppController.Screen[]{AppController.Screen.HOME,
						AppController.Screen.TRAINING, AppController.Screen.ASSISTANT, AppController.Screen.HISTORY,
						AppController.Screen.ACCOUNT, AppController.Screen.PROFILE}) {
						scenario.onActivity(a -> a.state().app.navigate(screen));
						MainFlowTest.await();
						scenario.onActivity(a -> {
							inspect(a.findViewById(R.id.main), screen + "-" + scale, 24);
							TextView account = a.findViewById(R.id.account);
							check("Cuenta".contentEquals(
									account.getContentDescription() == null ? "" : account.getContentDescription()),
								"Cuenta debe conservar nombre accesible");
						});
						capture(screen.name().toLowerCase(Locale.ROOT) + "-" + scale);
					}
					for (AppController.Screen screen : new AppController.Screen[]{AppController.Screen.PROFILE,
						AppController.Screen.ASSISTANT}) {
						scenario.onActivity(a -> a.state().app.navigate(screen));
						MainFlowTest.await();
						scenario.onActivity(a -> {
							EditText input = a.getWindow().getDecorView().findViewWithTag(
								screen == AppController.Screen.PROFILE ? "preferences" : "assistant_message");
							assertNotNull(input);
							input.requestFocus();
							((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,
								InputMethodManager.SHOW_IMPLICIT);
						});
						boolean[] shown = {false};
						for (int i = 0; i < 30 && !shown[0]; i++) {
							MainFlowTest.await();
							scenario.onActivity(a -> shown[0] =
								ViewCompat.getRootWindowInsets(a.findViewById(R.id.main))
									.isVisible(WindowInsetsCompat.Type.ime()));
						}
						assertTrue("IME real ausente", shown[0]);
						String label = screen == AppController.Screen.PROFILE ? "Guardar perfil" : "Enviar local";
						scenario.onActivity(a -> {
							View action = MainFlowTest.text(a.getWindow().getDecorView(), label);
							assertNotNull(action);
							action.requestRectangleOnScreen(new Rect(0, 0, action.getWidth(), action.getHeight()),
								true);
						});
						MainFlowTest.await();
						capture(screen.name().toLowerCase(Locale.ROOT) + "-ime-" + scale);
						scenario.onActivity(a -> {
							View action = MainFlowTest.text(a.getWindow().getDecorView(), label);
							Rect visible = new Rect();
							check(action.getGlobalVisibleRect(visible) && visible.height() >= dp(a, 48),
								label + " debe mostrar al menos48dp con IME");
						});
						automation.performGlobalAction(
							android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
						MainFlowTest.await();
					}
				}
			}
		} finally {
			restore("system", "font_scale", font);
			restore("secure", "show_ime_with_hard_keyboard", keyboard);
			write("roots-measurements.txt", measurements.toString());
		}
		finish();
	}

	@Test
	public void narrowRealWidgetsKeepTextAndRowsInsideBounds() throws Exception {
		InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
			for (int width : new int[]{320, 360}) {
				Configuration config = new Configuration(context.getResources().getConfiguration());
				config.fontScale = 1.3f;
				Context themed =
					new ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_EnerGym);
				for (int layout : new int[]{R.layout.activity_main, R.layout.activity_anatomy}) {
					View view = LayoutInflater.from(themed).inflate(layout, null, false);
					View controls = view.findViewById(R.id.viewer_controls);
					if (controls != null)
						controls.setVisibility(View.VISIBLE);
					View back = view.findViewById(R.id.back);
					if (back != null)
						back.setVisibility(View.VISIBLE);
					int w = dp(themed, width), h = dp(themed, 900);
					view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
						View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
					view.layout(0, 0, w, h);
					inspect(view, "layout-" + layout + "-" + width, controls == null ? 24 : 16);
					if (controls != null && width == 320) {
						check(view.findViewById(R.id.viewer_speed_quarter).getTop() !=
								view.findViewById(R.id.viewer_speed_normal).getTop(),
							"A320dp la tercera velocidad debe pasar a otra fila sin reducir su texto");
					}
				}
			}
		});
		write("narrow-measurements.txt", measurements.toString());
		finish();
	}

	@Test
	public void remoteViewerButtonsHaveMeasuredGapsAndRemainReachable() throws Exception {
		String font = shell("settings get system font_scale").trim();
		AccessibilityServiceInfo info = automation.getServiceInfo();
		int oldFlags = info.flags;
		info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
		automation.setServiceInfo(info);
		String[][] groups = {{"viewer_speed_quarter", "viewer_speed_half", "viewer_speed_normal"},
			{"viewer_initial", "viewer_middle", "viewer_final"},
			{"viewer_rotate_left", "viewer_rotate_right", "viewer_zoom_in", "viewer_zoom_out", "viewer_front",
				"viewer_side", "viewer_reset"}};
		try {
			for (String scale : new String[]{"1.0", "1.3"}) {
				shell("settings put system font_scale " + scale);
				try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
					ready(scenario);
					scenario.onActivity(a -> a.state().accounts.enterDemo());
					MainFlowTest.await();
					scenario.onActivity(a -> {
						a.state().app.openExercise(a.state().app.catalog.findExercise("standard-pushup"));
						a.state().app.requestVisualization();
					});
					for (int i = 0; i < 100; i++) {
						AccessibilityNodeInfo n = node("viewer_play");
						if (n != null && n.isEnabled())
							break;
						Thread.sleep(200);
					}
					assertNotNull("Visor local no preparado", node("viewer_play"));
					for (int g = 0; g < groups.length; g++) {
						String[] ids = groups[g];
						for (String id : ids) {
							AccessibilityNodeInfo n = node(id);
							assertNotNull(id, n);
							n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
							Thread.sleep(250);
							n = node(id);
							Rect bounds = new Rect();
							n.getBoundsInScreen(bounds);
							measurements.append(scale).append(' ').append(id).append(' ').append(bounds).append('\n');
							check(n.isVisibleToUser() && bounds.width() >= dp(context, 48) &&
								bounds.height() >= dp(context, 48), id + " alcanzable y48dp " + bounds);
							for (String other : ids) {
								if (other.equals(id))
									break;
								AccessibilityNodeInfo sibling = node(other);
								if (sibling != null && sibling.isVisibleToUser()) {
									Rect b = new Rect();
									sibling.getBoundsInScreen(b);
									gap(bounds, b, context.getResources().getDisplayMetrics().density, 16,
										id + "/" + other);
								}
							}
							capture("viewer-" + id + "-" + scale);
						}
					}
					assertTrue(node("viewer_back").performAction(AccessibilityNodeInfo.ACTION_CLICK));
					Thread.sleep(500);
				}
			}
		} finally {
			context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName()));
			restore("system", "font_scale", font);
			info.flags = oldFlags;
			automation.setServiceInfo(info);
			write("viewer-measurements.txt", measurements.toString());
		}
		finish();
	}

	private void inspect(View view, String label, int vertical) {
		if (view.getVisibility() != View.VISIBLE)
			return;
		if (view instanceof MaterialButton) {
			Rect hit = new Rect();
			view.getHitRect(hit);
			if (view.getParent() instanceof ViewGroup) {
				ViewGroup parent = (ViewGroup) view.getParent();
				check(
					hit.left >= 0 && hit.right <= parent.getWidth() && hit.top >= 0 && hit.bottom <= parent.getHeight(),
					label + " botón fuera de su contenedor: " + hit);
			}
			measurements.append(label).append(' ').append(((MaterialButton) view).getText()).append(' ').append(hit)
				.append('\n');
			check(hit.width() >= dp(view.getContext(), 48) && hit.height() >= dp(view.getContext(), 48),
				label + " touch48 " + hit);
		}
		if (view instanceof TextView) {
			TextView text = (TextView) view;
			if (text.getLayout() != null && text.getText().length() > 0) {
				for (int line = 0; line < text.getLayout().getLineCount(); line++) {
					check(text.getLayout().getEllipsisCount(line) == 0 && text.getLayout().getLineMax(line) <=
							text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1,
						label + " texto recortado: " + text.getText());
				}
			}
		}
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++) {
				View child = group.getChildAt(i);
				inspect(child, label, vertical);
				if (!(child instanceof MaterialButton) || child.getVisibility() != View.VISIBLE)
					continue;
				Rect a = new Rect();
				child.getHitRect(a);
				for (int j = 0; j < i; j++) {
					View other = group.getChildAt(j);
					if (!(other instanceof MaterialButton) || other.getVisibility() != View.VISIBLE)
						continue;
					Rect b = new Rect();
					other.getHitRect(b);
					gap(a, b, view.getResources().getDisplayMetrics().density, vertical, label);
				}
			}
		}
	}

	private void gap(Rect a, Rect b, float density, int vertical, String label) {
		check(!Rect.intersects(a, b), label + " solapamiento " + a + " " + b);
		if (Math.min(a.right, b.right) > Math.max(a.left, b.left)) {
			int gap = Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom);
			check(gap + 1 >= vertical * density, label + " gap vertical=" + gap / density + " <" + vertical);
		} else if (Math.min(a.bottom, b.bottom) > Math.max(a.top, b.top)) {
			int gap = Math.max(a.left, b.left) - Math.min(a.right, b.right);
			check(gap + 1 >= 12 * density, label + " gap horizontal=" + gap / density + " <12");
		}
	}

	private void ready(ActivityScenario<MainActivity> scenario) {
		boolean[] ready = {false};
		for (int i = 0; i < 80 && !ready[0]; i++) {
			MainFlowTest.await();
			scenario.onActivity(a -> ready[0] = a.state().app != null);
		}
		assertTrue("App no preparada", ready[0]);
	}

	private int dp(Context c, int value) {
		return Math.round(value * c.getResources().getDisplayMetrics().density);
	}

	private void check(boolean ok, String message) {
		if (!ok)
			problems.add(message);
	}

	private void finish() throws IOException {
		write("violations-" + System.nanoTime() + ".txt", String.join("\n", problems));
		if (!captureOnly)
			assertTrue(String.join("\n", problems), problems.isEmpty());
	}

	private AccessibilityNodeInfo node(String id) {
		AccessibilityNodeInfo root = automation.getRootInActiveWindow();
		if (root == null)
			return null;
		List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.energym.app:id/" + id);
		return nodes.isEmpty() ? null : nodes.get(0);
	}

	private File folder() {
		File f = new File(context.getExternalFilesDir(null), "task12-" + phase);
		assertTrue(f.isDirectory() || f.mkdirs());
		return f;
	}

	private void write(String name, String value) throws IOException {
		try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(folder(), name)), "UTF-8")) {
			out.write(value);
		}
	}

	private void capture(String name) throws IOException {
		Bitmap bitmap = automation.takeScreenshot();
		assertNotNull(bitmap);
		try (OutputStream out = new FileOutputStream(new File(folder(), name + ".png"))) {
			assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		} finally {
			bitmap.recycle();
		}
	}

	private String shell(String command) throws IOException {
		try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
			automation.executeShellCommand(command)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int n;
			while ((n = in.read(buffer)) != -1)
				out.write(buffer, 0, n);
			return out.toString("UTF-8");
		}
	}

	private void restore(String table, String key, String value) throws IOException {
		shell(value.equals("null") ? "settings delete " + table + " " + key :
			"settings put " + table + " " + key + " " + value);
	}
}
