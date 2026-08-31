package com.beatblock.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientThreadGuardTest {

	@Test
	void allowsWhenNoMinecraftClient() {
		assertTrue(ClientThreadGuard.isClientThread());
		assertDoesNotThrow(ClientThreadGuard::assertClientThread);
	}
}
