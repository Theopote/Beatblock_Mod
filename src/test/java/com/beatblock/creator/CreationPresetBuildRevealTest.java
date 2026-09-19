package com.beatblock.creator;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreationPresetBuildRevealTest {

	@Test
	void buildRevealSettingsCarryBuildLayerId() {
		AutoMapSettings settings = CreationPreset.BUILD_REVEAL.buildAutoMapSettings("stage-1", "layer-1");

		assertNotNull(settings);
		assertEquals(AutoMapStyle.CINEMATIC, settings.getStyle());
		assertTrue(settings.isCameraEnabled());
		assertFalse(settings.isParticlesEnabled());
		assertEquals("layer-1", settings.getBuildLayerId());
		assertTrue(settings.hasBuildLayerId());
		assertEquals(List.of("stage-1"), settings.getTargetObjectIds());
	}

	@Test
	void buildRevealWithoutLayerIdDoesNotArmBuildSequence() {
		AutoMapSettings settings = CreationPreset.BUILD_REVEAL.buildAutoMapSettings("stage-1");

		assertNotNull(settings);
		assertNull(settings.getBuildLayerId());
		assertFalse(settings.hasBuildLayerId());
	}

	@Test
	void rhythmPulseIgnoresBuildLayerId() {
		AutoMapSettings settings = CreationPreset.RHYTHM_PULSE.buildAutoMapSettings("stage-1", "layer-1");

		assertNotNull(settings);
		assertNull(settings.getBuildLayerId());
	}
}
