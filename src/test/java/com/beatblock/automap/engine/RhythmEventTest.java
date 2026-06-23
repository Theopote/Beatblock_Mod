package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmEventTest {

	@Test
	void defaultsNullTypeToKickAndClampsEnergy() {
		RhythmEvent event = new RhythmEvent(1.5, null, 1.5f);
		assertEquals(RhythmType.KICK, event.getType());
		assertEquals(1.0f, event.getEnergy(), 1e-6f);
		assertEquals(1.5, event.getTimeSeconds(), 1e-9);
	}

	@Test
	void preservesProvidedValues() {
		RhythmEvent event = new RhythmEvent(0.75, RhythmType.HIHAT, 0.4f);
		assertEquals(RhythmType.HIHAT, event.getType());
		assertEquals(0.4f, event.getEnergy(), 1e-6f);
	}
}
