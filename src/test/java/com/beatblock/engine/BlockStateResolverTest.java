package com.beatblock.engine;

import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockStateResolverTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void placementStateResolvesBlockIdOrFallsBack() {
		assertEquals(Blocks.GOLD_BLOCK.getDefaultState(),
			BlockStateResolver.placementState(Map.of("placeBlockId", "minecraft:gold_block")));
		assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(),
			BlockStateResolver.placementState(Map.of()));
		assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(),
			BlockStateResolver.placementState(Map.of("placeBlockId", "minecraft:not_a_real_block")));
	}

	@Test
	void flashStateUsesFlashKeys() {
		assertEquals(Blocks.GOLD_BLOCK.getDefaultState(),
			BlockStateResolver.flashState(Map.of("flashBlock", "minecraft:gold_block")));
	}
}
