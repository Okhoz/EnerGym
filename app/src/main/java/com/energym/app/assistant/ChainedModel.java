package com.energym.app.assistant;

/**
 * Elige el modelo del móvil: el preferido (Gemini Nano del sistema) si el dispositivo lo ofrece y, si no, el
 * alternativo (Gemma descargable). La elección se fija en la primera comprobación de estado.
 */
public final class ChainedModel implements OnDeviceModel {
	private final OnDeviceModel preferred, fallback;
	private volatile OnDeviceModel active;
	/** Si la descarga del preferido falla, se usa el alternativo durante el resto del proceso. */
	private volatile boolean preferredFailed;

	public ChainedModel(OnDeviceModel preferred, OnDeviceModel fallback) {
		this.preferred = preferred;
		this.fallback = fallback;
		active = preferred;
	}

	public void status(Result<Integer> result) {
		if (preferredFailed) {
			active = fallback;
			fallback.status(result);
			return;
		}
		preferred.status(new Result<Integer>() {
			public void success(Integer value) {
				if (value != null && value != UNAVAILABLE) {
					active = preferred;
					result.success(value);
				} else {
					active = fallback;
					fallback.status(result);
				}
			}

			public void failure(String code) {
				active = fallback;
				fallback.status(result);
			}
		});
	}

	public void download(Result<Void> result) {
		OnDeviceModel model = active;
		model.download(new Result<Void>() {
			public void success(Void value) {
				result.success(value);
			}

			public void failure(String code) {
				// Gemini Nano a veces se anuncia como descargable y luego no baja (AICore sin espacio o en
				// emuladores): en vez de reintentar siempre lo mismo, se pasa a Gemma.
				if (model == preferred && fallback != preferred) {
					preferredFailed = true;
					active = fallback;
				}
				result.failure(code);
			}
		});
	}

	public void generate(String prompt, Result<String> result) {
		active.generate(prompt, result);
	}

	public boolean downloadNeedsConsent() {
		return active.downloadNeedsConsent();
	}

	public long downloadBytes() {
		return active.downloadBytes();
	}

	public int downloadProgress() {
		return active.downloadProgress();
	}

	public void onProgress(Runnable listener) {
		preferred.onProgress(listener);
		fallback.onProgress(listener);
	}

	public String label() {
		return active.label();
	}
}
