package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockInfluencePresetTest {

	@Test
	void channelsForReturnsOnlyEnabledMatchingDimension() {
		ChannelSpec scale = ChannelSpec.enabled(
			InfluenceDimension.TRANSFORM_SCALE, PathKind.SCALE_UNIFORM, CurveKind.LINEAR, 1f, 1.2f);
		ChannelSpec disabled = new ChannelSpec(
			InfluenceDimension.TRANSFORM_SCALE, PathKind.SCALE_UNIFORM, CurveKind.LINEAR, 1f, 1.2f,
			false, DurationPolicy.fullDuration());
		ChannelSpec offset = ChannelSpec.enabled(
			InfluenceDimension.TRANSFORM_POSITION, PathKind.OFFSET_Y, CurveKind.LINEAR, 0f, 1f);

		BlockInfluencePreset preset = BlockInfluencePreset.builder("Test", "Test")
			.durationSeconds(0.5f)
			.channel(scale)
			.channel(disabled)
			.channel(offset)
			.build();

		List<ChannelSpec> scaleChannels = preset.channelsFor(InfluenceDimension.TRANSFORM_SCALE);
		assertEquals(1, scaleChannels.size());
		assertEquals(scale, scaleChannels.getFirst());
		assertTrue(preset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty());
	}

	@Test
	void equalsByIdOnly() {
		BlockInfluencePreset a = BlockInfluencePreset.builder("Pulse", "A").build();
		BlockInfluencePreset b = BlockInfluencePreset.builder("Pulse", "B").build();
		BlockInfluencePreset other = BlockInfluencePreset.builder("Other", "Other").build();

		assertEquals(a, b);
		assertNotEquals(a, other);
		assertEquals(a.hashCode(), b.hashCode());
	}
}
