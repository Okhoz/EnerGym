package com.energym.app.assistant;

/**
 * Elige el modelo del móvil: el preferido (Gemini Nano del sistema) si el dispositivo lo ofrece y, si no, el
 * alternativo (Gemma descargable). La elección se fija en la primera comprobación de estado.
 */
public final class ChainedModel implements OnDeviceModel {
	private final OnDeviceModel preferred, fallback;
	private volatile OnDeviceModel active;

	public ChainedModel(OnDeviceModel preferred, OnDeviceModel fallback) {
		this.preferred = preferred;
		this.fallback = fallback;
		active = preferred;
	}

	public void status(Result<Integer> result) {
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
		active.download(result);
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
