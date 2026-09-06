package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Undoable insert of a {@link EventType#CAMERA_KEYFRAME} onto an existing camera clip.
 */
public final class AddCameraKeyframeCommand implements Command {

	private final Timeline timeline;
	private final String clipId;
	private final double timeSeconds;
	private final Map<String, Object> parameters;
	private @Nullable String eventId;
	private boolean done;

	public AddCameraKeyframeCommand(
		Timeline timeline,
		String clipId,
		double timeSeconds,
		Map<String, Object> parameters
	) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.clipId = Objects.requireNonNull(clipId, "clipId");
		this.timeSeconds = timeSeconds;
		this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
	}

	public @Nullable String createdEventId() {
		return done ? eventId : null;
	}

	public boolean wasApplied() {
		return done && eventId != null;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) {
			return;
		}
		Clip clip = track.getClip(clipId);
		if (clip == null) {
			return;
		}
		TimelineEvent event = TimelineOperations.addEvent(
			clip, timeSeconds, EventType.CAMERA_KEYFRAME, parameters);
		if (event == null) {
			return;
		}
		eventId = event.getId();
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), clip.getEndTimeSeconds()));
		done = true;
	}

	@Override
	public void undo() {
		if (!done || eventId == null) {
			return;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track != null) {
			Clip clip = track.getClip(clipId);
			if (clip != null) {
				TimelineOperations.removeEvent(clip, eventId);
			}
		}
		eventId = null;
		done = false;
	}
}
