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

	/** Apply {@link CameraShotAngle} azimuth / pitch / height (Creator Angle intent). */
	public CameraFramingSolution withAngle(CameraShotAngle angle) {
		CameraShotAngle resolved = angle != null ? angle : CameraShotAngle.FRONT;
		return new CameraFramingSolution(
			lookAt,
			horizontalDistance * resolved.distanceScale(),
			eyeHeightAboveLookAt * resolved.heightScale(),
			resolved.azimuthDeg(),
			resolved.resolvePitchDeg(pitchDeg),
			fovDeg,
			dollyReachBlocks
		);
	}

	/** Eye offset using {@link #yawDeg()} as azimuth from +Z (legacy south = 0°). */
	public Vec3d eyePosition() {
		return eyePosition(1.0, 1.0);
	}

	public Vec3d eyePosition(double distanceScale, double heightScale) {
		double dist = horizontalDistance * distanceScale;
		double height = eyeHeightAboveLookAt * heightScale;
		double rad = Math.toRadians(yawDeg);
		return new Vec3d(
			lookAt.x + dist * Math.sin(rad),
			lookAt.y + height,
			lookAt.z + dist * Math.cos(rad)
		);
	}

	/** Yaw facing look-at from {@link #eyePosition()}. */
	public double facingYawDeg() {
		double rad = Math.toRadians(yawDeg);
		double dx = -Math.sin(rad);
		double dz = -Math.cos(rad);
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** @deprecated use {@link #eyePosition()} */
	@Deprecated
	public Vec3d eyePositionSouth() {
		return eyePosition();
	}

	/** @deprecated use {@link #eyePosition(double, double)} */
	@Deprecated
	public Vec3d eyePositionSouth(double distanceScale, double heightScale) {
		return eyePosition(distanceScale, heightScale);
	}
}
