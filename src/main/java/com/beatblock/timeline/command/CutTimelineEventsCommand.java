package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineEventRef;
import com.beatblock.timeline.interaction.TimelineEventRefs;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/** 剪切选中事件：写入剪贴板并删除原事件，可撤销。 */
public final class CutTimelineEventsCommand implements Command {

	private final Timeline timeline;
	private final SelectionState selectionState;
	private final TimelineTrackListState trackListState;
	private final List<TimelineInteractionClipboard.ClipboardEvent> clipboardBuffer;
	private final List<DeleteEventCommand> deleteCommands = new ArrayList<>();
	private boolean executed;

	public CutTimelineEventsCommand(
		@NonNull Timeline timeline,
		@NonNull SelectionState selectionState,
		@NonNull TimelineTrackListState trackListState,
		@NonNull List<TimelineInteractionClipboard.ClipboardEvent> clipboardBuffer
	) {
		this.timeline = timeline;
		this.selectionState = selectionState;
		this.trackListState = trackListState;
		this.clipboardBuffer = clipboardBuffer;
	}

	@Override
	public void execute() {
		if (executed) {
			return;
		}
		TimelineInteractionClipboard.copy(clipboardBuffer, timeline, selectionState);
		deleteCommands.clear();
		for (String eventId : new ArrayList<>(selectionState.getSelectedEvents())) {
			TimelineEventRef ref = TimelineEventRefs.find(timeline, eventId);
			if (ref == null) {
				continue;
			}
			if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId())) {
				continue;
			}
			DeleteEventCommand deleteCmd = new DeleteEventCommand(
				timeline,
				ref.track().getId(),
				ref.clip().getId(),
				ref.event()
			);
			deleteCmd.execute();
			deleteCommands.add(deleteCmd);
			selectionState.deselectEvent(eventId);
		}
		executed = !deleteCommands.isEmpty();
	}

	@Override
	public void undo() {
		if (!executed) {
			return;
		}
		for (int i = deleteCommands.size() - 1; i >= 0; i--) {
			deleteCommands.get(i).undo();
		}
		clipboardBuffer.clear();
		executed = false;
	}
}
