package com.energym.app.sync;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class CallbackExecutorTest {
	@Test
	public void queuedAndLateCallbacksDoNotRunAfterClose() {
		List<Runnable> queue = new ArrayList<>();
		CallbackExecutor callbacks = new CallbackExecutor(queue::add);
		int[] writes = {0};
		callbacks.execute(() -> writes[0]++);
		callbacks.close();
		queue.remove(0).run();
		callbacks.execute(() -> writes[0]++);
		assertTrue(queue.isEmpty());
		assertEquals(0, writes[0]);
	}

	@Test(expected = RejectedExecutionException.class)
	public void activeWorkerFailureIsNotHidden() {
		new CallbackExecutor(command -> {
			throw new RejectedExecutionException("activo");
		}).execute(() -> {
		});
	}
}
