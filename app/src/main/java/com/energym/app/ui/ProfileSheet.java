package com.energym.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.energym.app.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * Menú de la foto de perfil como panel inferior: cabecera con foto y nombre, opciones con icono en una sola
 * columna y, aparte, las acciones de la foto. Sobrio: superficie de tarjeta, texto blanco y dorado solo en la marca.
 */
public final class ProfileSheet {
	public interface Action {
		void run();
	}

	private final Context context;
	private final BottomSheetDialog dialog;
	private final LinearLayout column;

	public ProfileSheet(Context context, Bitmap photo, String name, String subtitle) {
		this.context = context;
		dialog = new BottomSheetDialog(context);
		column = new LinearLayout(context);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setBackgroundResource(R.drawable.sheet_background);
		column.setPadding(dp(20), dp(10), dp(20), dp(24));
		handle();
		header(photo, name, subtitle);
		dialog.setContentView(column);
		dialog.setOnShowListener(d -> {
			View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
			if (sheet != null)
				sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
		});
	}

	private void handle() {
		View bar = new View(context);
		android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
		shape.setCornerRadius(dp(2));
		shape.setColor(ContextCompat.getColor(context, R.color.text_tertiary));
		bar.setBackground(shape);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(4));
		params.gravity = Gravity.CENTER_HORIZONTAL;
		params.bottomMargin = dp(18);
		column.addView(bar, params);
	}

	private void header(Bitmap photo, String name, String subtitle) {
		LinearLayout row = new LinearLayout(context);
		row.setGravity(Gravity.CENTER_VERTICAL);
		ShapeableImageView avatar = new ShapeableImageView(context);
		avatar.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(
			new com.google.android.material.shape.RelativeCornerSize(0.5f)).build());
		avatar.setBackgroundColor(ContextCompat.getColor(context, R.color.glass_button));
		avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
		if (photo != null)
			avatar.setImageBitmap(photo);
		else {
			avatar.setImageResource(R.drawable.ic_account);
			avatar.setPadding(dp(14), dp(14), dp(14), dp(14));
		}
		avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		row.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
		LinearLayout texts = new LinearLayout(context);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.setPadding(dp(14), 0, 0, 0);
		texts.addView(label(name, 18, R.color.white, true));
		if (subtitle != null && !subtitle.isEmpty())
			texts.addView(label(subtitle, 14, R.color.text_secondary, false));
		row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT);
		params.bottomMargin = dp(12);
		column.addView(row, params);
	}

	/**
	 * Fila de opción: icono, texto y una flecha discreta. Al tocarla se cierra el panel y se ejecuta la acción.
	 */
	public ProfileSheet item(int icon, String text, boolean subtle, Action action) {
		LinearLayout row = new LinearLayout(context);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setMinimumHeight(dp(56));
		row.setPadding(dp(4), 0, dp(4), 0);
		android.util.TypedValue ripple = new android.util.TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
		row.setBackgroundResource(ripple.resourceId);
		row.setClickable(true);
		row.setFocusable(true);
		row.setContentDescription(text);
		ImageView image = new ImageView(context);
		image.setImageResource(icon);
		image.setImageTintList(android.content.res.ColorStateList.valueOf(
			ContextCompat.getColor(context, subtle ? R.color.text_secondary : R.color.white)));
		image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		row.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
		TextView label = label(text, 16, subtle ? R.color.text_secondary : R.color.white, false);
		label.setPadding(dp(16), 0, 0, 0);
		row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		TextView chevron = label("›", 22, R.color.text_tertiary, false);
		chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		row.addView(chevron);
		row.setOnClickListener(v -> {
			dialog.dismiss();
			action.run();
		});
		column.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT));
		return this;
	}

	public ProfileSheet divider() {
		View line = new View(context);
		line.setBackgroundColor(ContextCompat.getColor(context, R.color.divider));
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
		params.topMargin = dp(8);
		params.bottomMargin = dp(8);
		column.addView(line, params);
		return this;
	}

	public void show() {
		dialog.show();
	}

	public boolean isShowing() {
		return dialog.isShowing();
	}

	public void dismiss() {
		dialog.dismiss();
	}

	private TextView label(String text, int sp, int color, boolean bold) {
		TextView view = new TextView(context);
		view.setText(text);
		view.setTextSize(sp);
		view.setTextColor(ContextCompat.getColor(context, color));
		view.setMaxLines(2);
		view.setEllipsize(android.text.TextUtils.TruncateAt.END);
		if (bold)
			view.setTypeface(Typeface.DEFAULT_BOLD);
		return view;
	}

	private int dp(int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
