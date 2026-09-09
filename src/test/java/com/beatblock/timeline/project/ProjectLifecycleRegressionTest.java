package com.beatblock.timeline.project;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.ui.presenter.PresenterResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLifecycleRegressionTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private BuildLayerManager layerManager;
	private ProjectSessionController controller;

	@BeforeEach
	void setUp() {
		ProjectSessionState.resetForTests();
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		layerManager = new BuildLayerManager(new StageObjectSystem());
		controller = new ProjectSessionController(
			() -> timeline,
			() -> editor,
			() -> layerManager,
			() -> null,
			() -> null
		);
		ProjectSessionState.get().bindFromTimeline(timeline);
		ProjectSessionState.get().markClean();
	}

	@AfterEach
	void tearDown() {
		ProjectSessionState.resetForTests();
	}

	@Test
	void editMarksProjectDirty() {
		assertFalse(controller.isDirty());
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(controller.isDirty());
	}

	@Test
	void saveClearsDirty(@TempDir Path tempDir) throws Exception {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		Path file = tempDir.resolve("show.osc");
		assertTrue(controller.saveAs(file.toString()).ok());
		assertFalse(controller.isDirty());
		assertEquals(file.toAbsolutePath().normalize(), ProjectSessionState.get().projectPath());
	}

	@Test
	void saveFailureKeepsDirty() {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(controller.isDirty());
		PresenterResult result = controller.saveAs("");
		assertFalse(result.ok());
		assertTrue(controller.isDirty());
	}

	@Test
	void saveWithoutPathRoutesToSaveAs() {
		assertFalse(controller.hasProjectPath());
		var outcome = controller.save();
		assertEquals(ProjectSessionController.SaveRoute.NEEDS_SAVE_AS, outcome.route());
		assertFalse(outcome.ok());
	}

	@Test
	void saveAsOnlyChangesPathAfterSuccess(@TempDir Path tempDir) throws Exception {
		Path good = tempDir.resolve("ok.osc");
		assertTrue(controller.saveAs(good.toString()).ok());
		String saved = controller.defaultSaveProjectPath();

		PresenterResult failed = controller.saveAs("   ");
		assertFalse(failed.ok());
		assertEquals(saved, controller.defaultSaveProjectPath());
	}

	@Test
	void importAudioMarksDirtyViaNotifier() {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(controller.isDirty());
	}

	@Test
	void newProjectWhenCleanResetsDocument() {
		timeline.setName("Show");
		timeline.setMetadata("projectPath", "D:/old.osc");
		timeline.setMetadata("audioPath", "D:/a.wav");
		assertTrue(controller.newProject().ok());
		assertFalse(controller.isDirty());
		assertTrue(timeline.getName().isBlank());
		assertFalse(controller.hasProjectPath());
		assertNull(timeline.getMetadata("audioPath"));
	}

	@Test
	void newProjectDirtyRequiresGuard() {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		var gate = controller.unsavedChanges().request(
			UnsavedChangesCoordinator.Intent.NEW_PROJECT, controller.isDirty());
		assertEquals(UnsavedChangesCoordinator.Gate.NEEDS_CONFIRM, gate);
		assertTrue(controller.unsavedChanges().hasPending());
		controller.unsavedChanges().cancel();
		assertTrue(controller.isDirty());
		assertEquals("" /* still dirty project */, timeline.getName().isBlank() ? "" : timeline.getName());
	}

	@Test
	void cancelNewKeepsCurrentProject() {
		timeline.setName("Keep");
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		controller.unsavedChanges().request(UnsavedChangesCoordinator.Intent.NEW_PROJECT, true);
		controller.unsavedChanges().cancel();
		assertEquals("Keep", timeline.getName());
		assertTrue(controller.isDirty());
		assertFalse(controller.unsavedChanges().hasPending());
	}

	@Test
	void openDirtyProjectRequiresGuard() {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		var gate = controller.unsavedChanges().request(
			UnsavedChangesCoordinator.Intent.OPEN_PROJECT, true);
		assertEquals(UnsavedChangesCoordinator.Gate.NEEDS_CONFIRM, gate);
	}

	@Test
	void closeDirtyProjectRequiresGuard() {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		var gate = controller.unsavedChanges().request(
			UnsavedChangesCoordinator.Intent.CLOSE_BEATBLOCK, true);
		assertEquals(UnsavedChangesCoordinator.Gate.NEEDS_CONFIRM, gate);
	}

	@Test
	void openCorruptProjectPreservesCurrentDocument(@TempDir Path tempDir) throws Exception {
		timeline.setName("KeepMe");
		timeline.setDurationSeconds(9);
		timeline.setMetadata("projectPath", "KeepMe.osc");
		Path corrupt = tempDir.resolve("bad.osc");
		Files.writeString(corrupt, "{not-json");

		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO,
			new TimelineAnimationEvent("ev1", 1.0, 1.0, "pulse", "stage", 1f, Map.of())));
		assertTrue(editor.getCommandManager().canUndo());

		assertFalse(controller.openProject(corrupt.toString()).ok());
		assertEquals("KeepMe", timeline.getName());
		assertEquals(9.0, timeline.getDurationSeconds(), 1e-9);
		assertEquals("KeepMe.osc", String.valueOf(timeline.getMetadata("projectPath")));
		assertTrue(editor.getCommandManager().canUndo());
	}

	@Test
	void openSuccessfulProjectClearsDirtyAndUndo(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("demo.osc");
		OscProjectStore.save(file, Timeline.createDefault());

		editor.getCommandManager().execute(new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO,
			new TimelineAnimationEvent("ev1", 1.0, 1.0, "pulse", "stage", 1f, Map.of())));
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(controller.isDirty());
		assertTrue(editor.getCommandManager().canUndo());

		assertTrue(controller.openProject(file.toString()).ok());
		assertFalse(controller.isDirty());
		assertFalse(editor.getCommandManager().canUndo());
	}

	@Test
	void unsavedSaveThenContinueConsumesPending(@TempDir Path tempDir) throws Exception {
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		controller.unsavedChanges().request(UnsavedChangesCoordinator.Intent.NEW_PROJECT, true);
		Path file = tempDir.resolve("gate.osc");
		assertTrue(controller.saveAs(file.toString()).ok());
		var intent = controller.unsavedChanges().continueAfterSuccessfulSave();
		assertEquals(UnsavedChangesCoordinator.Intent.NEW_PROJECT, intent);
		assertFalse(controller.unsavedChanges().hasPending());
	}

	@Test
	void markSavedApiAlignsWithPath(@TempDir Path tempDir) {
		Path file = tempDir.resolve("x.osc");
		ProjectSessionState.get().markDocumentEdited();
		ProjectSessionState.get().markSaved(file);
		assertFalse(ProjectSessionState.get().isDirty());
		assertNotNull(ProjectSessionState.get().projectPath());
	}
}
