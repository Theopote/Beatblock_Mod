package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackDefinitionTest {

	@Test
	void storesFieldsAndDetectsCustomColor() {
		TrackDefinition colored = new TrackDefinition(
			"kick", "Kick", TrackDefinition.VisualType.IMPULSE, TrackDefinition.GROUP_RHYTHM, 0xFF112233);
		assertEquals("kick", colored.getKey());
		assertEquals("Kick", colored.getDisplayName());
		assertEquals(TrackDefinition.VisualType.IMPULSE, colored.getVisualType());
		assertEquals(TrackDefinition.GROUP_RHYTHM, colored.getGroup());
		assertEquals(0xFF112233, colored.getColor());
		assertTrue(colored.hasCustomColor());
	}

	@Test
	void defaultConstructorUsesZeroColor() {
		TrackDefinition plain = new TrackDefinition(
			"waveform", "Main", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE);
		assertEquals(0, plain.getColor());
		assertFalse(plain.hasCustomColor());
	}
}
