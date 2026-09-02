package com.beatblock.automap.camera;

import com.beatblock.engine.RuntimeStageObject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 舞台对象在世界空间中的轴对齐包围盒与尺寸。 */
public record StageBounds(
	Vec3d center,
	Vec3d min,
	Vec3d max,
	double width,
	double height,
	double depth,
	double boundingRadius
) {
	private static final double MIN_EXTENT = 1.0;

	public StageBounds {
		width = Math.max(MIN_EXTENT, width);
		height = Math.max(MIN_EXTENT, height);
		depth = Math.max(MIN_EXTENT, depth);
		boundingRadius = Math.max(MIN_EXTENT * 0.5, boundingRadius);
	}

	public static StageBounds fromStageObject(@Nullable RuntimeStageObject object) {
		if (object == null) return unitAt(Vec3d.ZERO);
		return fromBlocks(object.getBlocks(), object.getCenter());
	}

	public static StageBounds fromBlocks(@Nullable List<BlockPos> blocks) {
		return fromBlocks(blocks, null);
	}

	public static StageBounds fromBlocks(@Nullable List<BlockPos> blocks, @Nullable Vec3d fallbackCenter) {
		if (blocks == null || blocks.isEmpty()) {
			return unitAt(fallbackCenter != null ? fallbackCenter : Vec3d.ZERO);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : blocks) {
			if (pos == null) continue;
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX() + 1);
			maxY = Math.max(maxY, pos.getY() + 1);
			maxZ = Math.max(maxZ, pos.getZ() + 1);
		}
		if (minX == Integer.MAX_VALUE) {
			return unitAt(fallbackCenter != null ? fallbackCenter : Vec3d.ZERO);
		}
		Vec3d minCorner = new Vec3d(minX, minY, minZ);
		Vec3d maxCorner = new Vec3d(maxX, maxY, maxZ);
		double width = maxX - minX;
		double height = maxY - minY;
		double depth = maxZ - minZ;
		Vec3d center = new Vec3d(
			(minX + maxX) * 0.5,
			(minY + maxY) * 0.5,
			(minZ + maxZ) * 0.5
		);
		double radius = Math.max(width, Math.max(height, depth)) * 0.5;
		return new StageBounds(center, minCorner, maxCorner, width, height, depth, radius);
	}

	public static StageBounds union(@Nullable Collection<StageBounds> bounds) {
		if (bounds == null || bounds.isEmpty()) {
			return unitAt(Vec3d.ZERO);
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		int count = 0;
		for (StageBounds bound : bounds) {
			if (bound == null) continue;
			count++;
			minX = Math.min(minX, bound.min().x);
			minY = Math.min(minY, bound.min().y);
			minZ = Math.min(minZ, bound.min().z);
			maxX = Math.max(maxX, bound.max().x);
			maxY = Math.max(maxY, bound.max().y);
			maxZ = Math.max(maxZ, bound.max().z);
		}
		if (count == 0) {
			return unitAt(Vec3d.ZERO);
		}
		Vec3d minCorner = new Vec3d(minX, minY, minZ);
		Vec3d maxCorner = new Vec3d(maxX, maxY, maxZ);
		double width = maxX - minX;
		double height = maxY - minY;
		double depth = maxZ - minZ;
		Vec3d center = new Vec3d(
			(minX + maxX) * 0.5,
			(minY + maxY) * 0.5,
			(minZ + maxZ) * 0.5
		);
		double radius = Math.max(width, Math.max(height, depth)) * 0.5;
		return new StageBounds(center, minCorner, maxCorner, width, height, depth, radius);
	}

	public static StageBounds unitAt(Vec3d center) {
		Vec3d c = center != null ? center : Vec3d.ZERO;
		Vec3d min = c.add(-0.5, -0.5, -0.5);
		Vec3d max = c.add(0.5, 0.5, 0.5);
		return new StageBounds(c, min, max, 1.0, 1.0, 1.0, 0.5);
	}
}
