package com.beatblock.selection.collect;

import com.beatblock.selection.BlockSelectionLine;
import com.beatblock.selection.SelectionCollectResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 线选：体素折线或圆柱扫掠。 */
public final class LineSelectionCollector {

	private LineSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos endA,
		BlockPos endB,
		int thicknessRadius,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (endA == null || endB == null) {
			return SelectionCollectResult.failure("线选：无效端点。");
		}
		List<BlockPos> raw = BlockSelectionLine.blocksForSegment(endA, endB, thicknessRadius, maxBlocks);
		if (raw == null) {
			return SelectionCollectResult.failure(String.format(
				"线选候选方块超过上限 %d（可缩小线粗细或框选上限）。", maxBlocks
			));
		}
		if (world == null) {
			return SelectionCollectResult.success(raw);
		}
		return SelectionCollectResult.success(
			SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach)
		);
	}
}
