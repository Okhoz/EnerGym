package com.energym.app.data;

import org.junit.Test;
import static org.junit.Assert.*;

public final class InitialRestoreGateTest {
	@Test public void standaloneLocalStoreNeedsNoCloud() {
		InitialRestoreGate gate = new InitialRestoreGate();
		assertFalse(gate.pending());
		gate.requireWritable();
	}

	@Test public void virginAccountRequiresSuccessfulServerRestore() {
		InitialRestoreGate gate = new InitialRestoreGate();
		gate.requireRestore();
		assertTrue(gate.needsCacheInspection());
		gate.cacheInspected(false);
		assertFalse(gate.needsCacheInspection());
		assertTrue(gate.pending());
		assertThrows(IllegalStateException.class, gate::requireWritable);
		// Neither a failed download nor a rejected batch calls serverRestored.
		assertTrue(gate.pending());
		gate.serverRestored();
		assertFalse(gate.pending());
		gate.requireWritable();
	}

	@Test public void existingRowsRemainWritableOfflineAndCannotRelock() {
		InitialRestoreGate gate = new InitialRestoreGate();
		gate.requireRestore();
		gate.cacheInspected(true);
		assertFalse(gate.pending());
		gate.requireWritable();
		gate.cacheInspected(false);
		assertFalse(gate.pending());
	}
}
