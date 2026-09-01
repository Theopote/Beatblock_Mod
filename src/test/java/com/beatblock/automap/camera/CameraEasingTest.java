package com.beatblock.automap.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraEasingTest {

	@Test
	void appliesNamedCurves() {
		assertEquals(0.0, CameraEasing.apply(0.0, CameraShotEasing.LINEAR), 1e-9);
		assertEquals(1.0, CameraEasing.apply(1.0, CameraShotEasing.LINEAR), 1e-9);
		assertTrue(CameraEasing.apply(0.5, CameraShotEasing.EASE_IN) < 0.5);
		assertTrue(CameraEasing.apply(0.5, CameraShotEasing.EASE_OUT) > 0.5);
	}
}
