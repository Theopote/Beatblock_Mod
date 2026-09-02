package com.beatblock.automap.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraFramingEngineTest {

	@Test
	void fullFramingUsesLargerDistanceForLargerStage() {
		StageBounds small = StageBounds.fromBlocks(List.of(new net.minecraft.util.math.BlockPos(0, 64, 0)));
		StageBounds large = StageBounds.fromBlocks(List.of(
			new net.minecraft.util.math.BlockPos(0, 64, 0),
			new net.minecraft.util.math.BlockPos(29, 81, 24)
		));

		CameraFramingSolution smallShot = CameraFramingEngine.solve(CameraShotFraming.MEDIUM, small);
		CameraFramingSolution largeShot = CameraFramingEngine.solve(CameraShotFraming.MEDIUM, large);

		assertTrue(largeShot.horizontalDistance() > smallShot.horizontalDistance());
		assertTrue(largeShot.dollyReachBlocks() > smallShot.dollyReachBlocks());
	}

	@Test
	void closeFramingIsCloserThanWideForSameStage() {
		StageBounds stage = StageBounds.fromBlocks(List.of(
			new net.minecraft.util.math.BlockPos(0, 64, 0),
			new net.minecraft.util.math.BlockPos(19, 79, 19)
		));

		CameraFramingSolution close = CameraFramingEngine.solve(CameraShotFraming.CLOSE, stage);
		CameraFramingSolution wide = CameraFramingEngine.solve(CameraShotFraming.WIDE, stage);

		assertTrue(close.horizontalDistance() < wide.horizontalDistance());
	}

	@Test
	void overviewIsFarthestForSameStage() {
		StageBounds stage = StageBounds.fromBlocks(List.of(
			new net.minecraft.util.math.BlockPos(0, 64, 0),
			new net.minecraft.util.math.BlockPos(9, 74, 9)
		));

		CameraFramingSolution medium = CameraFramingEngine.solve(CameraShotFraming.MEDIUM, stage);
		CameraFramingSolution overview = CameraFramingEngine.solve(CameraShotFraming.OVERVIEW, stage);

		assertTrue(overview.horizontalDistance() > medium.horizontalDistance());
	}
}
