package com.energym.app.ui.effects;

import org.junit.Test;

import static org.junit.Assert.*;

public final class GlassPerformancePolicyTest {
	@Test
	public void blurRequiresSupportedHardwareAndHeadroom() {
		assertTrue(GlassPerformancePolicy.allowBlur(31, true, false, false, 256, false, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(30, true, false, false, 512, false, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(31, false, false, false, 512, false, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(31, true, true, false, 512, false, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(31, true, false, true, 512, false, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(31, true, false, false, 128, false, true, false));
	}

	@Test
	public void powerSaveReducedMotionAndNativeRendererDisableBlur() {
		assertFalse(GlassPerformancePolicy.allowBlur(35, true, false, false, 512, true, true, false));
		assertFalse(GlassPerformancePolicy.allowBlur(35, true, false, false, 512, false, false, false));
		assertFalse(GlassPerformancePolicy.allowBlur(35, true, false, false, 512, false, true, true));
		assertFalse(GlassPerformancePolicy.allowMotion(false, false));
		assertFalse(GlassPerformancePolicy.allowMotion(true, true));
		assertTrue(GlassPerformancePolicy.allowMotion(true, false));
	}

	@Test
	public void sustainedSlowFramesTriggerFallbackAndSamplingIsBounded() {
		GlassPerformancePolicy.FrameBudget slow = new GlassPerformancePolicy.FrameBudget();
		for (int i = 0; i < 7; i++)
			assertFalse(slow.record(26_000_000L));
		assertTrue(slow.record(26_000_000L));
		assertTrue(slow.complete());
		GlassPerformancePolicy.FrameBudget fast = new GlassPerformancePolicy.FrameBudget();
		for (int i = 0; i < 100; i++)
			assertFalse(fast.record(16_000_000L));
		assertTrue(fast.complete());
		assertEquals(GlassPerformancePolicy.FRAME_SAMPLES, fast.sampleCount());
	}

	@Test
	public void decorativeSurfaceHasConservativeHardLimits() {
		assertTrue(GlassPerformancePolicy.MAX_WIDTH_PX * GlassPerformancePolicy.MAX_HEIGHT_PX <= 131072);
		assertTrue(GlassPerformancePolicy.BLUR_RADIUS_PX <= 12f);
	}
}
