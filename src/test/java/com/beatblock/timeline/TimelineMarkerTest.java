package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimelineMarkerTest {

	@Test
	void clampsNegativeTimeAndDefaultsNullFields() {
		TimelineMarker marker = new TimelineMarker(-1.0, null, null);
		assertEquals(0.0, marker.getTimeSeconds(), 1e-9);
		assertEquals("", marker.getName());
		assertEquals(MarkerType.GENERIC, marker.getType());
		assertNotNull(marker.getId());
		assertFalse(marker.getId().isBlank());
	}

	@Test
	void preservesExplicitIdAndType() {
		TimelineMarker marker = new TimelineMarker("mk-1", 3.5, "Drop", MarkerType.DROP);
		assertEquals("mk-1", marker.getId());
		assertEquals(3.5, marker.getTimeSeconds(), 1e-9);
		assertEquals("Drop", marker.getName());
		assertEquals(MarkerType.DROP, marker.getType());
	}
}
