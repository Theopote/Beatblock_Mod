package com.beatblock.automap.camera;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

/**
 * 将导演取景意图（{@link CameraShotFraming}）与 {@link StageBounds} 求解为镜头距离、高度与俯仰。
 */
public final class CameraFramingEngine {

	public static final double DEFAULT_FOV_DEG = 70.0;
	public static final double DEFAULT_ASPECT_RATIO = 16.0 / 9.0;

	private CameraFramingEngine() {}

	public static CameraFramingSolution solve(CameraShotFraming framing, StageBounds bounds) {
		return solve(framing, bounds, DEFAULT_FOV_DEG, DEFAULT_ASPECT_RATIO);
	}

	public static CameraFramingSolution solve(
		@Nullable CameraShotFraming framing,
		@Nullable StageBounds bounds,
		double verticalFovDeg,
		double aspectRatio
	) {
		CameraShotFraming resolved = framing != null ? framing : CameraShotFraming.MEDIUM;
		StageBounds stage = bounds != null ? bounds : StageBounds.unitAt(Vec3d.ZERO);
		double fovV = Math.toRadians(clamp(verticalFovDeg, 35.0, 110.0));
		double aspect = Math.max(1.0, aspectRatio);
		double fovH = 2.0 * Math.atan(Math.tan(fovV / 2.0) * aspect);

		double fillRatio = resolved.verticalFillRatio();
		double margin = resolved.marginMultiplier();
		double subjectHeight = stage.height();
		double horizontalExtent = Math.max(stage.width(), stage.depth());

		double distanceForHeight = subjectHeight / (2.0 * Math.tan(fovV / 2.0) * fillRatio);
		double distanceForWidth = horizontalExtent / (2.0 * Math.tan(fovH / 2.0) * fillRatio);
		double horizontalDistance = Math.max(distanceForHeight, distanceForWidth) * margin;
		horizontalDistance = Math.max(horizontalDistance, resolved.minimumDistanceBlocks());

		double pitchDeg = resolved.defaultPitchDeg();
		double eyeHeight = horizontalDistance * Math.tan(Math.toRadians(Math.abs(pitchDeg)));
		double dollyReach = horizontalDistance * resolved.dollyReachFactor();

		return new CameraFramingSolution(
			stage.center(),
			horizontalDistance,
			eyeHeight,
			0.0,
			pitchDeg,
			verticalFovDeg,
			dollyReach
		);
	}

	/** 无法解析舞台 bounds 时回退到固定表（兼容单测 / 无 StageObject 场景）。 */
	public static CameraFramingSolution fallback(CameraShotFraming framing, Vec3d lookAt) {
		CameraShotFraming resolved = framing != null ? framing : CameraShotFraming.MEDIUM;
		Vec3d target = lookAt != null ? lookAt : Vec3d.ZERO;
		return new CameraFramingSolution(
			target,
			resolved.orbitRadiusBlocks(),
			resolved.orbitHeightBlocks(),
			0.0,
			resolved.defaultPitchDeg(),
			DEFAULT_FOV_DEG,
			resolved.dollyReachBlocks()
		);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
