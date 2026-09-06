package com.beatblock.timeline.command;

import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard.PasteRequest;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.Timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Duplicates the current event selection (or events inside selected clips) just after the selection span.
 * Does not mutate the user clipboard buffer.
 */
public final class DuplicateTimelineEventsCommand implements Command {

	private static final double MIN_OFFSET_SECONDS = 0.25;

	private final PasteTimelineEventsCommand pasteCommand;
	private boolean applied;

	public DuplicateTimelineEventsCommand(
		@NonNull Timeline timeline,
		@NonNull SelectionState selectionState,
		@Nullable TimelineTrackListState trackListState
	) {
		List<TimelineInteractionClipboard.ClipboardEvent> buffer = new ArrayList<>();
		TimelineInteractionClipboard.copy(buffer, timeline, selectionState);
		double anchor = computeAnchor(buffer);
		this.pasteCommand = new PasteTimelineEventsCommand(new PasteRequest(
			timeline,
			selectionState,
			buffer,
			anchor,
			null,
			null,
			trackListState
		));
	}

	public static boolean canDuplicate(
		@NonNull Timeline timeline,
		@NonNull SelectionState selectionState
	) {
		List<TimelineInteractionClipboard.ClipboardEvent> buffer = new ArrayList<>();
		TimelineInteractionClipboard.copy(buffer, timeline, selectionState);
		return !buffer.isEmpty();
	}

	private static double computeAnchor(List<TimelineInteractionClipboard.ClipboardEvent> buffer) {
		if (buffer.isEmpty()) return 0;
		double first = buffer.getFirst().timeSeconds();
		double last = buffer.getLast().timeSeconds();
		double span = Math.max(MIN_OFFSET_SECONDS, last - first);
		return first + span;
	}

	@Override
	public void execute() {
		if (applied) return;
		pasteCommand.execute();
		applied = pasteCommand.wasApplied();
	}

	@Override
	public void undo() {
		if (!applied) return;
		pasteCommand.undo();
		applied = false;
	}

	public boolean wasApplied() {
		return applied;
	}
}
