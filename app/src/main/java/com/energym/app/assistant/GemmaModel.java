package com.energym.app.assistant;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * ADAPTADOR Gemma 4 E2B con LiteRT-LM para móviles sin Gemini Nano. El usuario descarga una vez el modelo
 * (Apache 2.0, Hugging Face, versión fijada y verificada por SHA-256); después todo corre en el móvil, sin red
 * ni servidor. Se descarga sola al abrir la app (Wi-Fi o datos) con el gestor de descargas de Android, reanudable.
 */
public final class GemmaModel implements OnDeviceModel {
	static final String URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
		"b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm";
	static final long BYTES = 2588147712L;
	static final String SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c";
	private static final String FILE = "gemma-4-E2B-it.litertlm", PREFS = "on_device_model", DOWNLOAD = "download";
	/**
	 * Margen sobre el modelo: memoria del motor (≈1,7 GB en CPU) y espacio para el archivo y su verificación.
	 */
	private static final long MIN_RAM = 5_500_000_000L, MIN_FREE = BYTES + 400_000_000L;
	private static final int MAX_TOKENS = 6144;
	private final Context context;
	private final Executor worker;
	private Engine engine;
	private volatile int progress = -1;
	private volatile Runnable progressListener = () -> {
	};

	public GemmaModel(Context context, Executor worker) {
		this.context = context.getApplicationContext();
		this.worker = worker;
	}

	public String label() {
		return "Gemma";
	}

	/**
	 * Se descarga sola al abrir la app por primera vez, como parte de la instalación (decisión del producto).
	 */
	public boolean downloadNeedsConsent() {
		return false;
	}

	public long downloadBytes() {
		return BYTES;
	}

	public int downloadProgress() {
		return progress;
	}

	@Override
	public void onProgress(Runnable listener) {
		progressListener = listener == null ? () -> {
		} : listener;
	}

	private File directory() {
		File external = context.getExternalFilesDir("models");
		return external != null ? external : new File(context.getFilesDir(), "models");
	}

	private File model() {
		return new File(directory(), FILE);
	}

	private File verified() {
		return new File(directory(), FILE + ".ok");
	}

