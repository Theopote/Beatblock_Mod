package com.beatblock.ui.presenter;

import com.beatblock.timeline.TimelineEditor;

import java.util.function.Supplier;

/**
 * 时间线应用动作的唯一分发入口；UI 入口不应直接修改编辑器或运行生成器。
 */
public final class TimelineActionDispatcher {

	public record ActionResult(boolean executed, boolean success, String message, int count) {
		static ActionResult completed(boolean success) {
			return new ActionResult(true, success, "", 0);
		}

		static ActionResult unavailable() {
			return new ActionResult(false, false, "", 0);
		}
	}

	public record EditState(
		boolean hasSelection,
		boolean hasClipboard,
		boolean canDelete,
		boolean canDuplicate,
		boolean canSplitAtPlayhead
	) {}

	private final TimelineEditorPresenter editorPresenter;
	private final Supplier<TimelineEditor> timelineEditor;
	private final TimelineToolbarActionsPresenter generatedActions;

	public TimelineActionDispatcher(
		TimelineEditorPresenter editorPresenter,
		Supplier<TimelineEditor> timelineEditor,
		TimelineToolbarActionsPresenter generatedActions
	) {
		this.editorPresenter = editorPresenter;
		this.timelineEditor = timelineEditor;
		this.generatedActions = generatedActions;
	}

	public EditState editState() {
		TimelineEditor editor = timelineEditor.get();
		return editor != null
			? new EditState(
				editor.getEditSession().hasSelection(),
				editor.getEditSession().hasClipboardContent(),
				editor.getEditSession().canDelete(),
				editor.getEditSession().canDuplicate(),
				editor.getEditSession().canSplitAtPlayhead())
			: new EditState(false, false, false, false, false);
	}

	public boolean isEnabled(TimelineActionId actionId) {
		if (actionId == null) return false;
		TimelineEditor editor = timelineEditor.get();
		return switch (actionId) {
			case UNDO -> editorPresenter.undoRedoState().canUndo();
			case REDO -> editorPresenter.undoRedoState().canRedo();
			case CUT, COPY -> editor != null && editor.getEditSession().hasSelection();
			case PASTE_AT_PLAYHEAD -> editor != null && editor.getEditSession().hasClipboardContent();
			case DELETE -> editor != null && editor.getEditSession().canDelete();
			case DUPLICATE -> editor != null && editor.getEditSession().canDuplicate();
			case SPLIT_AT_PLAYHEAD -> editor != null && editor.getEditSession().canSplitAtPlayhead();
			case ADD_MARKER_AT_PLAYHEAD -> editor != null;
			case RUN_BINDING_MAP, RUN_AUTO_MAP, BAKE_STEP, GENERATE_RHYTHM_DROP -> true;
		};
	}

	public ActionResult execute(TimelineActionId actionId) {
		if (!isEnabled(actionId)) return ActionResult.unavailable();
		TimelineEditor editor = timelineEditor.get();
		return switch (actionId) {
			case UNDO -> ActionResult.completed(editorPresenter.undo());
			case REDO -> ActionResult.completed(editorPresenter.redo());
			case CUT -> { editor.getEditSession().cut(); yield ActionResult.completed(true); }
			case COPY -> { editor.getEditSession().copy(); yield ActionResult.completed(true); }
			case PASTE_AT_PLAYHEAD -> { editor.getEditSession().pasteAtPlayhead(); yield ActionResult.completed(true); }
			case DELETE -> { editor.getEditSession().deleteSelection(); yield ActionResult.completed(true); }
			case DUPLICATE -> ActionResult.completed(editor.getEditSession().duplicateSelection());
			case SPLIT_AT_PLAYHEAD -> ActionResult.completed(editor.getEditSession().splitAtPlayhead());
			case ADD_MARKER_AT_PLAYHEAD -> ActionResult.completed(addMarkerAtPlayhead(editor));
			case RUN_BINDING_MAP -> fromOutcome(generatedActions.runBindingMap());
			case RUN_AUTO_MAP -> fromOutcome(generatedActions.runAutoMap());
			case BAKE_STEP -> fromOutcome(generatedActions.runBakeStepSequences());
			case GENERATE_RHYTHM_DROP -> fromOutcome(generatedActions.runGenerateRhythmDrops());
		};
	}

	private static boolean addMarkerAtPlayhead(TimelineEditor editor) {
		if (editor == null) {
			return false;
		}
		var result = com.beatblock.timeline.marker.MarkerInsertionService.insertGenericAtPlayhead(
			editor.getTimeline(),
			editor,
			editor.getPlaybackSession().currentTimeSeconds()
		);
		return result.written();
	}

	private static ActionResult fromOutcome(TimelineToolbarActionsPresenter.ActionOutcome outcome) {
		return new ActionResult(true, outcome.success(), outcome.message(), outcome.count());
	}
}
