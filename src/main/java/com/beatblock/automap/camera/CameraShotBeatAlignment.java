package com.beatblock.automap.camera;

/**
 * 镜头起始时间与节拍网格的对齐策略。
 */
public record CameraShotBeatAlignment(boolean snapStartToBeat, int beatOffset) {

	public CameraShotBeatAlignment {
		beatOffset = Math.max(0, beatOffset);
	}

	public static CameraShotBeatAlignment onBeat() {
		return new CameraShotBeatAlignment(true, 0);
	}

	public static CameraShotBeatAlignment none() {
		return new CameraShotBeatAlignment(false, 0);
	}
}
