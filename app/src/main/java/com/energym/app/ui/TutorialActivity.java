package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.energym.app.R;
import com.google.android.material.button.MaterialButton;

/**
 * Tutorial obligatorio del primer arranque: un paso por sección de la app con una captura real de la pantalla y un
 * ejemplo 3D animado (fotogramas del propio visor). Atrás retrocede de paso; no se puede saltar. Se puede volver a
 * ver desde Cuenta.
 */
public final class TutorialActivity extends AppCompatActivity {
	private static final String PREFS = "tutorial", DONE = "done_v1", SETUP = "setup_pending";

	/** Pasos: título, texto, imagen (0 = logo, -1 = animación 3D). */
	private static final int[][] STEPS = {
		{R.string.tut_welcome, R.string.tut_welcome_body, 0},
		{R.string.tut_home, R.string.tut_home_body, R.drawable.tutorial_inicio},
		{R.string.tut_training, R.string.tut_training_body, R.drawable.tutorial_entreno},
		{R.string.tut_log, R.string.tut_log_body, R.drawable.tutorial_registro},
		{R.string.tut_3d, R.string.tut_3d_body, -1},
		{R.string.nutrition, R.string.tut_nutrition_body, R.drawable.tutorial_nutricion},
		{R.string.history, R.string.tut_progress_body, R.drawable.tutorial_progreso},
		{R.string.tut_trainer, R.string.tut_trainer_body, R.drawable.tutorial_asistente},
		{R.string.tut_account, R.string.tut_account_body, R.drawable.tutorial_cuenta},
	};

	private int step;
	private LinearLayout body;
	private TextView counter;
	private View progress;
	private MaterialButton back, next;

	public static boolean done(Context c) {
		return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(DONE, false);
	}

