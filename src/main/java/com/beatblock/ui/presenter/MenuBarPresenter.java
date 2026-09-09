package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioLoader;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.project.ProjectSessionController;
import com.beatblock.ui.i18n.BBTexts;

import java.util.function.Supplier;

/**
 * 菜单栏业务逻辑：撤销/重做、音频导入、工程生命周期（委托 {@link ProjectSessionController}）。
 */
public final class MenuBarPresenter {

	public record EditViewState(
		boolean hasSelection,
		boolean hasClipboard,
		boolean canDelete,
		boolean canDuplicate,
		boolean canSplitAtPlayhead
	) {}

	private final TimelineEditorPresenter editorPresenter;
	private final TimelineActionDispatcher actions;
	private final ProjectSessionController sessionController;
	private final Supplier<AudioLoader> audioLoader;

	public MenuBarPresenter(
		TimelineEditorPresenter editorPresenter,
		TimelineActionDispatcher actions,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<AudioLoader> audioLoader
	) {
		this(
			editorPresenter,
			actions,
			new ProjectSessionController(
				timeline,
				timelineEditor,
				layerManager,
				audioLoader,
				() -> {
					try {
						return com.beatblock.BeatBlock.getContext().stageManager();
					} catch (Throwable ignored) {
						return null;
					}
				}
			),
			audioLoader
		);
	}

	public MenuBarPresenter(
		TimelineEditorPresenter editorPresenter,
		TimelineActionDispatcher actions,
		ProjectSessionController sessionController,
		Supplier<AudioLoader> audioLoader
	) {
		this.editorPresenter = editorPresenter;
		this.actions = actions;
		this.sessionController = sessionController;
		this.audioLoader = audioLoader;
	}

	public TimelineEditorPresenter editorPresenter() {
		return editorPresenter;
	}

	public ProjectSessionController sessionController() {
		return sessionController;
	}

	public boolean isDirty() {
		return sessionController.isDirty();
	}

	public TimelineEditorPresenter.UndoRedoViewState undoRedoState() {
		return editorPresenter.undoRedoState();
	}

	public boolean undo() {
		return actions.execute(TimelineActionId.UNDO).success();
	}

	public boolean redo() {
		return actions.execute(TimelineActionId.REDO).success();
	}

	public EditViewState editViewState() {
		var state = actions.editState();
		return new EditViewState(
			state.hasSelection(),
			state.hasClipboard(),
			state.canDelete(),
			state.canDuplicate(),
			state.canSplitAtPlayhead());
	}

	public void cutTimelineSelection() {
		actions.execute(TimelineActionId.CUT);
	}

	public void copyTimelineSelection() {
		actions.execute(TimelineActionId.COPY);
	}

	public void pasteTimelineAtPlayhead() {
		actions.execute(TimelineActionId.PASTE_AT_PLAYHEAD);
	}

	public void deleteTimelineSelection() {
		actions.execute(TimelineActionId.DELETE);
	}

	public void duplicateTimelineSelection() {
		actions.execute(TimelineActionId.DUPLICATE);
	}

	public void splitTimelineAtPlayhead() {
		actions.execute(TimelineActionId.SPLIT_AT_PLAYHEAD);
	}

	public String defaultSaveProjectPath() {
		return sessionController.defaultSaveProjectPath();
	}

	public PresenterResult importAudio(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.path_empty"));
		}
		AudioLoader loader = audioLoader.get();
		if (loader == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.audio_loader_unavailable"));
		}
		if (!loader.load(path)) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.import_failed"));
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return PresenterResult.success("");
	}

	public PresenterResult newProject() {
		return sessionController.newProject();
	}

	public PresenterResult openProject(String rawPath) {
		return sessionController.openProject(rawPath);
	}

	public PresenterResult saveProject(String rawPath) {
		return sessionController.saveProject(rawPath);
	}

	static boolean isLoadableLocalAudioPath(String path) {
		return ProjectSessionController.isLoadableLocalAudioPath(path);
	}
}
