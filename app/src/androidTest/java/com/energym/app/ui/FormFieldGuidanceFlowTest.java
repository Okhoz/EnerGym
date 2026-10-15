package com.energym.app.ui;

import android.app.Dialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Verifica las filas compartidas con vistas transitorias, sin guardar datos ni cambiar de cuenta. */
@RunWith(AndroidJUnit4.class)
public final class FormFieldGuidanceFlowTest {

	@Test
	public void examplesRemainHintsAndOnlyUserEditsEmitValues() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			scenario.onActivity(activity -> {
				ScreenFragment host = (ScreenFragment) activity.getSupportFragmentManager()
					.findFragmentById(R.id.content);
				LinearLayout rows = new LinearLayout(activity);
				rows.setOrientation(LinearLayout.VERTICAL);
				List<String> edits = new ArrayList<>();
				host.textField(rows, "age", "Edad (años)", "", true, true, edits::add);
				EditText age = rows.findViewWithTag("age");
				assertNotNull(age.getHint());
				assertFalse(age.getHint().toString().trim().isEmpty());
				assertEquals("", age.getText().toString());
				assertTrue(edits.isEmpty());
				assertEquals(InputType.TYPE_CLASS_NUMBER,
					age.getInputType() & InputType.TYPE_MASK_CLASS);
				TextView caption = rows.findViewById(R.id.field_caption);
				assertEquals(age.getId(), caption.getLabelFor());
				age.setText("32");
				age.setText("");
				assertEquals(Arrays.asList("32", ""), edits);
				assertFalse(age.getHint().toString().trim().isEmpty());

				List<String> restoredEdits = new ArrayList<>();
				host.textField(rows, "weightKg", "Peso actual (kg)", "72.5", true, false,
					restoredEdits::add);
				EditText weight = rows.findViewWithTag("weightKg");
				assertEquals("72.5", weight.getText().toString());
				assertTrue(restoredEdits.isEmpty());
				assertTrue((weight.getInputType() & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
			});
		}
	}

	@Test
	public void guidanceKeepsExistingChoiceAndEmitsOnlyChangedSelection() throws Exception {
		Dialog[] dialog = {null};
		Spinner[] spinner = {null};
		List<String> selections = new ArrayList<>();
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			try {
				scenario.onActivity(activity -> {
					ScreenFragment host = (ScreenFragment) activity.getSupportFragmentManager()
						.findFragmentById(R.id.content);
					LinearLayout rows = new LinearLayout(activity);
					rows.setOrientation(LinearLayout.VERTICAL);
					host.choice(rows, "sex", "Sexo biológico (para la fórmula)",
						Arrays.asList("FEMALE", "MALE"), Arrays.asList("Mujer", "Hombre"),
						"MALE", "La fórmula utiliza este dato para estimar el gasto en reposo.", selections::add);
					spinner[0] = rows.findViewWithTag("sex");
					TextView caption = rows.findViewById(R.id.choice_label);
					assertEquals(spinner[0].getId(), caption.getLabelFor());
					assertEquals(2, spinner[0].getCount());
					dialog[0] = new Dialog(activity);
					dialog[0].setContentView(rows);
					dialog[0].show();
				});
				InstrumentationRegistry.getInstrumentation().waitForIdleSync();
				scenario.onActivity(activity -> {
					assertEquals("Hombre", spinner[0].getSelectedItem());
					assertTrue(selections.isEmpty());
					spinner[0].setSelection(0);
				});
				InstrumentationRegistry.getInstrumentation().waitForIdleSync();
				scenario.onActivity(activity -> assertEquals(Arrays.asList("FEMALE"), selections));
			} finally {
				scenario.onActivity(activity -> {
					if (dialog[0] != null) dialog[0].dismiss();
				});
			}
		}
	}

	@Test
	public void helpWrapsWithLargeTextWithoutBecomingAnEditableValue() throws Exception {
		Dialog[] dialog = {null};
		TextView[] help = {null};
		String explanation = "Cuenta qué actividades has practicado y durante cuánto tiempo. Puedes dejarlo vacío.";
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			try {
				scenario.onActivity(activity -> {
					ScreenFragment host = (ScreenFragment) activity.getSupportFragmentManager()
						.findFragmentById(R.id.content);
					LinearLayout rows = new LinearLayout(activity);
					rows.setOrientation(LinearLayout.VERTICAL);
					host.textField(rows, "experience", "Experiencia (opcional)", "", false, false,
						explanation, value -> fail("Mostrar ayuda no debe emitir un valor"));
					TextInputLayout field = rows.findViewById(R.id.field_label);
					help[0] = field.findViewById(com.google.android.material.R.id.textinput_helper_text);
					assertNotNull(help[0]);
					help[0].setTextSize(24);
					EditText input = rows.findViewWithTag("experience");
					assertEquals("", input.getText().toString());
					dialog[0] = new Dialog(activity);
					dialog[0].setContentView(rows);
					dialog[0].show();
					int width = Math.round(260 * activity.getResources().getDisplayMetrics().density);
					dialog[0].getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
				});
				InstrumentationRegistry.getInstrumentation().waitForIdleSync();
				scenario.onActivity(activity -> {
					assertEquals(explanation, help[0].getText().toString());
					assertTrue("La ayuda debe poder ocupar varias líneas", help[0].getLineCount() > 1);
					for (int line = 0; line < help[0].getLineCount(); line++)
						assertEquals(0, help[0].getLayout().getEllipsisCount(line));
				});
			} finally {
				scenario.onActivity(activity -> {
					if (dialog[0] != null) dialog[0].dismiss();
				});
			}
		}
	}

	private static void ready(ActivityScenario<MainActivity> scenario) throws Exception {
		boolean[] loaded = {false};
		for (int attempt = 0; attempt < 100 && !loaded[0]; attempt++) {
			InstrumentationRegistry.getInstrumentation().waitForIdleSync();
			scenario.onActivity(activity -> loaded[0] = activity.state().app != null &&
				activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ScreenFragment);
			if (!loaded[0]) Thread.sleep(50);
		}
		assertTrue("El formulario debe estar preparado", loaded[0]);
	}
}
