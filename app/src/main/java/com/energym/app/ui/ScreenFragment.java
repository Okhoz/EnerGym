package com.energym.app.ui;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.*;
import java.util.function.*;

/**
 * VISTA MVC compartida: infla filas XML, muestra texto y remite eventos sin persistencia ni decisiones de dominio.
 */
public abstract class ScreenFragment extends Fragment {
	protected AppState state;
	protected AppController app;
	protected LinearLayout rows;
	private final int layout;

	protected ScreenFragment(int layout) {
		this.layout = layout;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
		return inflater.inflate(layout, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle saved) {
		super.onViewCreated(view, saved);
		render();
	}

	public final void render() {
		if (getView() == null)
			return;
		state = ((MainActivity) requireActivity()).state();
		app = state.app;
		if (app == null)
			return;
		rows = getView().findViewById(R.id.rows);
		beforeContentReset();
		rows.removeAllViews();
		if (app.owner() != null && !app.initialDataReady && app.screen != AppController.Screen.ACCOUNT) {
			heading("", "Restaurando tus datos", "Estamos comprobando los datos guardados de esta cuenta.");
			LinearLayout actions = card("Restauración inicial",
				"Espera a que termine antes de editar. Si no hay conexión, puedes reintentar desde Cuenta.");
			action(actions, "Ir a Cuenta", () -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		content();
	}

	protected abstract void content();

	/** Permite conservar edición transitoria antes de retirar los campos de la vista. */
	protected void beforeContentReset() {
	}

	protected void heading(String eyebrow, String title, String subtitle) {
		// Diseño limpio: sin antetítulo en mayúsculas; el título basta para orientarse.
		TextView over = getView().findViewById(R.id.eyebrow);
		over.setText(eyebrow);
		over.setVisibility(View.GONE);
		ViewCompat.setAccessibilityHeading(getView().findViewById(R.id.screen_title), true);
		((TextView) getView().findViewById(R.id.screen_title)).setText(title);
		TextView description = getView().findViewById(R.id.screen_subtitle);
		description.setText(subtitle);
		description.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
		// Sin subtítulo, el título conserva la misma separación respecto al primer bloque.
		TextView titleView = getView().findViewById(R.id.screen_title);
		titleView.setPadding(titleView.getPaddingLeft(), titleView.getPaddingTop(), titleView.getPaddingRight(),
			subtitle.isEmpty() ? dp(16) : 0);
	}

	protected LinearLayout card(String title, String body) {
		View view = getLayoutInflater().inflate(R.layout.row_card, rows, false);
		ViewCompat.setAccessibilityHeading(view.findViewById(R.id.card_title), true);
		((TextView) view.findViewById(R.id.card_title)).setText(title);
		TextView description = view.findViewById(R.id.card_body);
		description.setText(body);
		description.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
		rows.addView(view);
		return view.findViewById(R.id.card_actions);
	}

	protected MaterialButton action(LinearLayout target, String label, boolean enabled, Runnable event) {
		MaterialButton b = (MaterialButton) getLayoutInflater().inflate(R.layout.row_action, target, false);
		b.setText(label);
		b.setEnabled(enabled);
		b.setOnClickListener(v -> event.run());
		target.addView(b);
		return b;
	}

	protected void action(LinearLayout target, String label, Runnable event) {
		action(target, label, true, event);
	}

	/**
	 * Añade una vista propia (gráfica, calendario, texto) a ancho completo con separación superior.
	 */
	protected <V extends View> V add(LinearLayout target, V view, int topDp) {
		LinearLayout.LayoutParams params =
			new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.topMargin = dp(topDp);
		target.addView(view, params);
		return view;
	}

	/**
	 * Fila horizontal de columnas iguales: anillos de macros, cifras resumen o botones compactos.
	 */
	protected LinearLayout columns(LinearLayout target, int topDp) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setBaselineAligned(false);
		return add(target, row, topDp);
	}

	protected <V extends View> V column(LinearLayout row, V view, int gapDp) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		if (row.getChildCount() > 0)
			params.setMarginStart(dp(gapDp));
		row.addView(view, params);
		return view;
	}

	/**
	 * Botón compacto para filas horizontales: mismo estilo que las acciones, sin margen superior propio.
	 */
	protected MaterialButton compact(LinearLayout row, String label, boolean enabled, Runnable event) {
		MaterialButton b = (MaterialButton) getLayoutInflater().inflate(R.layout.row_action, row, false);
		b.setText(label);
		b.setEnabled(enabled);
		b.setMinHeight(dp(48));
		b.setTextSize(14);
		b.setOnClickListener(v -> event.run());
		return column(row, b, 12);
	}

	/**
	 * Cifra destacada con su leyenda debajo, para resúmenes en columnas.
	 */
	protected View stat(LinearLayout row, String value, String caption) {
		LinearLayout box = new LinearLayout(requireContext());
		box.setOrientation(LinearLayout.VERTICAL);
		TextView number = new TextView(requireContext());
		number.setTextAppearance(R.style.EnerGym_StatValue);
		number.setText(value);
		TextView label = new TextView(requireContext());
		label.setTextAppearance(R.style.EnerGym_FieldCaption);
		label.setText(caption);
		box.addView(number);
		box.addView(label);
		box.setContentDescription(value + " " + caption);
		return column(row, box, 12);
	}

	protected TextView text(LinearLayout target, String value, int style, int topDp) {
		TextView view = new TextView(requireContext());
		view.setTextAppearance(style);
		view.setText(value);
		return add(target, view, topDp);
	}

	protected int dp(float value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	/**
	 * Acción principal: dorado relleno; deshabilitada vuelve al gris de las secundarias.
	 */
	protected void primary(MaterialButton button) {
		int[][] states = {{-android.R.attr.state_enabled}, {}};
		android.content.Context c = requireContext();
		button.setBackgroundTintList(new android.content.res.ColorStateList(states, new int[]{
			androidx.core.content.ContextCompat.getColor(c, R.color.glass_button),
			androidx.core.content.ContextCompat.getColor(c, R.color.gold)}));
		button.setTextColor(new android.content.res.ColorStateList(states, new int[]{
			androidx.core.content.ContextCompat.getColor(c, R.color.text_tertiary),
			androidx.core.content.ContextCompat.getColor(c, R.color.surface)}));
	}

	protected void textField(LinearLayout target, String key, String label, String value, boolean number,
		boolean integer, Consumer<String> event) {
		textField(target, key, label, value, number, integer,
			FormFieldGuidance.placeholder(key, label, number, integer), "", event);
	}

	protected void textField(LinearLayout target, String key, String label, String value, boolean number,
		boolean integer, String helper, Consumer<String> event) {
		textField(target, key, label, value, number, integer,
			FormFieldGuidance.placeholder(key, label, number, integer), helper, event);
	}

	protected void textField(LinearLayout target, String key, String label, String value, boolean number,
		boolean integer, String placeholder, String helper, Consumer<String> event) {
		View row = getLayoutInflater().inflate(R.layout.row_field, target, false);
		TextInputLayout field = row.findViewById(R.id.field_label);
		TextView caption = row.findViewById(R.id.field_caption);
		caption.setText(label);
		EditText edit = field.findViewById(R.id.field_input);
		edit.setId(View.generateViewId());
		edit.setTag(key);
		caption.setLabelFor(edit.getId());
		if (number)
			edit.setInputType(
				android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED |
					(integer ? 0 : android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
		// El ejemplo es un hint del EditText: la etiqueta externa no desaparece ni pasa al valor editable.
		edit.setHint(placeholder == null || placeholder.isEmpty() ?
			FormFieldGuidance.placeholder(key, label, number, integer) : placeholder);
		if (helper != null && !helper.isEmpty()) {
			field.setHelperText(helper);
			TextView help = field.findViewById(com.google.android.material.R.id.textinput_helper_text);
			if (help != null) {
				// La ayuda de Material conserva su relación accesible y admite fuentes grandes sin truncar.
				help.setSingleLine(false);
				help.setMaxLines(Integer.MAX_VALUE);
				help.setEllipsize(null);
			}
		}
		edit.setText(value);
		edit.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				event.accept(s.toString());
			}

			public void afterTextChanged(Editable e) {
			}
		});
		target.addView(row);
	}

	/** Convierte un campo ya creado en una duración «h:mm» con teclado numérico y sufijo « h». */
	protected void timeInput(LinearLayout target, String key) {
		View view = target.findViewWithTag(key);
		if (!(view instanceof EditText))
			return;
		EditText edit = (EditText) view;
		edit.setInputType(android.text.InputType.TYPE_CLASS_DATETIME |
			android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
		for (android.view.ViewParent p = edit.getParent(); p != null; p = p.getParent())
			if (p instanceof TextInputLayout) {
				((TextInputLayout) p).setSuffixText("h");
				break;
			}
	}

	protected void choice(LinearLayout target, String key, String label, List<String> values, List<String> labels,
		String value, Consumer<String> event) {
		choice(target, key, label, values, labels, value, "", event);
	}

	protected void choice(LinearLayout target, String key, String label, List<String> values, List<String> labels,
		String value, String helper, Consumer<String> event) {
		View field = getLayoutInflater().inflate(R.layout.row_choice, target, false);
		TextView text = field.findViewById(R.id.choice_label);
		text.setText(label);
		Spinner spinner = field.findViewById(R.id.choice_input);
		spinner.setId(View.generateViewId());
		spinner.setTag(key);
		text.setLabelFor(spinner.getId());
		spinner.setContentDescription(label);
		spinner.setPrompt(label);
		TextView help = field.findViewById(R.id.choice_helper);
		help.setText(helper);
		help.setVisibility(helper == null || helper.isEmpty() ? View.GONE : View.VISIBLE);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.row_choice_text, labels);
		adapter.setDropDownViewResource(R.layout.row_choice_option);
		spinner.setAdapter(adapter);
		int index = Math.max(0, values.indexOf(value));
		spinner.setSelection(index, false);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			private int last = index;

			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (position != last) {
					last = position;
					event.accept(values.get(position));
				}
			}

			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		target.addView(field);
	}

	protected void check(LinearLayout target, AppController.InputField field) {
		check(target, field, "");
	}

	protected void check(LinearLayout target, AppController.InputField field, String helper) {
		MaterialCheckBox box = (MaterialCheckBox) getLayoutInflater().inflate(R.layout.row_check, target, false);
		box.setId(View.generateViewId());
		box.setTag(field.key);
		box.setText(field.label);
		box.setChecked(Boolean.parseBoolean(field.value));
		box.setOnCheckedChangeListener((button, value) -> app.profileInput(field.key, Boolean.toString(value)));
		target.addView(box);
		if (helper != null && !helper.isEmpty()) {
			LinearLayout.LayoutParams boxParams = (LinearLayout.LayoutParams) box.getLayoutParams();
			boxParams.bottomMargin = dp(4);
			box.setLayoutParams(boxParams);
			TextView help = text(target, helper, R.style.EnerGym_FieldCaption, 0);
			help.setPadding(dp(16), 0, 0, 0);
			LinearLayout.LayoutParams helpParams = (LinearLayout.LayoutParams) help.getLayoutParams();
			helpParams.bottomMargin = dp(16);
			help.setLayoutParams(helpParams);
		}
	}
}
