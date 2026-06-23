package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMapSettingsTest {

	@Test
	void defaultsToEdmMediumWithCameraAndParticles() {
		AutoMapSettings settings = new AutoMapSettings();
		assertEquals(AutoMapStyle.EDM, settings.getStyle());
		assertEquals(Complexity.MEDIUM, settings.getComplexity());
		assertTrue(settings.isCameraEnabled());
		assertTrue(settings.isParticlesEnabled());
		assertTrue(settings.getTargetObjectIds().isEmpty());
	}

	@Test
	void settersDefensivelyCopyTargetIds() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(AutoMapStyle.CINEMATIC);
		settings.setComplexity(Complexity.HIGH);
		settings.setCameraEnabled(false);
		settings.setTargetObjectIds(List.of("stage-a", "stage-b"));

		assertEquals(AutoMapStyle.CINEMATIC, settings.getStyle());
		assertEquals(Complexity.HIGH, settings.getComplexity());
		settings.getTargetObjectIds().clear();
		assertEquals(2, settings.getTargetObjectIds().size());
	}

	@Test
	void nullStyleAndComplexityFallBackToDefaults() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(null);
		settings.setComplexity(null);
		assertEquals(AutoMapStyle.EDM, settings.getStyle());
		assertEquals(Complexity.MEDIUM, settings.getComplexity());
	}
}
