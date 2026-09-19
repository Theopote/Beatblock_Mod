package com.beatblock.engine;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Scrub / 播放头预览：将 BUILD / PLACE / CLEAR 的 world mutation 计划转为
 * {@link AnimatedBlock} 外观覆盖，供 {@link com.beatblock.client.render.BeatBlockAnimatedBlocksRenderer}
 * 绘制，而不写入 Minecraft 世界。
 */
public final class ScrubPreviewOverlay {

	private ScrubPreviewOverlay() {}

	public static void applyMutations(AnimationPlayer animationPlayer, List<BlockControlExecutor.BlockMutation> mutations) {
		if (animationPlayer == null || mutations == null || mutations.isEmpty()) {
			return;
		}
		Map<BlockPos, AnimatedBlock> frame = animationPlayer.getCurrentFrameBlocks();
		for (BlockControlExecutor.BlockMutation mutation : mutations) {
			if (mutation == null || mutation.pos() == null || mutation.toState() == null) {
				continue;
			}
			BlockPos pos = mutation.pos().toImmutable();
			BlockState toState = mutation.toState();
			AnimatedBlock block = frame.computeIfAbsent(pos, AnimatedBlock::new);
			block.resetToOriginal();
			if (toState.isAir()) {
				block.setAppearanceOverride(previewClearedAppearance());
			} else {
				block.setAppearanceOverride(toState);
			}
		}
	}

	/** 预览 CLEAR：用结构空位提示“此处应被清空”，避免真实 world mutation。 */
	private static BlockState previewClearedAppearance() {
		return Blocks.STRUCTURE_VOID.getDefaultState();
	}
}
