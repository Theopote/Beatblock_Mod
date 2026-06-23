package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;

/**
 * 更新动画事件属性与片段时间范围；支持 Undo/Redo。
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
		if (event == null) return;
		snapshot.applyTo(event, clip);
		timeline.markAnimationEventsDirty(trackId);
	}
}
