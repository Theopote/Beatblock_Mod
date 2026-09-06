package com.beatblock.timeline.command;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Cut selection into the timeline clipboard, then delete it in one Undo step.
 * <p>
 * Mirrors Copy for clipboard content (selected events and events inside selected clips),
 * then deletes via {@link DeleteSelectedTimelineEntriesCommand} so clip-only cuts remove
 * whole clips (including BuildLayer / audio-root cleanup) instead of leaving orphans.
 * Redo restores the post-cut clipboard and re-deletes from the captured delete snapshot.
 */
public final class CutTimelineEventsCommand implements Command {

	private final Timeline timeline;
	private final SelectionState selectionState;
	private final TimelineTrackListState trackListState;
	private final List<TimelineInteractionClipboard.ClipboardEvent> clipboardBuffer;
	private final DeleteSelectedTimelineEntriesCommand deleteCommand;

	private List<TimelineInteractionClipboard.ClipboardEvent> previousClipboard = List.of();
	private List<TimelineInteractionClipboard.ClipboardEvent> cutClipboard = List.of();
	private boolean executed;
	private boolean snapshotCaptured;

	public CutTimelineEventsCommand(
		@NonNull Timeline timeline,
		@NonNull SelectionState selectionState,
		@NonNull TimelineTrackListState trackListState,
		@NonNull List<TimelineInteractionClipboard.ClipboardEvent> clipboardBuffer
	) {
		this(timeline, currentLayerManager(), selectionState, trackListState, clipboardBuffer);
	}

	public CutTimelineEventsCommand(
		@NonNull Timeline timeline,
		@Nullable BuildLayerManager layerManager,
		@NonNull SelectionState selectionState,
		@NonNull TimelineTrackListState trackListState,
		@NonNull List<TimelineInteractionClipboard.ClipboardEvent> clipboardBuffer
	) {
		this.timeline = timeline;
		this.selectionState = selectionState;
		this.trackListState = trackListState;
		this.clipboardBuffer = clipboardBuffer;
		this.deleteCommand = new DeleteSelectedTimelineEntriesCommand(
			timeline, layerManager, selectionState, trackListState);
	}

	public boolean wasApplied() {
		return executed;
	}

	@Override
	public void execute() {
		if (executed) {
			return;
		}
		if (snapshotCaptured) {
			restoreClipboard(clipboardBuffer, cutClipboard);
			deleteCommand.execute();
			executed = deleteCommand.wasApplied();
			return;
		}

		previousClipboard = snapshotClipboard(clipboardBuffer);
		TimelineInteractionClipboard.copy(
			clipboardBuffer, timeline, selectionState, trackListState, true);
		cutClipboard = snapshotClipboard(clipboardBuffer);

		boolean hadClipboardOrClipSelection =
			!clipboardBuffer.isEmpty() || !selectionState.getSelectedClips().isEmpty();
		if (!hadClipboardOrClipSelection && selectionState.getSelectedEvents().isEmpty()) {
			return;
		}

		deleteCommand.execute();
		executed = deleteCommand.wasApplied();
		if (!executed) {
			restoreClipboard(clipboardBuffer, previousClipboard);
			cutClipboard = List.of();
			return;
		}
		snapshotCaptured = true;
	}

	@Override
	public void undo() {
		if (!executed) {
			return;
		}
		deleteCommand.undo();
		restoreClipboard(clipboardBuffer, previousClipboard);
		executed = false;
	}

	private static List<TimelineInteractionClipboard.ClipboardEvent> snapshotClipboard(
		List<TimelineInteractionClipboard.ClipboardEvent> buffer
	) {
		return buffer == null || buffer.isEmpty() ? List.of() : List.copyOf(buffer);
	}

	private static void restoreClipboard(
		List<TimelineInteractionClipboard.ClipboardEvent> buffer,
		List<TimelineInteractionClipboard.ClipboardEvent> previous
	) {
		buffer.clear();
		buffer.addAll(previous);
	}

	private static @Nullable BuildLayerManager currentLayerManager() {
		try {
			return BeatBlock.getContext().buildLayerManager();
		} catch (IllegalStateException ignored) {
			return null;
		}
	}
}
