package com.beatblock.testutil;

import net.minecraft.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftTestBootstrapTest {

	@Test
	void bootstrapsRegistriesForBlockAccess() {
		MinecraftTestBootstrap.ensureInitialized();
		assertNotNull(Blocks.AIR.getDefaultState());
		assertTrue(Blocks.STONE.getDefaultState().getBlock() == Blocks.STONE);
	}
}
