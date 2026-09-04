package com.beatblock.automap.camera;

/**
 * {@link CameraContinuityPlanner} 阈值；默认偏保守，避免穿模平滑。
 */
public record CameraContinuityOptions(
	double abuttingGapSeconds,
	double largeGapCutSeconds,
	double softPositionBlocks,
	double hardPositionBlocks,
	double softYawDeg,
	double hardYawDeg,
	double softPitchDeg
) {
	public CameraContinuityOptions {
		abuttingGapSeconds = Math.max(0.0, abuttingGapSeconds);
		largeGapCutSeconds = Math.max(abuttingGapSeconds, largeGapCutSeconds);
		softPositionBlocks = Math.max(0.0, softPositionBlocks);
		hardPositionBlocks = Math.max(softPositionBlocks, hardPositionBlocks);
		softYawDeg = Math.max(0.0, softYawDeg);
		hardYawDeg = Math.max(softYawDeg, hardYawDeg);
		softPitchDeg = Math.max(0.0, softPitchDeg);
	}

	public static CameraContinuityOptions defaults() {
		return new CameraContinuityOptions(
			0.08,
			0.45,
			4.0,
			18.0,
			35.0,
			110.0,
			12.0
		);
	}
}
