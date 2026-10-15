package com.energym.app.visual;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceView;

import com.google.android.filament.*;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.*;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Adaptador JNI de vista: el hilo principal posee motor, superficie, clips y destrucción.
 */
public final class NativeAnimationRenderer implements UiHelper.RendererCallback, Choreographer.FrameCallback {
	public interface Listener {
		void progress(float progress);

		void ready(String clipEvidence);

		void failure(String message);
	}

	private Engine engine;
	private Renderer renderer;
	private Scene scene;
	private com.google.android.filament.View view;
	private Camera camera;
	private int cameraEntity, lightEntity;
	private IndirectLight ambient;
	private UbershaderProvider materials;
	private AssetLoader assets;
	private ResourceLoader resources;
	private UiHelper ui;
	private DisplayHelper display;
	private final SurfaceView surface;
	private final ViewerController controller;
	private final Listener listener;
	private final Choreographer choreographer = Choreographer.getInstance();
	private SwapChain swapChain;
	private FilamentAsset asset;
	private Animator animator;
	private int clipIndex = -1, camTarget;
	private float clipDuration;
	private boolean active, closed, loaded;
	private long previousFrameNanos, loadStartedNanos;
	private final ForegroundLoadClock loadClock = new ForegroundLoadClock();
	private long renderedFrames, skippedFrames, gapSamples;
	private double gapTotalMs, maxGapMs;
	private float lastProgress = -1;
	private double baseDistance = 4;
	private final double[] center = new double[3];
	private String clipEvidence = "";

