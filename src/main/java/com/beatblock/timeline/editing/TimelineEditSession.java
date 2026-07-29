package com.beatblock.timeline.editing;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CutTimelineEventsCommand;
import com.beatblock.timeline.command.PasteTimelineEventsCommand;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.interaction.TimelineInteractionDeleteSupport;
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

	public void copy() {
		TimelineInteractionClipboard.copy(interaction.clipboardEvents(), timeline, selection);
	}

	public void cut() {
		commands.execute(new CutTimelineEventsCommand(
			timeline, selection, trackListState, interaction.clipboardEvents()));
	}

	public void pasteAtPlayhead() {
		pasteAt(playheadSeconds.getAsDouble());
	}

	public void pasteAt(double anchorTimeSeconds) {
		commands.execute(new PasteTimelineEventsCommand(new TimelineInteractionClipboard.PasteRequest(
			timeline,
			selection,
			interaction.clipboardEvents(),
			anchorTimeSeconds,
			interaction.contextTrackIdForClipboard(),
			interaction.contextClipIdForClipboard(),
			trackListState
		)));
	}

	public void deleteSelection() {
		TimelineInteractionDeleteSupport.deleteSelectedEntries(timeline, selection, trackListState);
	}
}
