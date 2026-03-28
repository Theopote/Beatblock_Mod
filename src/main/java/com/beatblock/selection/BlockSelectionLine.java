package com.beatblock.selection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 两点之间的体素线段：沿中心连线以固定步长采样，保证穿过格子的连续性（避免纯线性插值漏格）。
 */
public final class BlockSelectionLine {

	private static final double STEP = 0.25;

	private BlockSelectionLine() {}

	public static List<BlockPos> between(BlockPos a, BlockPos b) {
		Vec3d start = Vec3d.ofCenter(a);
		Vec3d end = Vec3d.ofCenter(b);
		Vec3d ray = end.subtract(start);
		double length = ray.length();
		List<BlockPos> out = new ArrayList<>();
		if (length < 1e-9) {
			out.add(a.toImmutable());
			return out;
		}
		Vec3d dir = ray.multiply(1.0 / length);
		int maxSteps = (int) Math.ceil(length / STEP) + 3;
		BlockPos last = null;
		for (int i = 0; i <= maxSteps; i++) {
			double t = Math.min(i * STEP, length);
			Vec3d p = start.add(dir.multiply(t));
			BlockPos bp = new BlockPos(
					MathHelper.floor(p.x),
					MathHelper.floor(p.y),
					MathHelper.floor(p.z));
			if (last == null || !last.equals(bp)) {
				out.add(bp.toImmutable());
				last = bp;
			}
			if (t >= length) break;
		}
		BlockPos endB = b.toImmutable();
		if (out.isEmpty() || !out.getLast().equals(endB)) {
			out.add(endB);
		}
		return out;
	}
}
