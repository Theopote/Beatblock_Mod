package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioLoader;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.project.OscProjectStore;
import com.beatblock.timeline.project.ProjectSessionController;
import com.beatblock.timeline.project.ProjectSessionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuBarPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private BuildLayerManager layerManager;
	private RecordingAudioLoader audioLoader;
	private MenuBarPresenter presenter;

	@BeforeEach
	void setUp() {
		ProjectSessionState.resetForTests();
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		layerManager = new BuildLayerManager(new StageObjectSystem());
		audioLoader = new RecordingAudioLoader();
		presenter = createPresenter(() -> timeline, () -> audioLoader);
		ProjectSessionState.get().bindFromTimeline(timeline);
		ProjectSessionState.get().markClean();
	}

	@AfterEach
	void tearDown() {
		ProjectSessionState.resetForTests();
	}

	@Test
	void importAudioRejectsEmptyPath() {
		var result = presenter.importAudio("  ");
		assertFalse(result.ok());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.path_empty"), result.messageOrEmpty());
	}

	@Test
	void importAudioFailsWhenLoaderReturnsFalse() {
		audioLoader.nextResult = false;
		var result = presenter.importAudio("C:/music/bad.wav");
		assertFalse(result.ok());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.import_failed"), result.messageOrEmpty());
	}

	@Test
	void importAudioFailsWhenLoaderUnavailable() {
		var missingLoader = createPresenter(() -> timeline, () -> null);
		assertFalse(missingLoader.importAudio("C:/music/test.wav").ok());
	}

	@Test
	void importAudioDelegatesToLoaderAndMarksDirty() {
		audioLoader.nextResult = true;
		assertFalse(presenter.isDirty());
		var result = presenter.importAudio("C:/music/test.wav");
		assertTrue(result.ok());
		assertEquals("C:/music/test.wav", audioLoader.lastPath);
		assertTrue(presenter.isDirty());
	}

	@Test
	void saveProjectRejectsEmptyPath() {
		assertFalse(presenter.saveProject("").ok());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.path_empty"), presenter.saveProject("").messageOrEmpty());
	}

	@Test
	void saveProjectPersistsFileAndClearsDirty(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("show.osc");
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(presenter.isDirty());
		var result = presenter.saveAs(file.toString());
		assertTrue(result.ok());
		assertTrue(Files.exists(file));
		assertEquals(file.toString(), presenter.defaultSaveProjectPath());
		assertFalse(presenter.isDirty());
	}

	@Test
	void saveWithoutPathRoutesToSaveAs() {
		assertFalse(presenter.hasProjectPath());
		var outcome = presenter.save();
		assertEquals(com.beatblock.timeline.project.ProjectSessionController.SaveRoute.NEEDS_SAVE_AS, outcome.route());
	}

	@Test
	void openProjectRejectsEmptyPath() {
		var result = presenter.openProject(" ");
		assertFalse(result.ok());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.path_empty"), result.messageOrEmpty());
	}

	@Test
	void openProjectFailsWhenTimelineMissing(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("demo.osc");
		OscProjectStore.save(file, timeline);
		var missingTimeline = createPresenter(() -> null, () -> audioLoader);
		assertFalse(missingTimeline.openProject(file.toString()).ok());
	}

	@Test
	void undoDelegatesToEditorPresenter() {
		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO,
			new TimelineAnimationEvent("ev-undo", 2.0, 1.0, "pulse", "stage", 1f, Map.of())));
		assertTrue(presenter.undoRedoState().canUndo());
		assertTrue(presenter.undo());
		assertFalse(presenter.undoRedoState().canUndo());
	}

	@Test
	void defaultSaveProjectPathReadsMetadata() {
		timeline.setMetadata("projectPath", "D:/proj/show.osc");
		assertEquals("D:/proj/show.osc", presenter.defaultSaveProjectPath());
	}

	@Test
	void editViewStateIsDisabledWithoutTimelineSelection() {
		var state = presenter.editViewState();
		assertFalse(state.hasSelection());
		assertFalse(state.hasClipboard());
		assertFalse(state.canDelete());
	}

	@Test
	void openProjectClearsUndoHistory(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("demo.osc");
		OscProjectStore.save(file, timeline);

		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO,
			new TimelineAnimationEvent("ev1", 1.0, 1.0, "build", "stage", 1f, Map.of())));
		assertTrue(presenter.undoRedoState().canUndo());

		var result = presenter.openProject(file.toString());
		assertTrue(result.ok());
		assertFalse(presenter.undoRedoState().canUndo());
		assertFalse(presenter.undoRedoState().canRedo());
		assertFalse(presenter.isDirty());
	}

	@Test
	void openProjectReportsAudioLoadFailure(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("audio-failure.osc");
		timeline.setMetadata("audioPath", "C:/missing/audio.wav");
		OscProjectStore.save(file, timeline);
		audioLoader.nextResult = false;

		var result = presenter.openProject(file.toString());

		assertTrue(result.ok());
		assertEquals(com.beatblock.ui.i18n.BBTexts.get(
			"beatblock.message.project_opened_audio_failed"), result.messageOrEmpty());
		assertEquals("C:/missing/audio.wav", audioLoader.lastPath);
		assertFalse(presenter.isDirty());
	}

	@Test
	void openCorruptProjectLeavesCurrentDocument(@TempDir Path tempDir) throws Exception {
		timeline.setName("KeepMe");
		timeline.setDurationSeconds(9);
		Path corrupt = tempDir.resolve("corrupt.osc");
		Files.writeString(corrupt, "%%%");

		var result = presenter.openProject(corrupt.toString());
		assertFalse(result.ok());
		assertEquals("KeepMe", timeline.getName());
		assertEquals(9.0, timeline.getDurationSeconds(), 1e-9);
	}

	@Test
	void newProjectResetsIdentityAndClearsDirty() {
		timeline.setMetadata("projectPath", "D:/old.osc");
		timeline.setMetadata("audioPath", "D:/old.wav");
		timeline.setName("OldShow");
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(presenter.isDirty());

		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO,
			new TimelineAnimationEvent("ev-old", 1.0, 1.0, "pulse", "stage", 1f, Map.of())));
		assertTrue(presenter.undoRedoState().canUndo());

		var result = presenter.newProject();
		assertTrue(result.ok());
		assertFalse(presenter.isDirty());
		assertFalse(presenter.undoRedoState().canUndo());
		assertEquals("", presenter.defaultSaveProjectPath());
		assertTrue(timeline.getMetadata("audioPath") == null
			|| String.valueOf(timeline.getMetadata("audioPath")).isBlank());
		Object id = timeline.getMetadata("projectId");
		assertTrue(id != null && !String.valueOf(id).isBlank());
		assertTrue(timeline.getName() == null || timeline.getName().isBlank());
		assertTrue(timeline.getStageEvents().isEmpty());
	}

	private MenuBarPresenter createPresenter(
		Supplier<Timeline> timelineSupplier,
		Supplier<AudioLoader> loaderSupplier
	) {
		TimelineEditorPresenter editorPresenter = new TimelineEditorPresenter(() -> editor, time -> {});
		RhythmDropPanelPresenter rhythmDrop = new RhythmDropPanelPresenter(
			() -> null, timelineSupplier, () -> editor, () -> null);
		TimelineToolbarActionsPresenter generatedActions = new TimelineToolbarActionsPresenter(
			timelineSupplier, () -> editor, () -> net.minecraft.util.math.Vec3d.ZERO, rhythmDrop);
		TimelineActionDispatcher actions = new TimelineActionDispatcher(
			editorPresenter, () -> editor, generatedActions);
		ProjectSessionController session = new ProjectSessionController(
			timelineSupplier,
			() -> editor,
			() -> layerManager,
			loaderSupplier,
			() -> null
		);
		return new MenuBarPresenter(editorPresenter, actions, session, loaderSupplier);
	}

	private static final class RecordingAudioLoader extends AudioLoader {
		String lastPath;
		boolean nextResult;

		@Override
		public boolean load(String path) {
			lastPath = path;
			return nextResult;
		}
	}
}