	public NativeAnimationRenderer(Context context, SurfaceView surface, ViewerController controller,
		Listener listener) {
		this.surface = surface;
		this.controller = controller;
		this.listener = listener;
		try {
			// El shader del rig supera el mínimo de uniformes de GLES3 en SwiftShader.
			// Vulkan usa buffers de uniformes; sin Vulkan 1.1 se ofrece fallback antes de entrar en JNI.
			if (!context.getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000))
				throw new UnsupportedOperationException("Vulkan 1.1 no disponible para esta referencia animada");
			Gltfio.init();
			engine = Engine.create(Engine.Backend.VULKAN);
			renderer = engine.createRenderer();
			scene = engine.createScene();
			view = engine.createView();
			cameraEntity = EntityManager.get().create();
			camera = engine.createCamera(cameraEntity);
			camera.setExposure(16f, 1f / 125f, 100f);
			view.setScene(scene);
			view.setCamera(camera);
			renderer.setClearOptions(new Renderer.ClearOptions(0, 0, 0, 1, 0, true, true));
			materials = new UbershaderProvider(engine);
			assets = new AssetLoader(engine, materials, EntityManager.get());
			resources = new ResourceLoader(engine, true);
			lightEntity = EntityManager.get().create();
			new LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(-1, -2, -1).color(1, 1, 1)
				.intensity(100_000f).castShadows(false).build(engine, lightEntity);
			scene.addEntity(lightEntity);
			ambient = new IndirectLight.Builder().irradiance(1, new float[]{1, 1, 1}).intensity(30_000f).build(engine);
			scene.setIndirectLight(ambient);
			display = new DisplayHelper(context);
			ui = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
			ui.setRenderCallback(this);
			ui.attachTo(surface);
		} catch (RuntimeException | LinkageError e) {
			close();
			throw e;
		}
	}

	/**
	 * Los bytes ya han pasado integridad. Comprueba clips reales antes de declarar listo.
	 */
	public void load(byte[] bytes, ResourceManifest manifest) {
		if (closed || asset != null)
			throw new IllegalStateException("Propietario del visor no disponible");
		loadStartedNanos = System.nanoTime();
		asset = assets.createAsset(ByteBuffer.wrap(bytes));
		if (asset == null)
			throw new IllegalArgumentException("No se pudo interpretar el GLB");
		// Filament crea el Animator al iniciar ResourceLoader; consultarlo antes cachea un puntero cero.
		if (!resources.asyncBeginLoad(asset))
			throw new IllegalStateException("No se pudo cargar el recurso nativo");
		animator = asset.getInstance().getAnimator();
		// El GLB único contiene un clip por movimiento: solo se aplica el del ejercicio, con su duración exacta.
		clipIndex = -1;
		for (int i = 0; i < animator.getAnimationCount(); i++) {
			if (!manifest.clipName.equals(animator.getAnimationName(i)))
				continue;
			if (clipIndex >= 0)
				throw new IllegalArgumentException("Clip duplicado en el recurso local");
			clipIndex = i;
		}
		if (clipIndex < 0)
			throw new IllegalArgumentException("Clip no encontrado en el recurso local");
		clipDuration = animator.getAnimationDuration(clipIndex);
		if (!Float.isFinite(clipDuration) || Math.abs(clipDuration - manifest.duration) > .001f)
			throw new IllegalArgumentException("Animación local distinta de la inspeccionada");
		clipEvidence = manifest.clipName + " " + clipDuration + " s; " + manifest.kind;
		Box box = asset.getBoundingBox();
		float[] c = box.getCenter(), h = box.getHalfExtent();
		for (int i = 0; i < 3; i++)
			center[i] = c[i];
		baseDistance = Math.max(.2, 3.0 * Math.max(manifest.viewRadius, Math.max(h[0], Math.max(h[1], h[2]))));
		// El límite de reposo no sigue la pose animada; cada clip coloca CamTarget en el centro de su movimiento.
		camTarget = asset.getFirstEntityByName("CamTarget");
		updateCenter();
		asset.releaseSourceData();
	}

	private void updateCenter() {
		if (camTarget == 0)
			return;
		TransformManager transforms = engine.getTransformManager();
		float[] matrix = transforms.getWorldTransform(transforms.getInstance(camTarget), new float[16]);
		center[0] = matrix[12];
		center[1] = matrix[13];
		center[2] = matrix[14];
	}

	public void resume() {
		if (!closed && !active) {
			active = true;
			controller.active(true);
			previousFrameNanos = 0;
			choreographer.postFrameCallback(this);
		}
	}

	public void pause() {
		active = false;
		loadClock.pause();
		controller.active(false);
		previousFrameNanos = 0;
		choreographer.removeFrameCallback(this);
	}

	@Override
	public void doFrame(long nanos) {
		if (!active || closed)
			return;
		try {
			if (asset != null && !loaded) {
				loadClock.frame(nanos);
				if (loadClock.expired())
					throw new IllegalStateException("La carga nativa agotó su tiempo");
			}
			if (!ui.isReadyToRender() || swapChain == null) {
				previousFrameNanos = 0;
				choreographer.postFrameCallback(this);
				return;
			}
			if (asset != null && !loaded) {
				resources.asyncUpdateLoad();
				int entity;
				while ((entity = asset.popRenderable()) != 0)
					scene.addEntity(entity);
				float progress = resources.asyncGetLoadProgress();
				if (progress - lastProgress > .05f) {
					lastProgress = progress;
					listener.progress(progress);
				}
				if (progress >= 1) {
					loaded = true;
					Log.i("EnerGymViewer", "Native ready " + clipEvidence + " loadMs=" +
						(System.nanoTime() - loadStartedNanos) / 1_000_000);
					listener.ready(clipEvidence);
				}
			}
			if (previousFrameNanos != 0) {
				double gapMs = (nanos - previousFrameNanos) / 1_000_000.0;
				if (loaded) {
					gapSamples++;
					gapTotalMs += gapMs;
					maxGapMs = Math.max(maxGapMs, gapMs);
				}
				controller.advance(Math.min(.1f, (nanos - previousFrameNanos) / 1_000_000_000f));
			}
			previousFrameNanos = nanos;
			ViewerState state = controller.state();
			if (loaded) {
				animator.applyAnimation(clipIndex, Math.min(state.playhead, clipDuration));
				animator.updateBoneMatrices();
				updateCenter();
			}
			double yaw = Math.toRadians(state.yaw), pitch = Math.toRadians(state.pitch), distance =
				baseDistance * state.zoom;
			camera.lookAt(center[0] + distance * Math.cos(pitch) * Math.sin(yaw),
				center[1] + distance * Math.sin(pitch), center[2] + distance * Math.cos(pitch) * Math.cos(yaw),
				center[0], center[1], center[2], 0, 1, 0);
			if (renderer.beginFrame(swapChain, nanos)) {
				renderer.render(view);
				renderer.endFrame();
				if (loaded)
					renderedFrames++;
			} else if (loaded)
				skippedFrames++;
			choreographer.postFrameCallback(this);
		} catch (RuntimeException | LinkageError e) {
			pause();
			Log.e("EnerGymViewer", "Fallo del renderizador", e);
			listener.failure("El visor nativo no está disponible en este dispositivo.");
		}
	}

	@Override
	public void onNativeWindowChanged(Surface window) {
		if (closed || engine == null)
			return;
		try {
			if (swapChain != null) {
				engine.destroySwapChain(swapChain);
				swapChain = null;
				engine.flushAndWait();
			}
			swapChain = engine.createSwapChain(window);
			if (surface.getDisplay() != null)
				display.attach(renderer, surface.getDisplay());
		} catch (RuntimeException | LinkageError e) {
			pause();
			Log.e("EnerGymViewer", "Superficie no disponible", e);
			listener.failure("No se pudo preparar la superficie del visor.");
		}
	}

	@Override
	public void onDetachedFromSurface() {
		if (engine == null)
			return;
		if (display != null)
			display.detach();
		if (swapChain != null) {
			engine.destroySwapChain(swapChain);
			engine.flushAndWait();
			swapChain = null;
		}
	}

	@Override
	public void onResized(int width, int height) {
		if (closed || engine == null || width <= 0 || height <= 0)
			return;
		try {
			view.setViewport(new Viewport(0, 0, width, height));
			camera.setProjection(45, (double) width / height, .05, 100, Camera.Fov.VERTICAL);
			// Filament 1.77.3 wait(mode) usa su centinela -1 internamente: espera infinita con FLUSH.
			Fence fence = engine.createFence();
			fence.wait(Fence.Mode.FLUSH);
			engine.destroyFence(fence);
		} catch (RuntimeException | LinkageError e) {
			pause();
			Log.e("EnerGymViewer", "Cambio de superficie fallido", e);
			listener.failure("No se pudo ajustar la superficie del visor.");
		}
	}

	/**
	 * Orden explícito e idempotente: retirar callbacks antes de liberar superficie y objetos JNI.
	 */
	public void close() {
		if (closed)
			return;
		pause();
		if (ui != null)
			ui.detach();
		closed = true;
		if (engine == null)
			return;
		if (resources != null) {
			resources.asyncCancelLoad();
			resources.evictResourceData();
		}
		if (asset != null) {
			scene.removeEntities(asset.getEntities());
			assets.destroyAsset(asset);
			asset = null;
			animator = null;
		}
		if (assets != null)
			assets.destroy();
		if (materials != null) {
			materials.destroyMaterials();
			materials.destroy();
		}
		if (resources != null)
			resources.destroy();
		if (ambient != null) {
			scene.setIndirectLight(null);
			engine.destroyIndirectLight(ambient);
		}
		if (lightEntity != 0) {
			engine.destroyEntity(lightEntity);
			EntityManager.get().destroy(lightEntity);
		}
		if (renderer != null)
			engine.destroyRenderer(renderer);
		if (view != null)
			engine.destroyView(view);
		if (scene != null)
			engine.destroyScene(scene);
		if (cameraEntity != 0) {
			engine.destroyCameraComponent(cameraEntity);
			EntityManager.get().destroy(cameraEntity);
		}
		engine.destroy();
		engine = null;
		Log.i("EnerGymViewer",
			"Native owner closed; frame callback removed; rendered=" + renderedFrames + " skipped=" + skippedFrames +
				" meanFrameGapMs=" + (gapSamples == 0 ? 0 : gapTotalMs / gapSamples) + " maxFrameGapMs=" + maxGapMs);
	}
}

