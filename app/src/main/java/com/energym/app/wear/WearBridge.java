package com.energym.app.wear;

import android.content.Context;

import com.google.android.gms.wearable.*;

import java.util.Map;

/**
 * Adaptador del Data Layer de Wear OS: publica el estado del entreno para el reloj y recibe «otra serie igual».
 * Sin reloj emparejado o sin Google Play services no hace nada.
 */
public final class WearBridge {
	private static String lastSent = "";

	private WearBridge() {
	}

	public static void publish(Context context, WearState state) {
		Map<String, String> values = state.toMap();
		String key = values.toString();
		if (key.equals(lastSent))
			return;
		lastSent = key;
		try {
			PutDataMapRequest request = PutDataMapRequest.create(WearState.PATH);
			for (Map.Entry<String, String> e : values.entrySet())
				request.getDataMap().putString(e.getKey(), e.getValue());
			Wearable.getDataClient(context.getApplicationContext()).putDataItem(request.asPutDataRequest().setUrgent());
		} catch (RuntimeException ignored) {
			// Sin Play services o sin la API de Wearable en este móvil.
		}
	}

	/** Escucha las peticiones del reloj; devuelve el listener para quitarlo con {@link #stop}. */
	public static MessageClient.OnMessageReceivedListener listen(Context context, Runnable repeat) {
		MessageClient.OnMessageReceivedListener listener = event -> {
			if (WearState.REPEAT.equals(event.getPath()))
				repeat.run();
		};
		try {
			Wearable.getMessageClient(context).addListener(listener);
		} catch (RuntimeException ignored) {
			return null;
		}
		return listener;
	}

	public static void stop(Context context, MessageClient.OnMessageReceivedListener listener) {
		if (listener == null)
			return;
		try {
			Wearable.getMessageClient(context).removeListener(listener);
		} catch (RuntimeException ignored) {
			// Nada que quitar.
		}
	}
}
