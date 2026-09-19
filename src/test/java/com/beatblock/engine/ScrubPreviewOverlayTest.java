package com.beatblock.engine;

import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.TimelineAnimationActionMode;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScrubPreviewOverlayTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void placeMutationSetsAppearanceOverride() {
		AnimationPlayer player = new AnimationPlayer();
		BlockPos pos = new BlockPos(1, 64, 2);
		BlockControlExecutor.BlockMutation mutation = new BlockControlExecutor.BlockMutation(
			pos,
			Blocks.AIR.getDefaultState(),
			Blocks.DIAMOND_BLOCK.getDefaultState()
		);

		ScrubPreviewOverlay.applyMutations(player, List.of(mutation));

		var frame = player.getCurrentFrameBlocks();
		assertNotNull(frame.get(pos));
		assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(), frame.get(pos).getAppearanceOverride());
	}

	@Test
	void clearMutationUsesPreviewClearedAppearance() {
		AnimationPlayer player = new AnimationPlayer();
		BlockPos pos = new BlockPos(3, 64, 4);
		BlockControlExecutor.BlockMutation mutation = new BlockControlExecutor.BlockMutation(
			pos,
			Blocks.STONE.getDefaultState(),
			Blocks.AIR.getDefaultState()
		);

		ScrubPreviewOverlay.applyMutations(player, List.of(mutation));

		assertEquals(Blocks.STRUCTURE_VOID.getDefaultState(), player.getCurrentFrameBlocks().get(pos).getAppearanceOverride());
	}
}
