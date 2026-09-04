package com.beatblock.automap.choreography;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChoreographyLayerTest {

	@Test
	void accentLayerScalesEnergyAndHeight() {
		Map<String, Object> scaled = ChoreographyLayer.ACCENT.scaleEventParams(
			Map.of("energy", 0.8f, "height", 4.0f),
			0.8f
		);

		assertEquals(0.2f, (float) scaled.get("energy"), 1e-6f);
		assertEquals(1.0f, (float) scaled.get("height"), 1e-6f);
		assertEquals("ACCENT", scaled.get(ChoreographyLayer.PARAM_KEY));
	}

	@Test
	void defaultAccentPrimitiveIsPulse() {
		assertEquals("pulse", ChoreographyLayer.defaultAccentPrimitiveId());
	}

	@Test
	void phraseLayerScalesEnergyAndHeight() {
		Map<String, Object> scaled = ChoreographyLayer.PHRASE.scaleEventParams(
			Map.of("energy", 1.0f, "height", 4.0f),
			1.0f
		);

		assertEquals(0.75f, (float) scaled.get("energy"), 1e-6f);
		assertEquals(3.0f, (float) scaled.get("height"), 1e-6f);
		assertEquals("PHRASE", scaled.get(ChoreographyLayer.PARAM_KEY));
	}
}
