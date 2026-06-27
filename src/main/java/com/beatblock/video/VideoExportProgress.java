package com.beatblock.video;

import org.jspecify.annotations.Nullable;

/**
 * 视频导出进度快照。
 */
public record VideoExportProgress(
	State state,
	@Nullable VideoExportSettings settings,
	int percent,
	String message,
	int currentFrame,
	int totalFrames
) {
	public enum State {
		STARTING,
		RUNNING,
		FINALIZING,
		SUCCEEDED,
		FAILED,
		CANCELLED
	}

	public static VideoExportProgress starting(VideoExportSettings settings) {
		return new VideoExportProgress(State.STARTING, settings, 0, "", 0, settings.totalFrames());
	}

	public VideoExportProgress withState(State newState, String newMessage, int newPercent) {
		return new VideoExportProgress(newState, settings, newPercent, newMessage, currentFrame, totalFrames);
	}

	public VideoExportProgress withFrameProgress(int frame, String newMessage, int newPercent) {
		return new VideoExportProgress(State.RUNNING, settings, newPercent, newMessage, frame, totalFrames);
	}
}
