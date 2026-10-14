package com.energym.app.visual;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ForegroundLoadClockTest {
	@Test public void longBackgroundGapDoesNotExpireButActiveDeadlineStillDoes() {
		ForegroundLoadClock clock = new ForegroundLoadClock();
		clock.frame(1); clock.frame(10_000_000_001L); clock.pause();
		clock.frame(110_000_000_001L); clock.frame(125_000_000_001L);
		assertFalse(clock.expired());
		clock.frame(131_000_000_001L);
		assertTrue(clock.expired());
	}
}
