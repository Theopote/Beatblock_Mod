package com.beatblock.selection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 两点之间的体素线段：沿中心连线以固定步长采样，保证穿过格子的连续性（避免纯线性插值漏格）。
 * 线粗细 &gt; 0 时：以两端方块中心连线为轴，按欧氏距离 ≤ 半径的圆柱体内的所有整数格。
 */
public final class BlockSelectionLine {

	private static final double STEP = 0.25;

	private BlockSelectionLine() {}

	/**
	 * @param radiusBlocks 0 表示仅中心折线路径；&gt;0 表示圆柱半径（格），与方块中心到线段距离比较
	 * @param maxBlocks    候选格数超过此值时返回 null
	 */
	public static List<BlockPos> blocksForSegment(BlockPos a, BlockPos b, int radiusBlocks, int maxBlocks) {
		if (maxBlocks < 1) {
			return List.of();
		}
		if (radiusBlocks <= 0) {
			List<BlockPos> thin = between(a, b);
			if (thin.size() > maxBlocks) {
				return null;
			}
			return thin;
		}
		Vec3d ca = Vec3d.ofCenter(a);
		Vec3d cb = Vec3d.ofCenter(b);
		int pad = radiusBlocks + 2;
		int x0 = Math.min(a.getX(), b.getX()) - pad;
		int x1 = Math.max(a.getX(), b.getX()) + pad;
		int y0 = Math.min(a.getY(), b.getY()) - pad;
		int y1 = Math.max(a.getY(), b.getY()) + pad;
		int z0 = Math.min(a.getZ(), b.getZ()) - pad;
		int z1 = Math.max(a.getZ(), b.getZ()) + pad;
		double rsq = (double) radiusBlocks * radiusBlocks;
		List<BlockPos> out = new ArrayList<>();
		BlockPos.Mutable mut = new BlockPos.Mutable();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					mut.set(x, y, z);
					Vec3d p = Vec3d.ofCenter(mut);
					if (distSqPointToSegment(p, ca, cb) <= rsq + 1e-10) {
						out.add(mut.toImmutable());
						if (out.size() > maxBlocks) {
							return null;
						}
					}
				}
			}
		}
		return out;
	}

	private static double distSqPointToSegment(Vec3d p, Vec3d segA, Vec3d segB) {
		double abx = segB.x - segA.x;
		double aby = segB.y - segA.y;
		double abz = segB.z - segA.z;
		double apx = p.x - segA.x;
		double apy = p.y - segA.y;
		double apz = p.z - segA.z;
		double abLenSq = abx * abx + aby * aby + abz * abz;
		if (abLenSq < 1e-18) {
			return apx * apx + apy * apy + apz * apz;
		}
		double t = (apx * abx + apy * aby + apz * abz) / abLenSq;
		t = Math.max(0.0, Math.min(1.0, t));
		double qx = segA.x + t * abx - p.x;
		double qy = segA.y + t * aby - p.y;
		double qz = segA.z + t * abz - p.z;
		return qx * qx + qy * qy + qz * qz;
	}

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
