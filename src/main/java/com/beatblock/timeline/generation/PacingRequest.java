package com.beatblock.timeline.generation;

import java.util.ArrayList;
import java.util.List;

/**
 * 节拍来源：参考轨显式节拍点，或固定 BPM 间隔 fallback。
 */
public record PacingRequest(
	int slotCount,
	double anchorTimeSeconds,
	boolean startImmediately,
	double[] referenceBeatTimesSeconds,
	double bpm,
	double fixedIntervalSeconds
) {
	public PacingRequest {
		slotCount = Math.max(0, slotCount);
		anchorTimeSeconds = Math.max(0.0, anchorTimeSeconds);
		bpm = Math.max(1.0, bpm);
		fixedIntervalSeconds = fixedIntervalSeconds > 0
			? fixedIntervalSeconds
			: 60.0 / bpm;
		referenceBeatTimesSeconds = referenceBeatTimesSeconds != null
			? referenceBeatTimesSeconds.clone()
			: new double[0];
	}
}
