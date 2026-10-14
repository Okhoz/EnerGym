package com.energym.app.ui.effects;

/**
 * Política visual pura: los recursos del dispositivo prevalecen sobre el efecto decorativo.
 */
public final class GlassPerformancePolicy {
	public static final int MAX_WIDTH_PX = 256;
	public static final int MAX_HEIGHT_PX = 512;
	public static final float BLUR_RADIUS_PX = 10f;
	public static final int FRAME_SAMPLES = 32;
	private static final long SLOW_FRAME_NS = 25_000_000L;

	private GlassPerformancePolicy() {
	}

	public static boolean allowBlur(int api, boolean hardware, boolean lowRam, boolean lowMemory, int memoryClassMb, boolean powerSave, boolean animations, boolean nativeViewer) {
		return api >= 31 && hardware && !lowRam && !lowMemory && memoryClassMb >= 256 && !powerSave && animations && !nativeViewer;
	}

	public static boolean allowMotion(boolean animations, boolean powerSave) {
		return animations && !powerSave;
	}

	/**
	 * Una sola ventana finita; no conserva fotogramas ni asigna memoria en cada muestra.
	 */
	public static final class FrameBudget {
		private final long[] samples = new long[FRAME_SAMPLES];
		private int count, slow;

		public boolean record(long totalDurationNs) {
			if (count >= samples.length)
				return exceeded();
			samples[count++] = totalDurationNs;
			if (totalDurationNs > SLOW_FRAME_NS)
				slow++;
			return exceeded();
		}

		public boolean exceeded() {
			return slow >= 8;
		}

		public boolean complete() {
			return count == samples.length || exceeded();
		}

		public int sampleCount() {
			return count;
		}
	}
}
