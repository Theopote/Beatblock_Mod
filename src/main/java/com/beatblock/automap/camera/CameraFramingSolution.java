package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;

/** 由 {@link CameraFramingEngine} 根据取景意图与舞台尺寸求解出的镜头几何。 */
public record CameraFramingSolution(
	Vec3d lookAt,
	double horizontalDistance,
	double eyeHeightAboveLookAt,
	double yawDeg,
	double pitchDeg,
	double fovDeg,
	double dollyReachBlocks
) {
	public CameraFramingSolution {
		horizontalDistance = Math.max(1.0, horizontalDistance);
		dollyReachBlocks = Math.max(0.5, dollyReachBlocks);
	}

	public double orbitRadiusBlocks() {
		return horizontalDistance;
	}

	public double orbitHeightBlocks() {
		return eyeHeightAboveLookAt;
	}

	/** 默认朝南（+Z）偏移的眼点，与历史 {@code offsetSouth} 一致。 */
	public Vec3d eyePositionSouth() {
		return new Vec3d(
			lookAt.x,
			lookAt.y + eyeHeightAboveLookAt,
			lookAt.z + horizontalDistance
		);
	}

	public Vec3d eyePositionSouth(double distanceScale, double heightScale) {
		return new Vec3d(
			lookAt.x,
			lookAt.y + eyeHeightAboveLookAt * heightScale,
			lookAt.z + horizontalDistance * distanceScale
		);
	}
}
