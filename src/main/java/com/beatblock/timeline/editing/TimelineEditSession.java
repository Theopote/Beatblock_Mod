package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CutTimelineEventsCommand;
import com.beatblock.timeline.command.DeleteSelectedTimelineEntriesCommand;
import com.beatblock.timeline.command.DuplicateTimelineEventsCommand;
import com.beatblock.timeline.command.PasteTimelineEventsCommand;
import com.beatblock.timeline.command.SplitClipCommand;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.interaction.TimelineInteractionDeleteSupport;
import com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.function.DoubleSupplier;

/**
 * 时间线编辑会话：集中管理选区、剪贴板和可撤销编辑命令。
 * 文档、视图与播放状态不属于本会话。
 */
public final class TimelineEditSession {

	private final Timeline timeline;
	private final SelectionState selection;
	private final TimelineTrackListState trackListState;
	private final TimelineInteraction interaction;
	private final CommandManager commands;
	private final DoubleSupplier playheadSeconds;

	public TimelineEditSession(
		Timeline timeline,
		SelectionState selection,
		TimelineTrackListState trackListState,
		TimelineInteraction interaction,
		CommandManager commands,
		DoubleSupplier playheadSeconds
	) {
		this.timeline = timeline;
		this.selection = selection;
		this.trackListState = trackListState;
		this.interaction = interaction;
		this.commands = commands;
		this.playheadSeconds = playheadSeconds;
	}

	public SelectionState selection() {
		return selection;
	}

	public CommandManager commands() {
		return commands;
	}

	public void clearHistory() {
		commands.clear();
	}

	public boolean hasSelection() {
		return !selection.getSelectedEvents().isEmpty() || !selection.getSelectedClips().isEmpty();
	}

	public boolean hasClipboardContent() {
		return !interaction.clipboardEvents().isEmpty();
	}

	public boolean canDelete() {
		return TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selection, trackListState);
	}

	public boolean canDuplicate() {
		return DuplicateTimelineEventsCommand.canDuplicate(timeline, selection, trackListState);
	}

	public boolean canSplitAtPlayhead() {
		return findSplittableClip(playheadSeconds.getAsDouble()) != null;
	}

	public boolean canSplitAt(double timeSeconds) {
		return findSplittableClip(timeSeconds) != null;
	}

	public void copy() {
		TimelineInteractionClipboard.copy(interaction.clipboardEvents(), timeline, selection);
	}

	public void cut() {
		if (!canDelete()) {
			return;
		}
		CutTimelineEventsCommand command = new CutTimelineEventsCommand(
			timeline, selection, trackListState, interaction.clipboardEvents());
		commands.execute(command);
		if (command.wasApplied()) {
			TimelineDocumentChangeNotifier.notifyDocumentEdited();
		}
	}

	public void pasteAtPlayhead() {
		pasteAt(playheadSeconds.getAsDouble());
	}

	public void pasteAt(double anchorTimeSeconds) {
		if (!hasClipboardContent()) {
			return;
		}
		PasteTimelineEventsCommand command = new PasteTimelineEventsCommand(
			new TimelineInteractionClipboard.PasteRequest(
				timeline,
				selection,
				interaction.clipboardEvents(),
				anchorTimeSeconds,
				interaction.contextTrackIdForClipboard(),
				interaction.contextClipIdForClipboard(),
				trackListState
			));
		commands.execute(command);
		if (command.wasApplied()) {
			TimelineDocumentChangeNotifier.notifyDocumentEdited();
		}
	}

	public void deleteSelection() {
		if (!canDelete()) {
			return;
		}
		DeleteSelectedTimelineEntriesCommand command = new DeleteSelectedTimelineEntriesCommand(
			timeline, selection, trackListState);
		commands.execute(command);
		if (command.wasApplied()) {
			TimelineDocumentChangeNotifier.notifyDocumentEdited();
		}
	}

	public boolean duplicateSelection() {
		if (!canDuplicate()) {
			return false;
		}
		DuplicateTimelineEventsCommand command =
			new DuplicateTimelineEventsCommand(timeline, selection, trackListState);
		commands.execute(command);
		if (!command.wasApplied()) {
			return false;
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return true;
	}

	public boolean splitAtPlayhead() {
		return splitAt(playheadSeconds.getAsDouble());
	}

	public boolean splitAt(double timeSeconds) {
		ClipRef ref = findSplittableClip(timeSeconds);
		if (ref == null) {
			return false;
		}
		SplitClipCommand command = new SplitClipCommand(
			timeline, ref.trackId(), ref.clipId(), timeSeconds, selection);
		commands.execute(command);
		if (!command.wasApplied()) {
			return false;
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return true;
	}

	private ClipRef findSplittableClip(double timeSeconds) {
		for (String clipId : selection.getSelectedClips()) {
			ClipRef ref = findClipRef(clipId);
			if (ref != null
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.trackId())
				&& SplitClipCommand.canSplit(ref.clip(), timeSeconds)) {
				return ref;
			}
		}
		String contextClipId = interaction.contextClipIdForClipboard();
		if (contextClipId != null) {
			ClipRef ref = findClipRef(contextClipId);
			if (ref != null
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.trackId())
				&& SplitClipCommand.canSplit(ref.clip(), timeSeconds)) {
				return ref;
			}
		}
		for (Track track : timeline.getTracks()) {
			if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
				continue;
			}
			for (Clip clip : track.getClips()) {
				if (SplitClipCommand.canSplit(clip, timeSeconds)
					&& timeSeconds >= clip.getStartTimeSeconds()
					&& timeSeconds <= clip.getEndTimeSeconds()) {
					return new ClipRef(track.getId(), clip.getId(), clip);
				}
			}
		}
		return null;
	}

	private ClipRef findClipRef(String clipId) {
		if (clipId == null || clipId.isBlank()) return null;
		for (Track track : timeline.getTracks()) {
			Clip clip = track.getClip(clipId);
			if (clip != null) {
				return new ClipRef(track.getId(), clipId, clip);
			}
		}
		return null;
	}

	private record ClipRef(String trackId, String clipId, Clip clip) {}
}
