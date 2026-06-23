package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationLibrary;
import com.beatblock.engine.AnimationPlayer;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockInfluenceOrchestratorTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void applyFrameForwardsMutationsToSink() {
		BlockInfluenceOrchestrator orchestrator = new BlockInfluenceOrchestrator();
		InfluenceFrame frame = new InfluenceFrame();
		BlockPos pos = new BlockPos(0, 64, 0);
		var mutation = new BlockControlExecutor.BlockMutation(
			pos, Blocks.STONE.getDefaultState(), Blocks.AIR.getDefaultState());
		frame.addWorldMutation(mutation);

		List<List<BlockControlExecutor.BlockMutation>> batches = new ArrayList<>();
		WorldMutationSink sink = batches::add;
		orchestrator.applyFrame(frame, null, sink);

		assertEquals(1, batches.size());
		assertEquals(1, batches.getFirst().size());
		assertEquals(pos, batches.getFirst().getFirst().pos());
	}

	@Test
	void tickWithNoActiveAnimationsReturnsZeroMutations() {
		BlockInfluenceOrchestrator orchestrator = new BlockInfluenceOrchestrator();
		AnimationPlayer player = new AnimationPlayer();
		int count = orchestrator.tick(1.0, player, null, null, WorldMutationSink.NO_OP);
		assertEquals(0, count);
		assertTrue(orchestrator.getLastFrame().getWorldMutations().isEmpty());
	}
}
