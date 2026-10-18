package com.energym.wear;

import android.app.Activity;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

import com.google.android.gms.wearable.*;

import java.util.*;

/**
 * VISTA del reloj: ejercicio y serie en curso, cuenta atrás del descanso (vibra al acabar) y un botón para apuntar
 * otra serie igual que la última, que guarda el móvil.
 */
public final class WorkoutActivity extends Activity implements DataClient.OnDataChangedListener {
	private final Handler ticker = new Handler(Looper.getMainLooper());
	private WatchState state = WatchState.idle();
	private TextView title, detail, rest;
	private Button repeat;
	private boolean vibrated;

	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		int pad = (int) (getResources().getDisplayMetrics().widthPixels * 0.12f);
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setGravity(Gravity.CENTER);
		root.setPadding(pad, pad, pad, pad);
		root.setBackgroundColor(Color.BLACK);
		title = text(root, 18, Color.WHITE);
		detail = text(root, 14, Color.LTGRAY);
		rest = text(root, 26, Color.rgb(0xF5, 0xC5, 0x42));
		repeat = new Button(this);
		repeat.setText(R.string.repeat);
		repeat.setContentDescription(getString(R.string.repeat_description));
		repeat.setOnClickListener(v -> sendRepeat());
		root.addView(repeat);
		setContentView(root);
		render();
	}

	private TextView text(LinearLayout root, int size, int color) {
		TextView t = new TextView(this);
		t.setTextSize(size);
		t.setTextColor(color);
		t.setGravity(Gravity.CENTER);
		root.addView(t);
		return t;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Wearable.getDataClient(this).addListener(this);
		Wearable.getDataClient(this).getDataItems().addOnSuccessListener(items -> {
			for (DataItem item : items)
				if (WatchState.PATH.equals(item.getUri().getPath()))
					update(DataMapItem.fromDataItem(item).getDataMap());
			items.release();
		});
		ticker.post(tick);
	}

	@Override
	protected void onPause() {
		Wearable.getDataClient(this).removeListener(this);
		ticker.removeCallbacks(tick);
		super.onPause();
	}

	@Override
	public void onDataChanged(DataEventBuffer events) {
		for (DataEvent e : events)
			if (e.getType() == DataEvent.TYPE_CHANGED && WatchState.PATH.equals(e.getDataItem().getUri().getPath()))
				update(DataMapItem.fromDataItem(e.getDataItem()).getDataMap());
		events.release();
	}

	private void update(DataMap map) {
		Map<String, String> values = new HashMap<>();
		for (String key : map.keySet())
			values.put(key, map.getString(key, ""));
		WatchState next = WatchState.from(values);
		runOnUiThread(() -> {
			if (next.restEnds != state.restEnds)
				vibrated = false;
			state = next;
			render();
		});
	}

	private final Runnable tick = new Runnable() {
		public void run() {
			render();
			ticker.postDelayed(this, 1000);
		}
	};

	private void render() {
		if (title == null)
			return;
		if (!state.active) {
			title.setText(R.string.app_name);
			detail.setText(R.string.idle);
			rest.setText("");
			repeat.setEnabled(false);
			return;
		}
		title.setText(state.exercise);
		detail.setText(getString(R.string.set_number, state.set) +
			(state.last.isEmpty() ? "" : "\n" + getString(R.string.last_set, state.last)));
		long left = state.restLeft(System.currentTimeMillis());
		if (left > 0)
			rest.setText(getString(R.string.rest, WatchState.clock(left)));
		else if (state.restEnds > 0) {
			rest.setText(R.string.rest_done);
			if (!vibrated) {
				vibrated = true;
				Vibrator v = getSystemService(Vibrator.class);
				if (v != null)
					v.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1));
			}
		} else
			rest.setText("");
		repeat.setEnabled(!state.last.isEmpty());
	}

	private void sendRepeat() {
		Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
			if (nodes.isEmpty()) {
				Toast.makeText(this, R.string.no_phone, Toast.LENGTH_SHORT).show();
				return;
			}
			for (Node node : nodes)
				Wearable.getMessageClient(this).sendMessage(node.getId(), WatchState.REPEAT, new byte[0]);
			Toast.makeText(this, R.string.sent, Toast.LENGTH_SHORT).show();
		});
	}
}
