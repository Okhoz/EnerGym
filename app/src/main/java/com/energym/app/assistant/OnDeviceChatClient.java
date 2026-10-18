package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.domain.Exercise;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * ADAPTADOR del entrenador personal local: Gemini Nano en el propio móvil, sin servidor, cuentas ni coste.
 * Si el dispositivo no tiene el modelo, el chat sigue con el guiado local por reglas.
 */
public final class OnDeviceChatClient implements ChatClient {
	private final OnDeviceModel model;
	private final List<Exercise> catalog;
	private final int catalogVersion;
	private final Executor worker, delivery;
	private int state = -1;
	private boolean checking, failed;
	private long epoch;
	private Runnable listener = () -> {
	};

	public OnDeviceChatClient(OnDeviceModel model, List<Exercise> catalog, int catalogVersion, Executor worker,
		Executor delivery) {
		this.model = Objects.requireNonNull(model);
		this.catalog = Objects.requireNonNull(catalog);
		this.catalogVersion = catalogVersion;
		this.worker = Objects.requireNonNull(worker);
		this.delivery = Objects.requireNonNull(delivery);
		model.onProgress(() -> {
			Runnable notify;
			synchronized (this) {
				notify = listener;
			}
			delivery.execute(notify);
		});
	}

	/**
	 * Aviso en el hilo de UI cuando cambia la disponibilidad (p. ej. al terminar la descarga del modelo).
	 */
	public synchronized void listen(Runnable changed) {
		listener = changed == null ? () -> {
		} : changed;
	}

	/**
	 * Consulta si el modelo está en el móvil. Lo que gestiona el sistema (Gemini Nano) se descarga solo; una
	 * descarga grande (Gemma) espera a que el usuario la pida con startDownload().
	 */
	public void activate(String owner) {
		synchronized (this) {
			epoch++;
			if (checking || state == OnDeviceModel.AVAILABLE || state == OnDeviceModel.UNAVAILABLE ||
				state == OnDeviceModel.DOWNLOADING)
				return;
			checking = true;
		}
		model.status(new OnDeviceModel.Result<Integer>() {
			public void success(Integer value) {
				// Una descarga ya en curso (p. ej. tras reabrir la app) se sigue sin volver a preguntar.
				if (value == OnDeviceModel.DOWNLOADING ||
					(value == OnDeviceModel.DOWNLOADABLE && !model.downloadNeedsConsent()))
					download();
				else
					update(value);
			}

			public void failure(String code) {
				update(OnDeviceModel.UNAVAILABLE);
			}
		});
	}

	/**
	 * Descarga pedida por el usuario (Gemma). No hace nada si no hay nada que descargar o ya se está descargando.
	 */
	public void startDownload() {
		synchronized (this) {
			if (state != OnDeviceModel.DOWNLOADABLE || checking)
				return;
			checking = true;
		}
		download();
	}

	private void download() {
		Runnable notify;
		synchronized (this) {
			// Sigue «comprobando» hasta que termine: otra activación no lanza una segunda descarga.
			state = OnDeviceModel.DOWNLOADING;
			failed = false;
			notify = listener;
		}
		delivery.execute(notify);
		model.download(new OnDeviceModel.Result<Void>() {
			public void success(Void ignored) {
				update(OnDeviceModel.AVAILABLE);
			}

			public void failure(String code) {
				// Se puede volver a intentar desde el chat o en la próxima activación.
				synchronized (OnDeviceChatClient.this) {
					failed = true;
				}
				update(OnDeviceModel.DOWNLOADABLE);
			}
		});
	}

	/**
	 * Texto del botón para descargar el modelo, o vacío si no hace falta que el usuario lo pida.
	 */
	public synchronized String downloadOffer() {
		if (state != OnDeviceModel.DOWNLOADABLE || !(failed || model.downloadNeedsConsent()))
			return "";
		if (failed)
			return Txt.t(R.string.od_retry_download);
		return Txt.t(R.string.od_download, model.downloadBytes() / 1e9);
	}

	public int downloadProgress() {
		return model.downloadProgress();
	}

	public String modelLabel() {
		return model.label();
	}

	private void update(int value) {
		Runnable notify;
		synchronized (this) {
			state = value;
			checking = false;
			notify = listener;
		}
		delivery.execute(notify);
	}

	public synchronized int state() {
		return state;
	}

	public synchronized boolean connected() {
		return state == OnDeviceModel.AVAILABLE;
	}

	public boolean automaticConnection() {
		return true;
	}

	public boolean onDevice() {
		return true;
	}

	public synchronized String unavailableCode() {
		if (state == OnDeviceModel.AVAILABLE)
			return "";
		if (state == OnDeviceModel.DOWNLOADABLE && failed)
			return "MODEL_DOWNLOAD_FAILED";
		if (state == OnDeviceModel.DOWNLOADABLE && model.downloadNeedsConsent())
			return "MODEL_NEEDS_DOWNLOAD";
		if (state == OnDeviceModel.DOWNLOADABLE || state == OnDeviceModel.DOWNLOADING)
			return "MODEL_DOWNLOADING";
		return state == OnDeviceModel.UNAVAILABLE ? "MODEL_UNAVAILABLE" : "MODEL_CHECKING";
	}

	public void send(Request request, Callback callback) {
		final long generation;
		synchronized (this) {
			generation = epoch;
		}
		if (!connected()) {
			deliver(generation, callback, null, unavailableCode());
			return;
		}
		worker.execute(() -> {
			String prompt;
			try {
				prompt = TrainerPrompt.build(request, catalog);
			} catch (RuntimeException e) {
				deliver(generation, callback, null, "MODEL_FAILED");
				return;
			}
			model.generate(prompt, new OnDeviceModel.Result<String>() {
				public void success(String output) {
					try {
						deliver(generation, callback, TrainerPrompt.reply(output, request, catalog, catalogVersion), null);
					} catch (RuntimeException e) {
						deliver(generation, callback, null, "MODEL_FAILED");
					}
				}

				public void failure(String code) {
					deliver(generation, callback, null, code);
				}
			});
		});
	}

	public void connect(String endpoint, String code, Callback callback) {
		delivery.execute(() -> callback.failure("PAIRING_DISABLED"));
	}

	/**
	 * Cerrar sesión no quita el modelo del móvil: solo descarta respuestas pendientes.
	 */
	public synchronized void disconnect() {
		epoch++;
	}

	@Override
	public synchronized void cancelPending() {
		epoch++;
	}

	private void deliver(long generation, Callback callback, String json, String error) {
		delivery.execute(() -> {
			synchronized (OnDeviceChatClient.this) {
				if (epoch != generation)
					return;
			}
			if (error == null)
				callback.success(json);
			else
				callback.failure(error);
		});
	}
}
