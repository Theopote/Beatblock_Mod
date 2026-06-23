package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 选区距离约束：相对相机最大距离、魔棒种子扩散半径。
 */
public final class SelectionReach {

	private SelectionReach() {}

	/**
	 * @param cameraCenter 为 null 时不限制（与 {@link BeatBlockSelectionManager} 一致）
	 */
	public static boolean isWithinCameraReach(BlockPos pos, Vec3d cameraCenter, double maxDistance) {
		if (pos == null) return false;
		if (cameraCenter == null) return true;
		double r = Math.max(0, maxDistance);
		return cameraCenter.squaredDistanceTo(Vec3d.ofCenter(pos)) <= r * r;
	}

	/** @param maxSpread &lt;= 0 时不限制 */
	public static boolean isWithinSpreadFromSeed(BlockPos seed, BlockPos pos, double maxSpread) {
		if (seed == null || pos == null) return false;
		if (maxSpread <= 0) return true;
		return Vec3d.ofCenter(seed).squaredDistanceTo(Vec3d.ofCenter(pos)) <= maxSpread * maxSpread;
	}
}
