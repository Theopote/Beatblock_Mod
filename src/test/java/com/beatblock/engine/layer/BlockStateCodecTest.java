package com.beatblock.engine.layer;

import com.beatblock.testutil.MinecraftTestBootstrap;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockStateCodecTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void roundTripsDefaultBlockState() {
		BlockState stone = Blocks.STONE.getDefaultState();
		JsonObject json = BlockStateCodec.toJson(stone);

		assertEquals("minecraft:stone", json.get("block").getAsString());
		BlockState decoded = BlockStateCodec.fromJson(json);
		assertEquals(stone, decoded);
	}

	@Test
	void fromJsonReturnsNullForMissingBlockField() {
		assertNull(BlockStateCodec.fromJson(new JsonObject()));
		assertNull(BlockStateCodec.fromJsonString("{}"));
	}

	@Test
	void fromJsonStringParsesValidPayload() {
		BlockState state = BlockStateCodec.fromJsonString("{\"block\":\"minecraft:gold_block\"}");
		assertNotNull(state);
		assertEquals(Blocks.GOLD_BLOCK, state.getBlock());
	}
}
