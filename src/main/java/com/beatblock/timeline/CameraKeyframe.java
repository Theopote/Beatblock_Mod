package com.beatblock.timeline;

/**
 * 摄像机轨道关键帧：仅表示时间点，具体位置/朝向由摄像机系统解析。
 * 视觉/镜头由 CameraTrack 控制，与动画触发解耦。
 */
public final class CameraKeyframe {

	private final double timeSeconds;

	public CameraKeyframe(double timeSeconds) {
		this.timeSeconds = Math.max(0, timeSeconds);
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}
}
