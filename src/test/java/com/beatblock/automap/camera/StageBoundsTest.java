package com.beatblock.automap.camera;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageBoundsTest {

	@Test
	void fromBlocksComputesExtentsFromBlockAabb() {
		StageBounds bounds = StageBounds.fromBlocks(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(29, 81, 24)
		));

		assertEquals(30.0, bounds.width(), 1e-9);
		assertEquals(18.0, bounds.height(), 1e-9);
		assertEquals(25.0, bounds.depth(), 1e-9);
		assertEquals(15.0, bounds.center().x, 1e-9);
		assertEquals(73.0, bounds.center().y, 1e-9);
		assertEquals(12.5, bounds.center().z, 1e-9);
	}

	@Test
	void fromStageObjectUsesRegisteredBlocks() {
		RuntimeStageObject object = StageObjectSystem.fromBlocks(
			"stage-large",
			"Large",
			List.of(new BlockPos(0, 64, 0), new BlockPos(9, 74, 9))
		);

		StageBounds bounds = StageBounds.fromStageObject(object);

		assertEquals(10.0, bounds.width(), 1e-9);
		assertEquals(11.0, bounds.height(), 1e-9);
		assertEquals(10.0, bounds.depth(), 1e-9);
	}
}
