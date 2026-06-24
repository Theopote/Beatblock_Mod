package com.beatblock.selection.collect;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 收集器共用的方块过滤（视角距离、跳过空气）。 */
public final class SelectionCollectSupport {

	private SelectionCollectSupport() {}

	public static List<BlockPos> filterBlocks(
		World world,
		List<BlockPos> raw,
		boolean includeAir,
		Predicate<BlockPos> withinReach
	) {
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		List<BlockPos> out = new ArrayList<>(raw.size());
		for (BlockPos p : raw) {
			if (p == null) continue;
			if (withinReach != null && !withinReach.test(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}
}
