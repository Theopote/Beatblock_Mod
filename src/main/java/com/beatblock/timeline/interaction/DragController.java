package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.TimelineViewState;

/**
 * 拖拽逻辑：事件时间更新、可选吸附。
 */
public final class DragController {

	/**
	 * 将指定事件的时间设为 newTimeSeconds（会夹到 [0, duration]）。
	 */
	public static void dragEvent(Timeline timeline, String trackId, String clipId, String eventId, double newTimeSeconds, double duration) {
		if (timeline == null || trackId == null || clipId == null || eventId == null) return;
		double t = Math.max(0, Math.min(newTimeSeconds, duration > 0 ? duration : Double.MAX_VALUE));
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e != null) e.setTimeSeconds(t);
	}
}
