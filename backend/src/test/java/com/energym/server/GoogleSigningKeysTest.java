package com.energym.server;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleSigningKeysTest {
	static final class Time extends Clock {
		Instant now = FirebaseIdTokenVerifierTest.NOW;
		public ZoneId getZone() { return ZoneOffset.UTC; }
		public Clock withZone(ZoneId zone) { return this; }
		public Instant instant() { return now; }
	}
	@Test void unknownKidsCannotCauseAKeyDownloadForEveryRequest() throws Exception {
		Time clock = new Time(); AtomicInteger loads = new AtomicInteger();
		GoogleSigningKeys keys = new GoogleSigningKeys(clock, () -> {
			loads.incrementAndGet(); return new GoogleSigningKeys.KeySet(Map.of("known", FirebaseIdTokenVerifierTest.KEY.getPublic()), Duration.ofMinutes(5));
		});
		assertNotNull(keys.key("known"));
		for (int index = 0; index < 50; index++) assertNull(keys.key("unknown-" + index));
		assertEquals(1, loads.get());
		clock.now = clock.now.plusSeconds(61); assertNull(keys.key("new"));
		assertEquals(2, loads.get());
	}
	@Test void expiredKeysAreNotAcceptedWhileGoogleIsUnavailable() throws Exception {
		Time clock = new Time(); AtomicInteger loads = new AtomicInteger();
		GoogleSigningKeys keys = new GoogleSigningKeys(clock, () -> {
			if (loads.incrementAndGet() > 1) throw new IOException("offline");
			return new GoogleSigningKeys.KeySet(Map.of("known", FirebaseIdTokenVerifierTest.KEY.getPublic()), Duration.ofMinutes(5));
		});
		assertNotNull(keys.key("known")); clock.now = clock.now.plusSeconds(301);
		assertThrows(IOException.class, () -> keys.key("known"));
		assertThrows(IOException.class, () -> keys.key("known"));
		assertEquals(2, loads.get());
	}
}
