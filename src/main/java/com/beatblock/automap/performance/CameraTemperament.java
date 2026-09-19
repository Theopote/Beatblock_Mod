package com.beatblock.automap.performance;

/**
 * 镜头气质：在 cameraEnabled 之上控制运动类型与节奏。
 */
public enum CameraTemperament {
	OFF,
	/** 能量镜头：保留 SHAKE / 快切倾向。 */
	ENERGETIC,
	/** 均衡：保留多数运动，略减 SHAKE。 */
	BALANCED,
	/** 慢推 / Hold / Pan，去掉 SHAKE。 */
	SLOW_CINEMATIC;

	public boolean isEnabled() {
		return this != OFF;
	}
}
