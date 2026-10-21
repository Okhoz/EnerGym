package com.energym.app.ui;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.energym.app.R;
import com.energym.app.domain.MetricType;
import com.energym.app.domain.SetLog;
import com.energym.app.progress.ShareSummary;

import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * VIEW: dibuja el resumen de un entreno como tarjeta (negro y dorado, como la app) y la comparte con
 * ACTION_SEND. La imagen va a la caché y sale por FileProvider: no hace falta ningún permiso de almacenamiento.
 */
public final class ShareCard {
	private static final int W = 1080, H = 1350, PAD = 80, MAX_LINES = 7;

	private ShareCard() {
	}

	public static void share(Context context, ShareSummary summary) {
		try {
			File dir = new File(context.getCacheDir(), "share");
			if (!dir.isDirectory() && !dir.mkdirs())
				throw new IOException("Sin carpeta de caché");
			File file = new File(dir, "energym_entreno.png");
			Bitmap bitmap = draw(context, summary);
			try (OutputStream out = new FileOutputStream(file)) {
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			} finally {
				bitmap.recycle();
			}
			Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".photos", file);
			Intent send = new Intent(Intent.ACTION_SEND)
				.setType("image/png")
				.putExtra(Intent.EXTRA_STREAM, uri)
				.putExtra(Intent.EXTRA_TEXT, text(context, summary))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			send.setClipData(ClipData.newRawUri("", uri));
			context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)));
		} catch (IOException | RuntimeException e) {
			Toast.makeText(context, R.string.share_error, Toast.LENGTH_SHORT).show();
		}
	}

	/** Texto que acompaña a la imagen para apps que solo muestran texto. */
	static String text(Context c, ShareSummary s) {
		String base = c.getString(R.string.share_text, s.title, s.minutes, volume(s.volumeKg));
		List<String> records = s.records();
		return records.isEmpty() ? base : base + " " + c.getString(R.string.share_text_records, String.join(", ", records));
	}

	static Bitmap draw(Context c, ShareSummary s) {
		int gold = ContextCompat.getColor(c, R.color.gold), white = ContextCompat.getColor(c, R.color.text_primary),
			grey = ContextCompat.getColor(c, R.color.text_secondary), card = ContextCompat.getColor(c, R.color.surface_card);
		Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawColor(ContextCompat.getColor(c, R.color.surface));
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		// Cabecera: marca y fecha.
		p.setColor(gold);
		canvas.drawRect(0, 0, W, 12, p);
		p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		p.setTextSize(44);
		canvas.drawText(c.getString(R.string.share_brand), PAD, 120, p);
		p.setTypeface(Typeface.DEFAULT);
		p.setColor(grey);
		p.setTextSize(36);
		String date = Instant.ofEpochMilli(s.completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
			.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG));
		canvas.drawText(date, W - PAD - p.measureText(date), 120, p);

		p.setColor(white);
		p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		p.setTextSize(68);
		canvas.drawText(fit(p, s.title, W - 2 * PAD), PAD, 240, p);

		// Tres cifras: duración, volumen y ejercicios.
		String[][] stats = {
			{c.getString(R.string.share_minutes, s.minutes), c.getString(R.string.share_label_duration)},
			{volume(s.volumeKg), c.getString(R.string.share_label_volume)},
			{Integer.toString(s.lines.size()), c.getString(R.string.share_label_exercises)}};
		float boxW = (W - 2 * PAD - 2 * 30) / 3f;
		for (int i = 0; i < 3; i++) {
			float x = PAD + i * (boxW + 30);
			p.setColor(card);
			canvas.drawRoundRect(x, 300, x + boxW, 480, 32, 32, p);
			p.setColor(gold);
			p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
			p.setTextSize(54);
			canvas.drawText(fit(p, stats[i][0], boxW - 40), x + 28, 390, p);
			p.setColor(grey);
			p.setTypeface(Typeface.DEFAULT);
			p.setTextSize(32);
			canvas.drawText(fit(p, stats[i][1], boxW - 40), x + 28, 448, p);
		}

		// Récord destacado.
		float y = 560;
		List<String> records = s.records();
		if (!records.isEmpty()) {
			p.setColor(ContextCompat.getColor(c, R.color.surface_selected));
			canvas.drawRoundRect(PAD, y - 20, W - PAD, y + 130, 32, 32, p);
			p.setColor(gold);
			p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
			p.setTextSize(44);
			canvas.drawText(c.getString(R.string.share_record_title), PAD + 36, y + 40, p);
			p.setTypeface(Typeface.DEFAULT);
			p.setColor(white);
			p.setTextSize(34);
			canvas.drawText(fit(p, String.join(", ", records), W - 2 * PAD - 72), PAD + 36, y + 95, p);
			y += 190;
		}

		// Ejercicios.
		int shown = Math.min(s.lines.size(), records.isEmpty() ? MAX_LINES + 1 : MAX_LINES);
		for (int i = 0; i < shown; i++) {
			ShareSummary.Line l = s.lines.get(i);
			p.setColor(white);
			p.setTypeface(Typeface.DEFAULT);
			p.setTextSize(38);
			String name = (l.record ? "🏆 " : "") + l.name;
			String detail = detail(c, l);
			p.setTextSize(34);
			float detailW = p.measureText(detail);
			p.setColor(gold);
			canvas.drawText(detail, W - PAD - detailW, y + 40, p);
			p.setColor(white);
			p.setTextSize(38);
			canvas.drawText(fit(p, name, W - 2 * PAD - detailW - 30), PAD, y + 40, p);
			p.setColor(ContextCompat.getColor(c, R.color.divider));
			canvas.drawRect(PAD, y + 70, W - PAD, y + 72, p);
			y += 90;
		}
		if (s.lines.size() > shown) {
			p.setColor(grey);
			p.setTextSize(34);
			canvas.drawText(c.getResources().getQuantityString(R.plurals.share_more, s.lines.size() - shown, s.lines.size() - shown), PAD, y + 40, p);
		}

		p.setColor(grey);
		p.setTextSize(30);
		String footer = c.getString(R.string.share_footer);
		canvas.drawText(footer, (W - p.measureText(footer)) / 2, H - 60, p);
		return bitmap;
	}

	private static String detail(Context c, ShareSummary.Line l) {
		if (l.type == MetricType.STRENGTH && l.sets > 0)
			return l.topKg > 0 ? c.getString(R.string.share_sets_kg, l.sets, l.topReps, SetLog.display(l.topKg)) :
				c.getString(R.string.share_sets_bodyweight, l.sets, l.topReps);
		if (l.distanceKm > 0)
			return c.getString(R.string.share_km, SetLog.display(Math.round(l.distanceKm * 100) / 100.0));
		return "✓";
	}

	private static String volume(double kg) {
		return kg >= 1000 ? SetLog.display(Math.round(kg / 100) / 10.0) + " t" : Math.round(kg) + " kg";
	}

	/** Recorta con «…» lo que no cabe en el ancho dado. */
	private static String fit(Paint p, String text, float width) {
		if (p.measureText(text) <= width)
			return text;
		String t = text;
		while (t.length() > 1 && p.measureText(t + "…") > width)
			t = t.substring(0, t.length() - 1);
		return t + "…";
	}
}
