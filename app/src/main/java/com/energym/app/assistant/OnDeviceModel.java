package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
/**
 * Puerto del modelo de lenguaje que corre en el propio móvil (Gemini Nano vía AICore o Gemma descargado).
 * Sin servidor: los callbacks pueden llegar en cualquier hilo; quien lo usa decide dónde entregarlos.
 */
public interface OnDeviceModel {
	int UNAVAILABLE = 0, DOWNLOADABLE = 1, DOWNLOADING = 2, AVAILABLE = 3;

	interface Result<T> {
		void success(T value);

		void failure(String code);
	}

	/**
	 * Estado del modelo en este dispositivo: una de las constantes de esta interfaz.
	 */
	void status(Result<Integer> result);

	/**
	 * Descarga el modelo; termina cuando está listo para generar.
	 */
	void download(Result<Void> result);

	/**
	 * Genera texto a partir de un único prompt (instrucciones y datos ya incluidos).
	 */
	void generate(String prompt, Result<String> result);

	/**
	 * Verdadero si la descarga es grande y debe pedirla el usuario (no la gestiona el sistema).
	 */
	default boolean downloadNeedsConsent() {
		return false;
	}

	/**
	 * Tamaño de la descarga en bytes, o 0 si lo gestiona el sistema.
	 */
	default long downloadBytes() {
		return 0;
	}

	/**
	 * Progreso de la descarga en curso, 0–100, o -1 si no se conoce.
	 */
	default int downloadProgress() {
		return -1;
	}

	/**
	 * Aviso (desde cualquier hilo) cada vez que avanza la descarga.
	 */
	default void onProgress(Runnable listener) {
	}

	/**
	 * Nombre que ve el usuario («Gemini Nano», «Gemma»).
	 */
	default String label() {
		return Txt.t(R.string.as_device_ai);
	}
}
