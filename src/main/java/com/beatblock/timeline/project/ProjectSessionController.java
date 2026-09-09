package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioLoader;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.PresenterResult;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Project lifecycle: New / Open / Save and session-switch cleanup.
 * MenuBar is UI only; dirty gates are enforced by callers using {@link #isDirty()}.
 */
public final class ProjectSessionController {

	private final ProjectSessionState session;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<AudioLoader> audioLoader;
	private final Supplier<@Nullable StageManager> stageManager;

	public ProjectSessionController(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<AudioLoader> audioLoader,
		Supplier<@Nullable StageManager> stageManager
	) {
		this(ProjectSessionState.get(), timeline, timelineEditor, layerManager, audioLoader, stageManager);
	}

	public ProjectSessionController(
		ProjectSessionState session,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<AudioLoader> audioLoader,
		Supplier<@Nullable StageManager> stageManager
	) {
		this.session = session != null ? session : ProjectSessionState.get();
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.layerManager = layerManager;
		this.audioLoader = audioLoader;
		this.stageManager = stageManager != null ? stageManager : () -> null;
	}

	public ProjectSessionState session() {
		return session;
	}

	public boolean isDirty() {
		return session.isDirty();
	}

	public String defaultSaveProjectPath() {
		Timeline current = timeline.get();
		if (current == null) {
			return "";
		}
		Object path = current.getMetadata("projectPath");
		if (path != null && !String.valueOf(path).isBlank()) {
			return String.valueOf(path);
		}
		String sessionPath = session.projectPath();
		return sessionPath != null ? sessionPath : "";
	}

	/**
	 * Reset to a fresh empty project. Caller must confirm discard when dirty.
	 */
	public PresenterResult newProject() {
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		try {
			beginSessionSwitch();
			current.resetToEmptyProject();
			BuildLayerManager layers = layerManager.get();
			if (layers != null) {
				layers.purgeAllLayers();
			}
			StageManager stages = stageManager.get();
			if (stages != null) {
				stages.clear();
			}
			String newId = UUID.randomUUID().toString();
			current.setMetadata("projectId", newId);
			current.setMetadata("projectPath", null);
			current.setMetadata("audioPath", null);
			finishSessionSwitch(false);
			session.onNewProject(newId);
			return PresenterResult.success(BBTexts.get("beatblock.message.project_new"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.new_project_failed", e.getMessage()));
		}
	}

	/**
	 * Transactional open. Audio missing after document commit remains a soft warning.
	 * Caller must confirm discard when dirty.
	 */
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
			beginSessionSwitch();
			BuildLayerManager layers = layerManager.get();
			var open = ProjectDocumentLoader.openTransactional(Path.of(path), current, layers);
			OscProjectStore.LoadedProject loaded = open.loaded();
			applyLoadedIdentity(current, loaded);
			boolean audioLoadFailed = loadReferencedAudio(loaded.getAudioPath());
			finishSessionSwitch(true);
			session.onProjectOpened(loaded.getProjectId(), loaded.getProjectPath());
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
			Object id = current.getMetadata("projectId");
			session.onProjectSaved(id != null ? String.valueOf(id) : "", path);
			return PresenterResult.success(BBTexts.get("beatblock.message.project_saved"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.save_failed", e.getMessage()));
		}
	}

	private void beginSessionSwitch() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.cancelLiveDocumentPreview();
		}
		stopPlaybackSafely();
	}

	private void finishSessionSwitch(boolean applyPersistedWorld) {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.clearTransientEditState();
			editor.syncClockDuration();
		}
		BuildLayerManager layers = layerManager.get();
		if (applyPersistedWorld && layers != null) {
			try {
				layers.applyPersistedWorldState(BuildLayerManager.currentWorld());
			} catch (Throwable error) {
				BeatBlock.LOGGER.debug("Skip applyPersistedWorldState after project switch", error);
			}
		}
	}

	private static void applyLoadedIdentity(Timeline current, OscProjectStore.LoadedProject loaded) {
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

	private boolean loadReferencedAudio(String audioPath) {
		if (audioPath == null || audioPath.isBlank()) {
			return false;
		}
		if (!isLoadableLocalAudioPath(audioPath)) {
			return true;
		}
		AudioLoader loader = audioLoader.get();
		return loader == null || !loader.load(audioPath);
	}

	private static void stopPlaybackSafely() {
		try {
			BeatBlockClientDriver.stopPlayback();
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip stopPlayback during project session switch", error);
		}
	}

	/** 仅尝试加载本地/可解码音频；跳过 golden:// 等测试占位 scheme。 */
	public static boolean isLoadableLocalAudioPath(String path) {
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
