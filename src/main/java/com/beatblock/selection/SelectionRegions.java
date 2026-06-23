package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 选区几何：框选体素枚举与体积检查（不含 World 过滤）。
 */
public final class SelectionRegions {

	private SelectionRegions() {}

	public static long cuboidVolume(BlockPos a, BlockPos b) {
		if (a == null || b == null) return 0L;
		int x0 = Math.min(a.getX(), b.getX());
		int x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ());
		int z1 = Math.max(a.getZ(), b.getZ());
		long dx = (long) x1 - x0 + 1L;
		long dy = (long) y1 - y0 + 1L;
		long dz = (long) z1 - z0 + 1L;
		return dx * dy * dz;
	}

	/**
	 * @return 体素列表；若体积超过 {@code maxBlocks} 则返回 null
	 */
	public static List<BlockPos> cuboidPositions(BlockPos a, BlockPos b, int maxBlocks) {
		long volume = cuboidVolume(a, b);
		if (volume <= 0) return List.of();
		if (volume > maxBlocks) return null;

		int x0 = Math.min(a.getX(), b.getX());
		int x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ());
		int z1 = Math.max(a.getZ(), b.getZ());

		List<BlockPos> out = new ArrayList<>((int) volume);
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					out.add(new BlockPos(x, y, z));
				}
			}
		}
		return out;
	}

	public static boolean containsInBounds(BlockPos pos, BlockPos min, BlockPos max) {
		if (pos == null || min == null || max == null) return false;
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
			&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
			&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}
}
