package com.beatblock.engine.influence;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VfxTriggerTest {

	@Test
	void recordStoresAllFields() {
		BlockPos pos = new BlockPos(1, 64, 2);
		VfxTrigger trigger = new VfxTrigger("pulse", pos, 3.5, 0.8f);
		assertEquals("pulse", trigger.kind());
		assertEquals(pos, trigger.blockPos());
		assertEquals(3.5, trigger.timeSeconds(), 1e-9);
		assertEquals(0.8f, trigger.intensity(), 1e-6f);
	}
}
