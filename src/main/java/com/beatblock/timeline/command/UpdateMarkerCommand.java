package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/** Undoable marker field replace (name / time / type / editState). */
public final class UpdateMarkerCommand implements Command {

	private final Timeline timeline;
	private final TimelineMarker before;
	private final TimelineMarker after;
	private boolean done;

	public UpdateMarkerCommand(
		@NonNull Timeline timeline,
		@NonNull TimelineMarker before,
		@NonNull TimelineMarker after
	) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.before = Objects.requireNonNull(before, "before");
		this.after = Objects.requireNonNull(after, "after");
		if (!before.getId().equals(after.getId())) {
			throw new IllegalArgumentException("before/after marker id mismatch");
		}
	}

	public TimelineMarker before() {
		return before;
	}

	public TimelineMarker after() {
		return after;
	}

	@Override
	public void execute() {
		timeline.replaceMarker(after);
		done = true;
	}

	@Override
	public void undo() {
		if (!done) {
			return;
		}
		timeline.replaceMarker(before);
		done = false;
	}
}
