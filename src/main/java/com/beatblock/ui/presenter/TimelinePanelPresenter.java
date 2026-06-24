package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.util.MusicTimeFormatter;

/**
 * 时间线面板视图状态：时长解析与播放位置显示。
 */
public final class TimelinePanelPresenter {

	public static final double DEFAULT_DURATION_SECONDS = 60.0;

	public record TimelinePanelViewState(
		boolean timelineLoaded,
		boolean editorReady,
		String positionDisplay,
		double durationSeconds,
		double currentTimeSeconds,
		double bpm
	) {}

	public TimelinePanelViewState viewState(
		Timeline timeline,
		TimelineEditor editor,
		double musicPlayerDurationSeconds
	) {
		if (timeline == null) {
			return new TimelinePanelViewState(false, false, "", 0, 0, 0);
		}
		double duration = resolveDurationSeconds(timeline, musicPlayerDurationSeconds);
		double currentTime = editor != null ? editor.getClock().getCurrentTimeSeconds() : 0.0;
		double bpm = timeline.getBpm();
		String positionDisplay = MusicTimeFormatter.formatPositionDisplay(currentTime, duration, bpm);
		return new TimelinePanelViewState(
			true,
			editor != null,
			positionDisplay,
			duration,
			currentTime,
			bpm
		);
	}

	public static double resolveDurationSeconds(Timeline timeline, double musicPlayerDurationSeconds) {
		if (timeline != null && timeline.getDurationSeconds() > 0) {
			return timeline.getDurationSeconds();
		}
		if (musicPlayerDurationSeconds > 0) {
			return musicPlayerDurationSeconds;
		}
		return DEFAULT_DURATION_SECONDS;
	}
}
