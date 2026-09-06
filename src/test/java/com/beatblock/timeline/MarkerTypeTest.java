package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerTypeTest {

	@Test
	void fromNameResolvesKnownTypes() {
		assertEquals(MarkerType.DROP, MarkerType.fromName("drop"));
		assertEquals(MarkerType.CAMERA, MarkerType.fromName("CAMERA"));
	}

	@Test
	void fromNameDefaultsToGeneric() {
		assertEquals(MarkerType.GENERIC, MarkerType.fromName(null));
		assertEquals(MarkerType.GENERIC, MarkerType.fromName("unknown"));
	}

	@Test
	void onlySectionIsStructuralOthersAreAnnotation() {
		assertTrue(MarkerType.SECTION.isStructural());
		assertTrue(!MarkerType.SECTION.isAnnotation());
		for (MarkerType type : MarkerType.values()) {
			if (type == MarkerType.SECTION) {
				continue;
			}
			assertTrue(type.isAnnotation(), type.name());
			assertTrue(!type.isStructural(), type.name());
		}
	}
}
