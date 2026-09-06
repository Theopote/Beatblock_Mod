package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioLoader;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.project.OscProjectStore;
import com.beatblock.ui.i18n.BBTexts;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 菜单栏业务逻辑：撤销/重做、音频导入、工程打开/保存。
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
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<AudioLoader> audioLoader;

	public MenuBarPresenter(
		TimelineEditorPresenter editorPresenter,
		TimelineActionDispatcher actions,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<AudioLoader> audioLoader
	) {
		this.editorPresenter = editorPresenter;
		this.actions = actions;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.layerManager = layerManager;
		this.audioLoader = audioLoader;
	}

	public TimelineEditorPresenter editorPresenter() {
		return editorPresenter;
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
		Timeline current = timeline.get();
		if (current == null) {
			return "";
		}
		Object path = current.getMetadata("projectPath");
		return path != null ? String.valueOf(path) : "";
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
		return PresenterResult.success("");
	}

	public PresenterResult openProject(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.path_empty"));
		}
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		try {
			// Cancel live drag/resize preview before loadInto replaces timeline contents,
			// otherwise gesture snapshots would apply onto the newly loaded document.
			TimelineEditor editor = timelineEditor.get();
			if (editor != null) {
				editor.cancelLiveDocumentPreview();
			}
			BuildLayerManager layers = layerManager.get();
			OscProjectStore.LoadedProject loaded = OscProjectStore.load(Path.of(path), layers, current);
			applyLoadedProject(current, loaded);
			boolean audioLoadFailed = false;
			String audioPath = loaded.getAudioPath();
			if (!audioPath.isBlank() && isLoadableLocalAudioPath(audioPath)) {
				AudioLoader loader = audioLoader.get();
				audioLoadFailed = loader == null || !loader.load(audioPath);
			} else if (!audioPath.isBlank()) {
				// golden:// 等占位路径：保留元数据，不尝试本地解码
				audioLoadFailed = true;
			}
			if (editor != null) {
				editor.clearUndoHistory();
				editor.syncClockDuration();
			}
			if (layers != null) {
				layers.applyPersistedWorldState(BuildLayerManager.currentWorld());
			}
			return PresenterResult.success(BBTexts.get(audioLoadFailed
				? "beatblock.message.project_opened_audio_failed"
				: "beatblock.message.project_opened"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.open_failed", e.getMessage()));
		}
	}

	public PresenterResult saveProject(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.path_empty"));
		}
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		try {
			OscProjectStore.save(Path.of(path), current, layerManager.get());
			current.setMetadata("projectPath", path);
			return PresenterResult.success(BBTexts.get("beatblock.message.project_saved"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.save_failed", e.getMessage()));
		}
	}

	private static void applyLoadedProject(Timeline current, OscProjectStore.LoadedProject loaded) {
		if (!loaded.getTimelineName().isBlank()) {
			current.setName(loaded.getTimelineName());
		}
		current.setMetadata("projectId", loaded.getProjectId());
		current.setMetadata("projectPath", loaded.getProjectPath());
		if (!loaded.getAudioPath().isBlank()) {
			current.setMetadata("audioPath", loaded.getAudioPath());
		}
		current.setMarkers(loaded.getMarkers());
	}

	/** 仅尝试加载本地/可解码音频；跳过 golden:// 等测试占位 scheme。 */
	static boolean isLoadableLocalAudioPath(String path) {
		if (path == null || path.isBlank()) return false;
		String trimmed = path.trim();
		int scheme = trimmed.indexOf("://");
		if (scheme > 0) {
			String schemeName = trimmed.substring(0, scheme).toLowerCase();
			return "file".equals(schemeName);
		}
		return true;
	}
}
