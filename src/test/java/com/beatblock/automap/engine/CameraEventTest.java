package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraEventTest {

	@Test
	void defaultsNullActionToHold() {
		CameraEvent event = new CameraEvent(-1.0, null);
		assertEquals(0.0, event.getTimeSeconds(), 1e-9);
		assertEquals(CameraAction.HOLD, event.getAction());
	}

	@Test
	void preservesProvidedAction() {
		CameraEvent event = new CameraEvent(3.5, CameraAction.ORBIT);
		assertEquals(3.5, event.getTimeSeconds(), 1e-9);
		assertEquals(CameraAction.ORBIT, event.getAction());
	}
}
