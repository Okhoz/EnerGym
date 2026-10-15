package com.energym.app.assistant;

/**
 * Puerto del modelo de lenguaje que corre en el propio móvil (Gemini Nano vía AICore). Sin red ni servidor:
 * los callbacks pueden llegar en cualquier hilo; quien lo usa decide dónde entregarlos.
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
	 * Pide a AICore que descargue el modelo; termina cuando está listo para generar.
	 */
	void download(Result<Void> result);

	/**
	 * Genera texto a partir de un único prompt (instrucciones y datos ya incluidos).
	 */
	void generate(String prompt, Result<String> result);
}
