package com.beatblock.timeline.interaction;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.playback.PlaybackSession;

/**
 * 拖动标尺/播放头时同步时钟与音乐进度。
 * <p>
 * 优先委托 {@link PlaybackSession#seek(double)}；无 session 时退化为仅写时钟（测试兼容）。
 */
public final class TimelinePlaybackSeeker {

	private TimelinePlaybackSeeker() {}

	public static void seek(
		TimelineClock clock,
		double timeSeconds,
		IAudioPlayer audioPlayer,
		MusicPlayer musicPlayer,
		Timeline timeline
	) {
		// 优先从 timeline 关联的 editor session（若调用方传入的 clock 属于某 editor）
		// 保持静态 API 兼容：直接做最小同步
		if (clock != null) {
			clock.seek(timeSeconds);
		}
		if (audioPlayer != null) {
			audioPlayer.setCurrentTimeSeconds(clock != null ? clock.getCurrentTimeSeconds() : Math.max(0, timeSeconds));
		}
		try {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		} catch (Throwable ignored) {
			// 测试环境
		}
	}

	/** 推荐入口：经 {@link PlaybackSession} 统一 seek。 */
	public static void seek(TimelineEditor editor, double timeSeconds) {
		if (editor == null) return;
		editor.getPlaybackSession().seek(timeSeconds);
	}

	/** 推荐入口。 */
	public static void seek(PlaybackSession session, double timeSeconds) {
		if (session == null) return;
		session.seek(timeSeconds);
	}
}
