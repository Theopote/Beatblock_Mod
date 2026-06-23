package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleEventTest {

	@Test
	void defaultsNullTypeToSpark() {
		ParticleEvent event = new ParticleEvent(-2.0, null);
		assertEquals(0.0, event.getTimeSeconds(), 1e-9);
		assertEquals(ParticleType.SPARK, event.getType());
	}

	@Test
	void preservesProvidedType() {
		ParticleEvent event = new ParticleEvent(1.25, ParticleType.FLASH);
		assertEquals(1.25, event.getTimeSeconds(), 1e-9);
		assertEquals(ParticleType.FLASH, event.getType());
	}
}
