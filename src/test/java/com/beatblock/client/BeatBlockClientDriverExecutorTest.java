package com.beatblock.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BeatBlockClientDriverExecutorTest {
	@Test
	void globalEventExecutorIsCreatedOncePerDriver() {
		BeatBlockClientDriver driver = new BeatBlockClientDriver(null);
		assertNotNull(driver.globalEventExecutorForTests());
		assertSame(driver.globalEventExecutorForTests(), driver.globalEventExecutorForTests());
	}
}