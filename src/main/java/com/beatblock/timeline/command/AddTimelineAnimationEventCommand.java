package com.beatblock.timeline.command;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 通过 Clip + Event 写入 {@link TimelineAnimationEvent}，支持 Undo。
 */
public final class AddTimelineAnimationEventCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final TimelineAnimationEvent animationEvent;
	private @Nullable String clipId;
	private @Nullable String eventId;
	private boolean done;

	public AddTimelineAnimationEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull TimelineAnimationEvent animationEvent
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.animationEvent = animationEvent;
	}

	@Override
	public void execute() {
		if (timeline == null || animationEvent == null || done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = TimelineOperations.addClip(track, animationEvent.getTimeSeconds(), animationEvent.getEndTimeSeconds());
		if (clip == null) return;
		Map<String, Object> params = AnimationEventParams.fromAnimationEvent(animationEvent).toParameterMap();
		var event = TimelineOperations.addEvent(clip, animationEvent.getTimeSeconds(), EventType.ANIMATION, params);
		if (event == null) return;
		clipId = clip.getId();
		eventId = event.getId();
		timeline.markAnimationEventsDirty(trackId);
		done = true;
	}

	@Override
	public void undo() {
		if (!done || timeline == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = clipId != null ? track.getClip(clipId) : null;
		if (clip != null && eventId != null) {
			clip.removeEvent(eventId);
			if (clip.getEvents().isEmpty()) {
				track.removeClip(clipId);
			}
		}
		timeline.markAnimationEventsDirty(trackId);
		done = false;
	}

	static Map<String, Object> buildParams(TimelineAnimationEvent event) {
		return AnimationEventParams.fromAnimationEvent(event).toParameterMap();
	}
}
