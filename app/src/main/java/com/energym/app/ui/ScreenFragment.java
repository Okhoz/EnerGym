package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
			heading("", Txt.t(R.string.sf_restoring), Txt.t(R.string.sf_restoring_body));
			LinearLayout actions = card(Txt.t(R.string.sf_initial_restore), Txt.t(R.string.sf_initial_restore_body));
			action(actions, Txt.t(R.string.man_go_account), () -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		content();
		spaceButtons(rows);
	}

	/**
	 * Misma separación para todos los botones de la app: 16 dp entre botones apilados y entre el texto de una
	 * tarjeta y su primer botón, el mismo hueco entre botones en fila y, tras una tarjeta, solo el margen de la
	 * tarjeta. Se aplica al final de cada pintado para que ninguna pantalla lo ajuste a mano.
	 */
	private void spaceButtons(ViewGroup group) {
		int gap = getResources().getDimensionPixelSize(R.dimen.button_gap);
		boolean vertical = group instanceof LinearLayout &&
			((LinearLayout) group).getOrientation() == LinearLayout.VERTICAL;
		// Tarjeta sin botones: su zona de acciones vacía no añade hueco abajo.
		if (group.getId() == R.id.card_actions)
			group.setVisibility(group.getChildCount() == 0 ? View.GONE : View.VISIBLE);
		for (int i = 0; i < group.getChildCount(); i++) {
			View child = group.getChildAt(i);
			if (vertical && isButtonish(child) && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
				LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
				View before = previousVisible(group, i);
				boolean firstInCard = before == null && group.getId() == R.id.card_actions;
				boolean afterCard = before != null && before.findViewById(R.id.card_actions) != null;
				// Tras un campo de texto basta su propio margen inferior.
				boolean afterField = before != null && before.findViewById(R.id.field_label) != null;
				View next = nextVisible(group, i);
				lp.topMargin = firstInCard || afterCard || afterField ? 0 : gap;
				// Antes de una tarjeta, el mismo hueco que separa tarjetas.
				lp.bottomMargin = next != null && next.findViewById(R.id.card_actions) != null ?
					getResources().getDimensionPixelSize(R.dimen.section_gap) : 0;
				child.setLayoutParams(lp);
			}
			if (child instanceof LinearLayout && isButtonRow((LinearLayout) child)) {
				LinearLayout row = (LinearLayout) child;
				for (int j = 1; j < row.getChildCount(); j++) {
					LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getChildAt(j).getLayoutParams();
					lp.setMarginStart(gap);
					row.getChildAt(j).setLayoutParams(lp);
				}
				continue;
			}
			if (child instanceof ViewGroup)
				spaceButtons((ViewGroup) child);
		}
	}

	private static View nextVisible(ViewGroup group, int index) {
		for (int i = index + 1; i < group.getChildCount(); i++)
			if (group.getChildAt(i).getVisibility() != View.GONE)
				return group.getChildAt(i);
		return null;
	}

	private static View previousVisible(ViewGroup group, int index) {
		for (int i = index - 1; i >= 0; i--)
			if (group.getChildAt(i).getVisibility() != View.GONE)
				return group.getChildAt(i);
		return null;
	}

	private static boolean isButtonish(View v) {
		return v instanceof MaterialButton || (v instanceof LinearLayout && isButtonRow((LinearLayout) v));
	}

	/** Fila horizontal formada solo por botones (no los − / + con su campo, que llevan su propio diseño). */
	private static boolean isButtonRow(LinearLayout row) {
		if (row.getOrientation() != LinearLayout.HORIZONTAL || row.getChildCount() < 2)
			return false;
		for (int i = 0; i < row.getChildCount(); i++)
			if (!(row.getChildAt(i) instanceof MaterialButton))
				return false;
		return true;
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
		b.setMinHeight(getResources().getDimensionPixelSize(R.dimen.primary_touch_target));
		b.setTextSize(15);
		// En una fila, cada botón cabe en una línea: el texto se reduce un poco antes de partirse.
		b.setMaxLines(1);
		b.setPadding(dp(16), b.getPaddingTop(), dp(16), b.getPaddingBottom());
		// Las flechas de navegación (‹ ›) van grandes y fijas; el resto se ajusta para caber en una línea.
		if (label.equals("‹") || label.equals("›")) {
			// Misma altura que los botones vecinos; la flecha de la fuente cae baja, se sube un poco para centrarla.
			b.setTextSize(24);
			b.setIncludeFontPadding(false);
			b.setPadding(0, 0, 0, dp(4));
			b.setMinHeight(0);
			b.setMinimumHeight(0);
			b.setGravity(android.view.Gravity.CENTER);
		} else
			androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 11, 15, 1,
				android.util.TypedValue.COMPLEX_UNIT_SP);
		b.setOnClickListener(v -> event.run());
		MaterialButton added = column(row, b, 16);
		if (label.equals("‹") || label.equals("›"))
			added.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.primary_touch_target);
		return added;
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
		return column(row, box, 16);
	}

	protected TextView text(LinearLayout target, String value, int style, int topDp) {
		TextView view = new TextView(requireContext());
		view.setTextAppearance(style);
		view.setText(value);
		return add(target, view, topDp);
	}

	/** Última opción elegida de cada control segmentado: la burbuja sale de ahí al volver a pintar. */
	private static final Map<String, Integer> SEGMENTS = new HashMap<>();

	/**
	 * Control segmentado con burbuja líquida: un contenedor redondeado y la opción elegida en dorado. La burbuja
	 * se desliza desde la opción anterior; {@code select} recibe el índice tocado.
	 */
	protected LinearLayout segmented(String key, String[] labels, String[] tags, int selected, IntConsumer select) {
		android.widget.FrameLayout frame = new android.widget.FrameLayout(requireContext());
		frame.setPadding(dp(4), dp(4), dp(4), dp(4));
		frame.setBackground(rounded(R.color.surface_card, 24, 0, 0));
		LiquidIndicator bubble = new LiquidIndicator(requireContext());
		bubble.setColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold));
		frame.addView(bubble, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT));
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setBaselineAligned(false);
		frame.addView(row, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT));
		for (int i = 0; i < labels.length; i++) {
			int index = i;
			boolean chosen = i == selected;
			MaterialButton b = new MaterialButton(requireContext(), null,
				com.google.android.material.R.attr.borderlessButtonStyle);
			b.setText(labels[i]);
			if (tags != null) b.setTag(tags[i]);
			b.setAllCaps(false);
			b.setTextSize(14);
			b.setMinWidth(0);
			b.setMinHeight(dp(44));
			b.setMaxLines(1);
			b.setGravity(android.view.Gravity.CENTER);
			androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 10, 14, 1,
				android.util.TypedValue.COMPLEX_UNIT_SP);
			b.setPadding(dp(4), 0, dp(4), 0);
			b.setInsetTop(0);
			b.setInsetBottom(0);
			b.setCornerRadius(dp(20));
			b.setCheckable(true);
			b.setChecked(chosen);
			b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
			b.setRippleColor(android.content.res.ColorStateList.valueOf(0x22FFFFFF));
			b.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
				chosen ? R.color.surface : R.color.text_secondary));
			b.setContentDescription(labels[i] + (chosen ? ", seleccionado" : ""));
			b.setOnClickListener(v -> {
				if (index != selected) select.accept(index);
			});
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
			if (i > 0) params.setMarginStart(dp(4));
			row.addView(b, params);
		}
		int previous = SEGMENTS.containsKey(key) ? SEGMENTS.get(key) : selected;
		SEGMENTS.put(key, selected);
		row.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
				row.removeOnLayoutChangeListener(this);
				bubble.jumpTo(segment(row, Math.min(previous, labels.length - 1)));
				if (previous != selected)
					bubble.slideTo(segment(row, selected),
						com.energym.app.ui.effects.MotionPreferences.enabled(requireContext()), null);
			}
		});
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT);
		params.bottomMargin = dp(28);
		rows.addView(frame, params);
		return row;
	}

	private static android.graphics.RectF segment(LinearLayout row, int index) {
		View child = row.getChildAt(index);
		return new android.graphics.RectF(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
	}

	/** Rótulo de sección discreto, con aire por encima para separar bloques. */
	protected TextView section(String label, int topDp) {
		TextView view = text(rows, label.toUpperCase(new Locale("es", "ES")), R.style.EnerGym_FieldCaption, topDp);
		view.setLetterSpacing(0.08f);
		view.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_tertiary));
		((LinearLayout.LayoutParams) view.getLayoutParams()).bottomMargin = dp(12);
		ViewCompat.setAccessibilityHeading(view, true);
		return view;
	}

	protected android.graphics.drawable.GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeDp) {
		android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
		d.setColor(androidx.core.content.ContextCompat.getColor(requireContext(), fill));
		d.setCornerRadius(dp(radiusDp));
		if (strokeDp > 0)
			d.setStroke(dp(strokeDp), androidx.core.content.ContextCompat.getColor(requireContext(), stroke));
		return d;
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

	/**
	 * Fila de lista compacta (título, una línea de detalle y un indicador a la derecha, normalmente ›): para menús y
	 * opciones, mucho más ligera que un botón grande con texto debajo.
	 */
	protected View listRow(LinearLayout target, String title, String detail, String end, boolean enabled, Runnable click) {
		View row = getLayoutInflater().inflate(R.layout.row_food, target, false);
		// Icono inicial (emoji o símbolo) en una columna fija: todos los textos empiezan a la misma altura.
		String icon = "", label = title;
		int space = title.indexOf(' ');
		if (space > 0 && !Character.isLetterOrDigit(title.codePointAt(0))) {
			icon = title.substring(0, space);
			label = title.substring(space).trim();
		}
		((TextView) row.findViewById(R.id.food_name)).setText(label);
		if (!icon.isEmpty()) {
			TextView glyph = new TextView(requireContext());
			glyph.setText(icon);
			glyph.setTextSize(18);
			glyph.setGravity(android.view.Gravity.CENTER);
			glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
			((LinearLayout) row).addView(glyph, 0, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		TextView sub = row.findViewById(R.id.food_detail);
		sub.setText(detail);
		sub.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
		TextView arrow = row.findViewById(R.id.food_kcal);
		arrow.setText(end);
		arrow.setTextSize(20);
		arrow.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold));
		row.setEnabled(enabled);
		row.setAlpha(enabled ? 1f : 0.45f);
		row.setContentDescription(title + (detail == null || detail.isEmpty() ? "" : ". " + detail));
		row.setOnClickListener(v -> {
			if (v.isEnabled())
				click.run();
		});
		// El título también responde (y sin anunciarse aparte): pulsarlo equivale a pulsar la fila.
		TextView name = row.findViewById(R.id.food_name);
		name.setOnClickListener(v -> row.performClick());
		name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		target.addView(row);
		return row;
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
