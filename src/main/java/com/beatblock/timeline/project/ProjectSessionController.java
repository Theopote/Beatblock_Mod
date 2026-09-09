package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioLoader;
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
 * Project lifecycle: New / Open / Save / Save As and session-switch cleanup.
 * MenuBar is UI; dirty gates use {@link UnsavedChangesCoordinator}.
 */
public final class ProjectSessionController {

	public enum SaveRoute {
		/** Wrote to current projectPath. */
		SAVED,
		/** No projectPath — UI must open Save As. */
		NEEDS_SAVE_AS,
		/** Save failed; path unchanged; dirty unchanged. */
		FAILED
	}

	public record SaveOutcome(SaveRoute route, PresenterResult result) {
		public boolean ok() {
			return route == SaveRoute.SAVED && result != null && result.ok();
		}

		public static SaveOutcome saved(PresenterResult result) {
			return new SaveOutcome(SaveRoute.SAVED, result);
		}

		public static SaveOutcome needsSaveAs() {
			return new SaveOutcome(SaveRoute.NEEDS_SAVE_AS,
				PresenterResult.failure(BBTexts.get("beatblock.message.save_as_required")));
		}

		public static SaveOutcome failed(PresenterResult result) {
			return new SaveOutcome(SaveRoute.FAILED, result);
		}
	}

	private final ProjectSessionState session;
	private final UnsavedChangesCoordinator unsaved;
	private final ProjectRuntimeResetService runtimeReset;
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
		this.unsaved = new UnsavedChangesCoordinator();
		this.runtimeReset = new ProjectRuntimeResetService(timelineEditor);
	}

	public ProjectSessionState session() {
		return session;
	}

	public UnsavedChangesCoordinator unsavedChanges() {
		return unsaved;
	}

	public ProjectRuntimeResetService runtimeReset() {
		return runtimeReset;
	}

	public boolean isDirty() {
		return session.isDirty();
	}

	public boolean hasProjectPath() {
		return !defaultSaveProjectPath().isBlank();
	}

	public String defaultSaveProjectPath() {
		Timeline current = timeline.get();
		if (current != null) {
			Object path = current.getMetadata("projectPath");
			if (path != null && !String.valueOf(path).isBlank()) {
				return String.valueOf(path);
			}
		}
		return session.projectPathString();
	}

	/**
	 * Save to current projectPath; if none, returns {@link SaveRoute#NEEDS_SAVE_AS}.
	 */
	public SaveOutcome save() {
		String path = defaultSaveProjectPath();
		if (path == null || path.isBlank()) {
			return SaveOutcome.needsSaveAs();
		}
		PresenterResult result = saveAs(path);
		return result.ok() ? SaveOutcome.saved(result) : SaveOutcome.failed(result);
	}

	/**
	 * Save As: write to {@code rawPath}; update projectPath only after success.
	 */
	public PresenterResult saveAs(String rawPath) {
		String path = rawPath != null ? rawPath.trim() : "";
		if (path.isEmpty()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.path_empty"));
		}
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		String previousPath = stringMeta(current, "projectPath");
		try {
			Path abs = Path.of(path).toAbsolutePath().normalize();
			OscProjectStore.save(abs, current, layerManager.get());
			current.setMetadata("projectPath", abs.toString());
			Object id = current.getMetadata("projectId");
			if (id != null && !String.valueOf(id).isBlank()) {
				session.syncIdentity(String.valueOf(id), abs.toString());
			}
			session.markSaved(abs);
			return PresenterResult.success(BBTexts.get("beatblock.message.project_saved"));
		} catch (Exception e) {
			// Failure must not update path / dirty.
			if (!previousPath.isBlank()) {
				current.setMetadata("projectPath", previousPath);
			} else {
				current.setMetadata("projectPath", null);
			}
			return PresenterResult.failure(BBTexts.get("beatblock.message.save_failed", e.getMessage()));
		}
	}

	public PresenterResult saveProject(String rawPath) {
		return saveAs(rawPath);
	}

	/**
	 * Reset to a fresh empty project (transactional). Caller must confirm discard when dirty.
	 * On failure, live Timeline / BuildLayers are restored from an in-memory backup.
	 */
	public PresenterResult newProject() {
		Timeline current = timeline.get();
		if (current == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		String newId = UUID.randomUUID().toString();
		try {
			runtimeReset.reset(ProjectRuntimeResetService.Mode.PREPARE_SWITCH);
			BuildLayerManager layers = layerManager.get();
			ProjectDocumentLoader.newProjectTransactional(current, layers, newId);
			StageManager stages = stageManager.get();
			if (stages != null) {
				try {
					stages.clear();
				} catch (RuntimeException stageFailure) {
					// Document already reset; stage clear is best-effort presentation cleanup.
					BeatBlock.LOGGER.debug("StageManager.clear failed during New Project", stageFailure);
				}
			}
			runtimeReset.reset(ProjectRuntimeResetService.Mode.AFTER_DOCUMENT_SWAP);
			session.markNewProject(newId);
			return PresenterResult.success(BBTexts.get("beatblock.message.project_new"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.new_project_failed", e.getMessage()));
		}
	}

	/**
	 * Transactional open. Audio missing after document commit remains a soft warning.
	 * Undo / runtime clear only after successful commit.
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
			// Cancel live gesture before commit, but do not clear Undo until success.
			TimelineEditor editor = timelineEditor.get();
			if (editor != null) {
				editor.cancelLiveDocumentPreview();
			}
			BuildLayerManager layers = layerManager.get();
			var open = ProjectDocumentLoader.openTransactional(Path.of(path), current, layers);
			OscProjectStore.LoadedProject loaded = open.loaded();
			runtimeReset.reset(ProjectRuntimeResetService.Mode.PREPARE_SWITCH);
			applyLoadedIdentity(current, loaded);
			boolean audioLoadFailed = loadReferencedAudio(loaded.getAudioPath());
			runtimeReset.reset(ProjectRuntimeResetService.Mode.AFTER_DOCUMENT_SWAP);
			if (layers != null) {
				try {
					layers.applyPersistedWorldState(BuildLayerManager.currentWorld());
				} catch (Throwable error) {
					BeatBlock.LOGGER.debug("Skip applyPersistedWorldState after project open", error);
				}
			}
			Path opened = Path.of(path).toAbsolutePath().normalize();
			session.syncIdentity(loaded.getProjectId(), opened.toString());
			session.markOpened(opened);
			return PresenterResult.success(BBTexts.get(audioLoadFailed
				? "beatblock.message.project_opened_audio_failed"
				: "beatblock.message.project_opened"));
		} catch (Exception e) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.open_failed", e.getMessage()));
		}
	}

	/** Close BeatBlock after unsaved gate: shared runtime cleanup then UI callback. */
	public void closeBeatBlock(Runnable onCloseUi) {
		runtimeReset.reset(ProjectRuntimeResetService.Mode.CLOSE_UI);
		if (onCloseUi != null) {
			onCloseUi.run();
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

	private static String stringMeta(Timeline timeline, String key) {
		Object v = timeline.getMetadata(key);
		return v != null ? String.valueOf(v).trim() : "";
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
