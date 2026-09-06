package com.beatblock.timeline.command;

import com.beatblock.automap.vfx.GlobalEventCreationRequest;
import com.beatblock.automap.vfx.GlobalEventTimelineWriter;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Undoable global-track insert: typed payload → clip + GLOBAL event. */
public final class CreateGlobalEventCommand implements Command {

	private final Timeline timeline;
	private final GlobalEventCreationRequest request;
	private final TimelineGenerationMetadata metadata;
	private @Nullable String createdClipId;
	private @Nullable String createdEventId;
	private double previousDurationSeconds;
	private boolean done;

	public CreateGlobalEventCommand(
		Timeline timeline,
		GlobalEventCreationRequest request,
		TimelineGenerationMetadata metadata
	) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.request = Objects.requireNonNull(request, "request");
		this.metadata = metadata != null ? metadata : TimelineGenerationMetadata.manual();
	}

	public @Nullable String createdClipId() {
		return done ? createdClipId : null;
	}

	public @Nullable String createdEventId() {
		return done ? createdEventId : null;
	}

	public boolean wasApplied() {
		return done && createdClipId != null && createdEventId != null;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		previousDurationSeconds = timeline.getDurationSeconds();
		var result = GlobalEventTimelineWriter.write(timeline, request, metadata);
		if (!result.written()) {
			return;
		}
		createdClipId = result.clipId();
		createdEventId = result.eventId();
		done = true;
	}

	@Override
	public void undo() {
		if (!done || createdClipId == null) {
			return;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (track != null) {
			track.removeClip(createdClipId);
		}
		timeline.setDurationSeconds(previousDurationSeconds);
		createdClipId = null;
		createdEventId = null;
		done = false;
	}
}
