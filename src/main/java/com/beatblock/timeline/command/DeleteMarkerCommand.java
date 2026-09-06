package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Undoable marker delete. */
public final class DeleteMarkerCommand implements AppliedCommand {

	private final Timeline timeline;
	private final TimelineMarker removed;
	private boolean done;

	public DeleteMarkerCommand(@NonNull Timeline timeline, @NonNull TimelineMarker removed) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.removed = Objects.requireNonNull(removed, "removed");
	}

	public static @Nullable DeleteMarkerCommand of(@Nullable Timeline timeline, @Nullable String markerId) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return null;
		}
		int index = timeline.findMarkerIndexById(markerId);
		if (index < 0) {
			return null;
		}
		return new DeleteMarkerCommand(timeline, timeline.getMarkers().get(index));
	}

	public TimelineMarker removed() {
		return removed;
	}

	public boolean wasApplied() {
		return done;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		done = timeline.removeMarker(removed.getId());
	}

	@Override
	public void undo() {
		if (!done) {
			return;
		}
		if (timeline.findMarkerIndexById(removed.getId()) < 0) {
			timeline.addMarker(removed);
		} else {
			timeline.replaceMarker(removed);
		}
		done = false;
	}
}
