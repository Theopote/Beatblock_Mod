package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionReachTest {

	@Test
	void cameraReachAllowsAllWhenCameraUnset() {
		BlockPos far = new BlockPos(1000, 64, 1000);
		assertTrue(SelectionReach.isWithinCameraReach(far, null, 8));
	}

	@Test
	void cameraReachUsesBlockCenterDistance() {
		Vec3d camera = new Vec3d(0.5, 64.5, 0.5);
		BlockPos nearby = new BlockPos(0, 64, 0);
		BlockPos far = new BlockPos(10, 64, 0);

		assertTrue(SelectionReach.isWithinCameraReach(nearby, camera, 1.0));
		assertFalse(SelectionReach.isWithinCameraReach(far, camera, 1.0));
	}

	@Test
	void wandSpreadLimitUsesSeedCenter() {
		BlockPos seed = new BlockPos(0, 64, 0);
		BlockPos neighbor = new BlockPos(1, 64, 0);
		BlockPos distant = new BlockPos(5, 64, 0);

		assertTrue(SelectionReach.isWithinSpreadFromSeed(seed, neighbor, 1.0));
		assertFalse(SelectionReach.isWithinSpreadFromSeed(seed, distant, 1.0));
	}

	@Test
	void wandSpreadDisabledWhenNonPositive() {
		BlockPos seed = new BlockPos(0, 64, 0);
		BlockPos distant = new BlockPos(100, 64, 0);
		assertTrue(SelectionReach.isWithinSpreadFromSeed(seed, distant, 0));
	}
}
