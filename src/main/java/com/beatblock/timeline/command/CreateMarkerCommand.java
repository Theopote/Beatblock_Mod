package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/** Undoable marker insert. */
public final class CreateMarkerCommand implements Command {

	private final Timeline timeline;
	private final TimelineMarker marker;
	private boolean done;

	public CreateMarkerCommand(@NonNull Timeline timeline, @NonNull TimelineMarker marker) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.marker = Objects.requireNonNull(marker, "marker");
	}

	public TimelineMarker marker() {
		return marker;
	}

	public boolean wasApplied() {
		return done;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		if (timeline.findMarkerIndexById(marker.getId()) >= 0) {
			done = timeline.replaceMarker(marker);
		} else {
			done = timeline.addMarker(marker);
		}
	}

	@Override
	public void undo() {
		if (!done) {
			return;
		}
		timeline.removeMarker(marker.getId());
		done = false;
	}
}
