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

	private final TimelineEditorPresenter editorPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<AudioLoader> audioLoader;

	public MenuBarPresenter(
		TimelineEditorPresenter editorPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<AudioLoader> audioLoader
	) {
		this.editorPresenter = editorPresenter;
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
		return editorPresenter.undo();
	}

	public boolean redo() {
		return editorPresenter.redo();
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
			BuildLayerManager layers = layerManager.get();
			OscProjectStore.LoadedProject loaded = OscProjectStore.load(Path.of(path), layers, current);
			applyLoadedProject(current, loaded);
			if (!loaded.getAudioPath().isBlank()) {
				AudioLoader loader = audioLoader.get();
				if (loader != null) {
					loader.load(loaded.getAudioPath());
				}
			}
			TimelineEditor editor = timelineEditor.get();
			if (editor != null) {
				editor.clearUndoHistory();
				editor.syncClockDuration();
			}
			if (layers != null) {
				layers.applyPersistedWorldState(BuildLayerManager.currentWorld());
			}
			return PresenterResult.success(BBTexts.get("beatblock.message.project_opened"));
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
}