	private SharedPreferences prefs() {
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public void status(Result<Integer> result) {
		worker.execute(() -> {
			int value;
			// Un fallo al leer el almacenamiento no debe dejar el chat esperando para siempre.
			try {
				if (verified().isFile() && model().length() == BYTES)
					value = AVAILABLE;
				else if (prefs().getLong(DOWNLOAD, -1) >= 0 && running(prefs().getLong(DOWNLOAD, -1)))
					value = DOWNLOADING;
				else
					value = supported() ? DOWNLOADABLE : UNAVAILABLE;
			} catch (RuntimeException e) {
				value = UNAVAILABLE;
			}
			result.success(value);
		});
	}

	/**
	 * Móviles de 64 bits ARM con memoria y espacio suficientes; los demás siguen con el guiado local.
	 */
	private boolean supported() {
		if (!Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a"))
			return false;
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
		am.getMemoryInfo(memory);
		File dir = directory();
		dir.mkdirs();
		return memory.totalMem >= MIN_RAM && new StatFs(dir.getPath()).getAvailableBytes() >= MIN_FREE;
	}

	private boolean running(long id) {
		long status = queryBytes(id)[0];
		return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING ||
			status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_SUCCESSFUL;
	}

	/**
	 * Estado y bytes descargados de una descarga del sistema; {-1, 0} si ya no existe.
	 */
	private long[] queryBytes(long id) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
			if (c == null || !c.moveToFirst())
				return new long[]{-1, 0};
			return new long[]{c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
				c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))};
		}
	}

	public void download(Result<Void> result) {
		new Thread(() -> {
			try {
				DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
				long id = prefs().getLong(DOWNLOAD, -1);
				if (id < 0 || !running(id)) {
					File target = model();
					directory().mkdirs();
					target.delete();
					verified().delete();
					DownloadManager.Request request = new DownloadManager.Request(Uri.parse(URL))
						.setTitle(Txt.t(R.string.gm_download_title))
						.setDescription(Txt.t(R.string.gm_download_body))
						.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI |
							DownloadManager.Request.NETWORK_MOBILE)
						.setAllowedOverMetered(true)
						.setAllowedOverRoaming(false)
						.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
						.setDestinationUri(Uri.fromFile(target));
					id = dm.enqueue(request);
					prefs().edit().putLong(DOWNLOAD, id).apply();
				}
				while (true) {
					long[] q = queryBytes(id);
					int status = (int) q[0];
					progress = (int) Math.min(99, q[1] * 100 / BYTES);
					progressListener.run();
					if (status == DownloadManager.STATUS_SUCCESSFUL)
						break;
					if (status == DownloadManager.STATUS_FAILED || status == -1) {
						prefs().edit().remove(DOWNLOAD).apply();
						progress = -1;
						result.failure("MODEL_DOWNLOAD_FAILED");
						return;
					}
					Thread.sleep(2000);
				}
				prefs().edit().remove(DOWNLOAD).apply();
				if (model().length() != BYTES || !SHA256.equals(sha256(model()))) {
					model().delete();
					progress = -1;
					result.failure("MODEL_DOWNLOAD_FAILED");
					return;
				}
				try (FileOutputStream ok = new FileOutputStream(verified())) {
					ok.write(SHA256.getBytes("UTF-8"));
				}
				progress = 100;
				result.success(null);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				progress = -1;
				result.failure("MODEL_DOWNLOAD_FAILED");
			} catch (Exception e) {
				progress = -1;
				result.failure("MODEL_DOWNLOAD_FAILED");
			}
		}, "gemma-download").start();
	}

	private static String sha256(File file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] buffer = new byte[1 << 20];
		try (InputStream in = new FileInputStream(file)) {
			int n;
			while ((n = in.read(buffer)) > 0)
				digest.update(buffer, 0, n);
		}
		StringBuilder hex = new StringBuilder();
		for (byte b : digest.digest())
			hex.append(String.format(Locale.ROOT, "%02x", b));
		return hex.toString();
	}

	public void generate(String prompt, Result<String> result) {
		worker.execute(() -> {
			String text;
			try (Conversation conversation = engine().createConversation(new ConversationConfig())) {
				Message reply = conversation.sendMessage(prompt);
				StringBuilder out = new StringBuilder();
				for (Content c : reply.getContents().getContents())
					if (c instanceof Content.Text)
						out.append(((Content.Text) c).getText());
				text = out.toString();
			} catch (Throwable e) {
				result.failure("MODEL_FAILED");
				return;
			}
			if (text.trim().isEmpty())
				result.failure("MODEL_FAILED");
			else
				result.success(text);
		});
	}

	/**
	 * Carga perezosa (varios segundos): GPU si el móvil la ofrece y, si falla, CPU.
	 */
	private boolean reverified;

	private synchronized Engine engine() {
		if (engine != null)
			return engine;
		// Hasta Android 9 otras apps con permiso de almacenamiento pueden escribir en Android/data: antes de cargar
		// el modelo se comprueba de nuevo su huella (una vez por proceso). Desde Android 10 la carpeta es privada.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !reverified) {
			try {
				if (model().length() != BYTES || !SHA256.equals(sha256(model()))) {
					model().delete();
					verified().delete();
					throw new IllegalStateException("Modelo modificado: se volverá a descargar");
				}
			} catch (IllegalStateException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException("No se pudo verificar el modelo", e);
			}
			reverified = true;
		}
		String path = model().getPath();
		for (Backend backend : new Backend[]{new Backend.GPU(), new Backend.CPU()}) {
			// Ventana de tokens explícita: el prompt (≈3500 tokens como mucho) más la respuesta caben siempre.
			Engine candidate = new Engine(new EngineConfig(path, backend, null, null, MAX_TOKENS));
			try {
				candidate.initialize();
				engine = candidate;
				return engine;
			} catch (Throwable e) {
				candidate.close();
			}
		}
		throw new IllegalStateException("No se pudo cargar el modelo");
	}

	/**
	 * Libera la memoria del motor (p. ej. con poca memoria); se vuelve a cargar al siguiente mensaje.
	 */
	public void release() {
		worker.execute(() -> {
			synchronized (this) {
				if (engine != null)
					engine.close();
				engine = null;
			}
		});
	}
}
