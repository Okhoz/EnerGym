package com.energym.app.sync;

import com.google.android.gms.tasks.TaskCompletionSource;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CallbackLifecycleTest {
	@Test
	public void sdkTaskCompletesAfterOwnerClosesWithoutSchedulingOrEmission() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		CallbackExecutor callbacks = new CallbackExecutor(worker);
		TaskCompletionSource<String> retained = new TaskCompletionSource<>();
		AtomicInteger writes = new AtomicInteger();
		retained.getTask().addOnCompleteListener(callbacks, result -> writes.incrementAndGet());
		callbacks.close();
		worker.shutdown();
		assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
		retained.setResult("retardada");
		assertEquals(0, writes.get());
	}
}
