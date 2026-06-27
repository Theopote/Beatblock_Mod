package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockInfluencePresetsTest {

	@Test
	void builtInPulsePresetHasScaleChannel() {
		BlockInfluencePreset pulse = BlockInfluencePresets.get("Pulse");
		assertNotNull(pulse);
		assertEquals("Pulse", pulse.getId());
		assertFalse(pulse.channelsFor(InfluenceDimension.TRANSFORM_SCALE).isEmpty());
	}

	@Test
	void blockTapIncludesAppearanceChannel() {
		BlockInfluencePreset tap = BlockInfluencePresets.get("BlockTap");
		assertNotNull(tap);
		assertFalse(tap.channelsFor(InfluenceDimension.APPEARANCE).isEmpty());
		assertEquals(0.35f, tap.getDefaultDurationSeconds(), 1e-6);
	}

	@Test
	void rhythmDropUsesWorldTrajectoryAndImpactVfx() {
		BlockInfluencePreset drop = BlockInfluencePresets.get("RhythmDrop");
		assertNotNull(drop);
		assertFalse(drop.channelsFor(InfluenceDimension.TRANSFORM_POSITION).isEmpty());
		assertFalse(drop.channelsFor(InfluenceDimension.VFX).isEmpty());
		assertEquals(1.0f, drop.getDefaultDurationSeconds(), 1e-6);
	}
}
