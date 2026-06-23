package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationDefinitionTest {

	@Test
	void mirrorsPresetMetadata() {
		var preset = BlockInfluencePresets.get("Pulse");
		AnimationDefinition def = new AnimationDefinition(preset);

		assertEquals("Pulse", def.getId());
		assertEquals(preset.getDisplayName(), def.getName());
		assertEquals(preset.getDefaultDurationSeconds(), def.getDurationSeconds(), 1e-6f);
		assertSame(preset, def.getPreset());
	}

	@Test
	void rejectsNullPreset() {
		assertThrows(IllegalArgumentException.class, () -> new AnimationDefinition(null));
	}
}
