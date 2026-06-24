package com.beatblock.selection.collect;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionBrushRegions;
import com.beatblock.selection.SelectionCollectResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 笔刷选区：球体或立方包络。 */
public final class BrushSelectionCollector {

	private BrushSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos center,
		BrushShape shape,
		int radius,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (center == null) {
			return SelectionCollectResult.failure("笔刷：无效中心。");
		}
		BrushShape resolved = shape != null ? shape : BrushShape.SPHERE;
		List<BlockPos> raw = switch (resolved) {
			case SPHERE -> SelectionBrushRegions.spherePositions(center, radius, maxBlocks);
			case CUBE -> SelectionBrushRegions.cubePositions(center, radius, maxBlocks);
		};
		if (raw == null) {
			long worst = (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1);
			String label = resolved == BrushShape.CUBE ? "立方笔刷包络" : "球体包络方块数";
			String hint = resolved == BrushShape.SPHERE ? "，请缩小半径" : "";
			return SelectionCollectResult.failure(String.format("%s约 %d 方块，超过上限 %d%s。", label, worst, maxBlocks, hint));
		}
		if (world == null) {
			return SelectionCollectResult.success(raw);
		}
		return SelectionCollectResult.success(
			SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach)
		);
	}
}
