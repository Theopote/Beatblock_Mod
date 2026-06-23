package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;

/**
 * 更新时间线事件属性、片段时间与相关元数据；支持 Undo/Redo。
 */
public final class UpdateAnimationEventCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final String eventId;
	private final AnimationEventSnapshot before;
	private final AnimationEventSnapshot after;

	public UpdateAnimationEventCommand(
		Timeline timeline,
		String trackId,
		String clipId,
		String eventId,
		AnimationEventSnapshot before,
		AnimationEventSnapshot after
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.eventId = eventId;
		this.before = before;
		this.after = after;
	}

	@Override
	public void execute() {
		apply(after);
	}

	@Override
	public void undo() {
		apply(before);
	}

	private void apply(AnimationEventSnapshot snapshot) {
		if (timeline == null || snapshot == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null && !clip.getEvents().isEmpty()) {
			event = clip.getEvents().get(0);
		}
		if (event == null) return;
		snapshot.applyTo(event, clip, timeline);
		if (isAnimationTrack(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.TRACK_ID_ANIMATION_BLOCK.equals(trackId)
			|| Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)
			|| Timeline.TRACK_ID_BUILD_REVERSE.equals(trackId)
			|| Timeline.isBlockAnimationFeatureTrackId(trackId);
	}
}
