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
		rows.removeAllViews();
		content();
	}

	protected abstract void content();

	protected void heading(String eyebrow, String title, String subtitle) {
		((TextView) getView().findViewById(R.id.eyebrow)).setText(eyebrow);
		ViewCompat.setAccessibilityHeading(getView().findViewById(R.id.screen_title), true);
		((TextView) getView().findViewById(R.id.screen_title)).setText(title);
		TextView description = getView().findViewById(R.id.screen_subtitle);
		description.setText(subtitle);
		description.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
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
		return column(row, b, 8);
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

	protected void primary(MaterialButton button) {
		button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
			androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold)));
		button.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface));
	}

	protected void textField(LinearLayout target, String key, String label, String value, boolean number,
		boolean integer, Consumer<String> event) {
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

	protected void choice(LinearLayout target, String key, String label, List<String> values, List<String> labels,
		String value, Consumer<String> event) {
		View field = getLayoutInflater().inflate(R.layout.row_choice, target, false);
		TextView text = field.findViewById(R.id.choice_label);
		text.setText(label);
		Spinner spinner = field.findViewById(R.id.choice_input);
		spinner.setId(View.generateViewId());
		spinner.setTag(key);
		text.setLabelFor(spinner.getId());
		spinner.setContentDescription(label);
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
		MaterialCheckBox box = (MaterialCheckBox) getLayoutInflater().inflate(R.layout.row_check, target, false);
		box.setId(View.generateViewId());
		box.setTag(field.key);
		box.setText(field.label);
		box.setChecked(Boolean.parseBoolean(field.value));
		box.setOnCheckedChangeListener((button, value) -> app.profileInput(field.key, Boolean.toString(value)));
		target.addView(box);
	}
}
