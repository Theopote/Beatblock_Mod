package com.beatblock.automap.camera;

/**
 * 相邻两镜连续性评估指标。
 */
public record CameraContinuityJump(
	double gapSeconds,
	double positionDeltaBlocks,
	double yawDeltaDeg,
	double pitchDeltaDeg,
	boolean subjectChanged,
	boolean oppositeScreenDirection
) {
	public CameraContinuityJump {
		gapSeconds = Math.max(0.0, gapSeconds);
		positionDeltaBlocks = Math.max(0.0, positionDeltaBlocks);
		yawDeltaDeg = Math.max(0.0, Math.min(180.0, yawDeltaDeg));
		pitchDeltaDeg = Math.max(0.0, pitchDeltaDeg);
	}

	public boolean abutting(double maxGapSeconds) {
		return gapSeconds <= Math.max(0.0, maxGapSeconds);
	}
}
