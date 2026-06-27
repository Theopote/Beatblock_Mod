package com.beatblock.ui.presenter;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;

import java.util.List;
import java.util.function.Supplier;

public final class UndoHistoryPanelPresenter {

	public record ViewState(
		boolean editorReady,
		int undoCount,
		int redoCount,
		List<String> undoDescriptions,
		List<String> redoDescriptions
	) {}

	private final TimelineEditorPresenter editorPresenter;
	private final Supplier<TimelineEditor> timelineEditor;

	public UndoHistoryPanelPresenter(
		TimelineEditorPresenter editorPresenter,
		Supplier<TimelineEditor> timelineEditor
	) {
		this.editorPresenter = editorPresenter;
		this.timelineEditor = timelineEditor;
	}

	public ViewState viewState() {
		TimelineEditor editor = timelineEditor.get();
		if (editor == null) {
			return new ViewState(false, 0, 0, List.of(), List.of());
		}
		CommandManager commands = editor.getCommandManager();
		return new ViewState(
			true,
			commands.undoCount(),
			commands.redoCount(),
			commands.undoDescriptionsNewestFirst(),
			commands.redoDescriptionsNewestFirst()
		);
	}

	public boolean undo() {
		return editorPresenter.undo();
	}

	public boolean redo() {
		return editorPresenter.redo();
	}
}
