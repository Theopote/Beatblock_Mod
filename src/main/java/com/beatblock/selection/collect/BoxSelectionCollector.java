package com.beatblock.selection.collect;

import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.selection.SelectionRegions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 轴对齐框选：两角点定义的长方体。 */
public final class BoxSelectionCollector {

	private BoxSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos cornerA,
		BlockPos cornerB,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (cornerA == null || cornerB == null) {
			return SelectionCollectResult.failure("框选：无效角点。");
		}
		List<BlockPos> raw = SelectionRegions.cuboidPositions(cornerA, cornerB, maxBlocks);
		if (raw == null) {
			return SelectionCollectResult.failure(String.format(
				"框选体积 %d 超过上限 %d，已取消。",
				SelectionRegions.cuboidVolume(cornerA, cornerB),
				maxBlocks
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
