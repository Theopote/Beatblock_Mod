package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioLoader;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.project.OscProjectStore;

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
			return PresenterResult.failure("路径不能为空");
		}
		AudioLoader loader = audioLoader.get();
		if (loader == null) {
			return PresenterResult.failure("音频加载器不可用");
		}
		if (!loader.load(path)) {
			return PresenterResult.failure("导入失败");
		}
		return PresenterResult.success("");
	}

	public PresenterResult openProject(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure("路径不能为空");
		}
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure("Timeline 不可用");
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
			return PresenterResult.success("工程已打开");
		} catch (Exception e) {
			return PresenterResult.failure("打开失败: " + e.getMessage());
		}
	}

	public PresenterResult saveProject(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure("路径不能为空");
		}
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure("Timeline 不可用");
		}
		try {
			OscProjectStore.save(Path.of(path), current, layerManager.get());
			current.setMetadata("projectPath", path);
			return PresenterResult.success("工程已保存");
		} catch (Exception e) {
			return PresenterResult.failure("保存失败: " + e.getMessage());
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
