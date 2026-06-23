package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelSpecTest {

	@Test
	void sampleInterpolatesUsingCurveLibrary() {
		ChannelSpec channel = ChannelSpec.enabled(
			InfluenceDimension.TRANSFORM_SCALE,
			PathKind.SCALE_UNIFORM,
			CurveKind.LINEAR,
			1f,
			2f
		);
		assertEquals(1f, channel.sample(0f), 1e-6);
		assertEquals(2f, channel.sample(1f), 1e-6);
		assertEquals(1.5f, channel.sample(0.5f), 1e-6);
	}

	@Test
	void sampleMagnitudeScalesByEnergy() {
		ChannelSpec channel = ChannelSpec.enabled(
			InfluenceDimension.TRANSFORM_POSITION,
			PathKind.OFFSET_Y,
			CurveKind.LINEAR,
			0f,
		 10f
		);
		assertEquals(5f, channel.sampleMagnitude(0.5f, 1f), 1e-6);
		assertEquals(2.5f, channel.sampleMagnitude(0.5f, 0.5f), 1e-6);
	}
}
