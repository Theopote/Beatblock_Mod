package com.beatblock.automap.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxEffectCategoryTest {

	@Test
	void forKindMapsEveryCreatorKindToOwnTab() {
		for (GlobalEffectKind kind : GlobalEffectKind.values()) {
			VfxEffectCategory category = VfxEffectCategory.forKind(kind);
			assertTrue(category.contains(kind), () -> kind + " -> " + category);
			assertEquals(kind, category.defaultKind());
		}
	}

	@Test
	void environmentLightingAndScreenTintAreSeparateEntries() {
		assertEquals(VfxEffectCategory.ENVIRONMENT_LIGHTING,
			VfxEffectCategory.forKind(GlobalEffectKind.ENVIRONMENT_LIGHTING));
		assertEquals(VfxEffectCategory.SCREEN_TINT,
			VfxEffectCategory.forKind(GlobalEffectKind.SCREEN_TINT));
		assertFalse(VfxEffectCategory.ENVIRONMENT_LIGHTING.contains(GlobalEffectKind.SCREEN_TINT));
		assertFalse(VfxEffectCategory.SCREEN_TINT.contains(GlobalEffectKind.ENVIRONMENT_LIGHTING));
	}
}
