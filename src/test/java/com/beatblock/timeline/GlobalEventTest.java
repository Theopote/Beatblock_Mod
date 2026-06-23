package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalEventTest {

	@Test
	void clampsNegativeTimeAndDefaultsNullFields() {
		GlobalEvent event = new GlobalEvent(-2.0, null, null);
		assertEquals(0.0, event.getTimeSeconds(), 1e-9);
		assertEquals(GlobalEventType.SPECIAL, event.getType());
		assertEquals("", event.getName());
	}

	@Test
	void preservesProvidedTypeAndName() {
		GlobalEvent event = new GlobalEvent(4.5, GlobalEventType.LIGHTING, "Strobe");
		assertEquals(4.5, event.getTimeSeconds(), 1e-9);
		assertEquals(GlobalEventType.LIGHTING, event.getType());
		assertEquals("Strobe", event.getName());
	}
}
