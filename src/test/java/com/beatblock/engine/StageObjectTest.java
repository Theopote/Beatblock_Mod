package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageObjectTest {

	@Test
	void computesCenterFromBlocksWhenNotProvided() {
		StageObject object = new StageObject(
			"s1",
			"Stage",
			List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0)),
			null
		);

		Vec3d center = object.getCenter();
		assertEquals(1.5, center.x, 1e-6);
		assertEquals(64.0, center.y, 1e-6);
		assertEquals(0.5, center.z, 1e-6);
	}

	@Test
	void defaultsEmptyIdAndUsesManualGroupSpec() {
		StageObject object = new StageObject(null, null, List.of(), Vec3d.ZERO);
		assertEquals("", object.getId());
		assertEquals("", object.getName());
		assertEquals(GroupSortingStrategy.SEQUENTIAL, object.getGroupSpec().getSortingStrategy());
	}
}
