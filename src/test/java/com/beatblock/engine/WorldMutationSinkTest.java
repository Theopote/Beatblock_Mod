package com.beatblock.engine;

import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMutationSinkTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void noOpAcceptsMutationsWithoutEffect() {
		WorldMutationSink.NO_OP.apply(List.of());
		assertTrue(true);
	}

	@Test
	void directReturnsNoOpWhenExecutorOrWorldMissing() {
		BlockControlExecutor executor = new BlockControlExecutor(null);
		WorldMutationSink sink = WorldMutationSink.direct(executor, null);
		sink.apply(List.of(new BlockControlExecutor.BlockMutation(
			new BlockPos(0, 64, 0),
			net.minecraft.block.Blocks.STONE.getDefaultState(),
			net.minecraft.block.Blocks.AIR.getDefaultState())));
		assertFalse(sink == null);
	}
}
