package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraShotAngleTest {

	@Test
	void sideAnglePlacesEyeAlongPositiveX() {
		CameraFramingSolution base = new CameraFramingSolution(
			new Vec3d(0, 64, 0), 10.0, 3.0, 0.0, -10.0, 70.0, 4.0);
		CameraFramingSolution angled = base.withAngle(CameraShotAngle.SIDE);
		Vec3d eye = angled.eyePosition();

		assertEquals(90.0, angled.yawDeg(), 1e-9);
		assertEquals(10.0, eye.x, 1e-6);
		assertEquals(0.0, eye.z, 1e-6);
		assertEquals(90.0, angled.facingYawDeg(), 1e-6);
	}

	@Test
	void topAngleRaisesPitchAndEye() {
		CameraFramingSolution base = new CameraFramingSolution(
			new Vec3d(0, 64, 0), 10.0, 3.0, 0.0, -10.0, 70.0, 4.0);
		CameraFramingSolution angled = base.withAngle(CameraShotAngle.TOP);

		assertEquals(-55.0, angled.pitchDeg(), 1e-9);
		assertTrue(angled.eyeHeightAboveLookAt() > base.eyeHeightAboveLookAt());
		assertTrue(angled.horizontalDistance() < base.horizontalDistance());
	}
}