	/** Tras el tutorial, la app guía a rellenar el perfil y los datos físicos hasta que ambos estén guardados. */
	public static boolean setupPending(Context c) {
		return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SETUP, false);
	}

	public static void setupFinished(Context c) {
		c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SETUP, false).apply();
	}

	/** Los tests instrumentados abren la app directamente: allí el tutorial no se interpone. */
	static boolean underTest() {
		try {
			Class.forName("androidx.test.platform.app.InstrumentationRegistry");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Muestra el tutorial si aún no se ha completado en este móvil. */
	public static void showIfNeeded(android.app.Activity from) {
		if (!done(from) && !underTest())
			from.startActivity(new Intent(from, TutorialActivity.class));
	}

	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		androidx.activity.EdgeToEdge.enable(this);
		// Fondo oscuro: iconos claros en las barras del sistema.
		androidx.core.view.WindowInsetsControllerCompat systemBars =
			androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		systemBars.setAppearanceLightStatusBars(false);
		systemBars.setAppearanceLightNavigationBars(false);
		step = saved == null ? 0 : saved.getInt("step");
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.surface));
		root.setPadding(dp(20), dp(16), dp(20), dp(20));
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
			v.setPadding(dp(20), bars.top + dp(16), dp(20), bars.bottom + dp(20));
			return insets;
		});

		counter = new TextView(this);
		counter.setTextAppearance(R.style.EnerGym_FieldCaption);
		counter.setTextColor(color(R.color.gold));
		root.addView(counter);
		LinearLayout track = new LinearLayout(this);
		track.setBackground(round(R.color.surface_card, 3));
		progress = new View(this);
		progress.setBackground(round(R.color.gold, 3));
		track.addView(progress, new LinearLayout.LayoutParams(0, dp(6)));
		LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
		trackParams.topMargin = dp(8);
		root.addView(track, trackParams);

		ScrollView scroll = new ScrollView(this);
		body = new LinearLayout(this);
		body.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(body);
		LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
		scrollParams.topMargin = dp(16);
		root.addView(scroll, scrollParams);

		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		back = button(Txt.t(R.string.tut_back), false);
		next = button(Txt.t(R.string.tut_next), true);
		LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(56), 1);
		LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(56), 1);
		right.setMarginStart(dp(16));
		buttons.addView(back, left);
		buttons.addView(next, right);
		LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		buttonsParams.topMargin = dp(16);
		root.addView(buttons, buttonsParams);
		back.setOnClickListener(v -> go(step - 1));
		next.setOnClickListener(v -> {
			if (step == STEPS.length - 1)
				finishTutorial();
			else
				go(step + 1);
		});
		setContentView(root);

		// Deslizar también cambia de paso.
		GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
				if (a == null || Math.abs(vx) < Math.abs(vy) * 1.5 || Math.abs(b.getX() - a.getX()) < dp(60))
					return false;
				if (vx < 0 && step < STEPS.length - 1)
					go(step + 1);
				else if (vx > 0 && step > 0)
					go(step - 1);
				return true;
			}
		});
		scroll.setOnTouchListener((v, e) -> {
			swipe.onTouchEvent(e);
			return false;
		});

		// Obligatorio: atrás retrocede un paso; en el primero no sale.
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (step > 0)
					go(step - 1);
				else
					android.widget.Toast.makeText(TutorialActivity.this, Txt.t(R.string.tut_complete),
						android.widget.Toast.LENGTH_SHORT).show();
			}
		});
		show();
	}

	@Override
	protected void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		out.putInt("step", step);
	}

	private void go(int target) {
		if (target < 0 || target >= STEPS.length)
			return;
		step = target;
		show();
	}

	private void show() {
		int[] s = STEPS[step];
		counter.setText(Txt.t(R.string.tut_counter, step + 1, STEPS.length));
		progress.post(() -> {
			View track = (View) progress.getParent();
			progress.getLayoutParams().width = track.getWidth() * (step + 1) / STEPS.length;
			progress.requestLayout();
		});
		body.removeAllViews();
		TextView title = new TextView(this);
		title.setTextAppearance(R.style.EnerGym_CardTitle);
		title.setTextSize(26);
		title.setText(Txt.t(s[0]));
		title.setTextColor(color(R.color.white));
		androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
		body.addView(title);
		int image = s[2];
		body.addView(image == 0 ? logo() : image == -1 ? squat() : screenshot(image), imageParams(image));
		TextView text = new TextView(this);
		text.setTextAppearance(R.style.EnerGym_Body);
		text.setTextSize(16);
		text.setText(Txt.t(s[1]));
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		textParams.topMargin = dp(20);
		body.addView(text, textParams);
		back.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
		next.setText(Txt.t(step == STEPS.length - 1 ? R.string.home_start : R.string.tut_next));
		next.setContentDescription(Txt.t(step == STEPS.length - 1 ? R.string.tut_finish : R.string.tut_next_step));
		body.announceForAccessibility(Txt.t(s[0]));
	}

	private LinearLayout.LayoutParams imageParams(int image) {
		// Las capturas se ajustan a su ancho (el marco no deja bandas); el 3D ocupa todo el ancho.
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(image == 0 ? dp(120) : image == -1 ?
			LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT,
			image == 0 ? dp(120) : LinearLayout.LayoutParams.WRAP_CONTENT);
		p.topMargin = dp(24);
		p.gravity = Gravity.CENTER_HORIZONTAL;
		return p;
	}

	private View logo() {
		ImageView v = new ImageView(this);
		v.setImageResource(R.drawable.ic_brand);
		v.setContentDescription("EnerGym");
		return v;
	}

	/** Captura real de la pantalla, con marco como un móvil y una altura cómoda para leer el texto debajo. */
	private View screenshot(int res) {
		ImageView v = new ImageView(this);
		v.setImageResource(res);
		v.setAdjustViewBounds(true);
		v.setMaxHeight(dp(380));
		v.setScaleType(ImageView.ScaleType.FIT_CENTER);
		v.setBackground(frame(false));
		v.setForeground(frame(true));
		v.setPadding(dp(4), dp(4), dp(4), dp(4));
		v.setClipToOutline(true);
		v.setContentDescription(Txt.t(R.string.tut_screenshot, Txt.t(STEPS[step][0])));
		return v;
	}

	/** Ejemplo 3D: fotogramas del visor de la app en bucle. */
	private View squat() {
		ImageView v = new ImageView(this);
		AnimationDrawable anim = new AnimationDrawable();
		for (int i = 1; i <= 24; i++) {
			int id = getResources().getIdentifier(String.format(java.util.Locale.ROOT, "tutorial_squat_%02d", i),
				"drawable", getPackageName());
			if (id != 0)
				anim.addFrame(ContextCompat.getDrawable(this, id), 90);
		}
		anim.setOneShot(false);
		v.setImageDrawable(anim);
		v.setAdjustViewBounds(true);
		v.setBackground(frame(false));
		v.setForeground(frame(true));
		v.setPadding(dp(4), dp(4), dp(4), dp(4));
		v.setClipToOutline(true);
		v.setContentDescription(Txt.t(R.string.tut_squat_animation));
		v.post(anim::start);
		return v;
	}

	/** Fondo redondeado (recorta la imagen) y, por encima, solo el borde dorado para que nada lo tape. */
	private GradientDrawable frame(boolean border) {
		GradientDrawable d = new GradientDrawable();
		d.setCornerRadius(dp(20));
		if (border) {
			d.setColor(android.graphics.Color.TRANSPARENT);
			d.setStroke(dp(2), color(R.color.gold));
		} else
			d.setColor(color(R.color.surface_card));
		return d;
	}

	private GradientDrawable round(int colorRes, int radiusDp) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color(colorRes));
		d.setCornerRadius(dp(radiusDp));
		return d;
	}

	private MaterialButton button(String label, boolean primary) {
		MaterialButton b = (MaterialButton) getLayoutInflater().inflate(R.layout.row_action, null, false);
		b.setText(label);
		b.setInsetTop(0);
		b.setInsetBottom(0);
		if (primary) {
			b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(R.color.gold)));
			b.setTextColor(color(R.color.surface));
		}
		return b;
	}

	private void finishTutorial() {
		getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(DONE, true).putBoolean(SETUP, true).apply();
		finish();
	}

	private int color(int res) {
		return ContextCompat.getColor(this, res);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
