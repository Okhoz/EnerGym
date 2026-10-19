package com.energym.app.ui.charts;

import com.energym.app.R;
import com.energym.app.i18n.Txt;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Calendario mensual (lunes primero). Días entrenados en dorado, días con alimentación anotada con punto
 * blanco, hoy con contorno y el día seleccionado relleno tenue. Pulsar un día notifica la selección.
 */
public final class MonthCalendarView extends View {
	private static final String[] WEEKDAYS = {"L", "M", "X", "J", "V", "S", "D"};
	private final ChartPalette palette;
	private final Paint header, number, numberOnGold, trained, todayRing, selectedFill, dot, muted;
	private YearMonth month = YearMonth.now();
	private LocalDate today = LocalDate.now(), selected;
	private Set<LocalDate> training = Collections.emptySet(), nutrition = Collections.emptySet();
	private Consumer<LocalDate> listener;
	/** Colores de los grupos musculares entrenados cada día (puntos bajo el número, como en FitNotes). */
	private Map<LocalDate, int[]> groups = Collections.emptyMap();
	private final Paint groupDot = new Paint(Paint.ANTI_ALIAS_FLAG);

	public MonthCalendarView(Context context) {
		this(context, null);
	}

	public MonthCalendarView(Context context, AttributeSet attrs) {
		super(context, attrs);
		palette = new ChartPalette(context);
		header = palette.text(palette.secondary, 12, true);
		number = palette.text(palette.white, 14, false);
		numberOnGold = palette.text(Color.BLACK, 14, true);
		muted = palette.text(palette.track, 14, false);
		trained = palette.fill(palette.gold);
		todayRing = palette.stroke(palette.gold, 1.5f);
		selectedFill = palette.fill(palette.track);
		dot = palette.fill(palette.white);
		setClickable(true);
		setFocusable(true);
	}

	public MonthCalendarView set(YearMonth month, LocalDate today, LocalDate selected, Set<LocalDate> training,
		Set<LocalDate> nutrition, Consumer<LocalDate> listener) {
		this.month = month;
		this.today = today;
		this.selected = selected;
		this.training = training;
		this.nutrition = nutrition;
		this.listener = listener;
		int count = 0;
		for (LocalDate d : training)
			if (YearMonth.from(d).equals(month))
				count++;
		setContentDescription(Txt.n(R.plurals.cal_days_trained, count,
			month.getMonthValue() + "/" + month.getYear(), count));
		requestLayout();
		invalidate();
		return this;
	}

	public MonthCalendarView groups(Map<LocalDate, int[]> colors) {
		groups = colors == null ? Collections.emptyMap() : colors;
		requestLayout();
		invalidate();
		return this;
	}

	/** Alto de fila: la celda más un hueco para los puntos de colores si los hay. */
	private float rowHeight(float cell) {
		return cell + (groups.isEmpty() ? 0 : palette.dp(10));
	}

	private int offset() {
		return month.atDay(1).getDayOfWeek().getValue() - 1;
	}

	private int rows() {
		return (offset() + month.lengthOfMonth() + 6) / 7;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int width = MeasureSpec.getSize(widthSpec);
		float cell = Math.min(width / 7f, palette.dp(52));
		setMeasuredDimension(width, (int) (palette.dp(28) + rowHeight(cell) * rows()));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float cellW = getWidth() / 7f;
		float cell = Math.min(cellW, palette.dp(52));
		float headerH = palette.dp(28);
		for (int i = 0; i < 7; i++)
			canvas.drawText(WEEKDAYS[i], cellW * i + cellW / 2, palette.dp(16), header);
		float radius = Math.min(cellW, cell) * 0.38f;
		for (int day = 1; day <= month.lengthOfMonth(); day++) {
			int index = offset() + day - 1;
			float cx = cellW * (index % 7) + cellW / 2;
			float cy = headerH + rowHeight(cell) * (index / 7) + cell / 2;
			LocalDate date = month.atDay(day);
			if (date.equals(selected))
				canvas.drawCircle(cx, cy, radius + palette.dp(3), selectedFill);
			boolean gold = training.contains(date);
			if (gold)
				canvas.drawCircle(cx, cy, radius, trained);
			if (date.equals(today))
				canvas.drawCircle(cx, cy, radius + palette.dp(1), todayRing);
			Paint text = gold ? numberOnGold : date.isAfter(today) ? muted : number;
			canvas.drawText(Integer.toString(day), cx, cy - (text.descent() + text.ascent()) / 2, text);
			if (nutrition.contains(date))
				canvas.drawCircle(cx, cy + radius - palette.dp(2), palette.dp(2.5f), gold ? numberOnGold : dot);
			int[] colors = groups.get(date);
			if (colors != null && colors.length > 0) {
				int shown = Math.min(5, colors.length);
				float gap = palette.dp(6), start = cx - gap * (shown - 1) / 2f;
				for (int i = 0; i < shown; i++) {
					groupDot.setColor(colors[i]);
					canvas.drawCircle(start + gap * i, cy + radius + palette.dp(6), palette.dp(2.5f), groupDot);
				}
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN)
			return true;
		if (event.getAction() != MotionEvent.ACTION_UP || listener == null)
			return super.onTouchEvent(event);
		float cellW = getWidth() / 7f;
		float cell = Math.min(cellW, palette.dp(52));
		float y = event.getY() - palette.dp(28);
		if (y < 0)
			return true;
		int index = (int) (y / rowHeight(cell)) * 7 + (int) (event.getX() / cellW);
		int day = index - offset() + 1;
		if (day >= 1 && day <= month.lengthOfMonth() && !month.atDay(day).isAfter(today)) {
			performClick();
			listener.accept(month.atDay(day));
		}
		return true;
	}

	@Override
	public boolean performClick() {
		return super.performClick();
	}
}
