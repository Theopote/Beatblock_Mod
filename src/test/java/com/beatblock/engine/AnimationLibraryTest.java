package com.beatblock.engine;

import com.beatblock.ui.animation.AnimationLibraryItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLibraryTest {

	@Test
	void registersBuiltInPresets() {
		AnimationLibrary library = new AnimationLibrary();
		assertTrue(library.getAll().size() >= 10);
		assertNotNull(library.get("Pulse"));
		assertNotNull(library.get("BlockJump"));
	}

	@Test
	void customDefinitionCanBeRegistered() {
		AnimationLibrary library = new AnimationLibrary();
		var preset = com.beatblock.engine.influence.BlockInfluencePresets.get("Pulse");
		library.register(new AnimationDefinition(preset));
		assertEquals("Pulse", library.get("Pulse").getId());
	}

	@Test
	void primaryDimensionUsesFirstEnabledChannel() {
		AnimationLibrary library = new AnimationLibrary();
		AnimationDefinition pulse = library.get("Pulse");
		assertNotNull(pulse);
		assertNotNull(pulse.getPrimaryDimension());
		assertEquals(pulse.getPrimaryDimension(), new AnimationLibraryItem(pulse).primaryDimension());
	}

	@Test
	void getReturnsNullForUnknownId() {
		AnimationLibrary library = new AnimationLibrary();
		assertNull(library.get("NotARealAnimation"));
	}
}
