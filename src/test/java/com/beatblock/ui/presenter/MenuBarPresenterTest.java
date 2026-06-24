package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioLoader;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import com.beatblock.timeline.project.OscProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		layerManager = new BuildLayerManager(new StageObjectSystem());
		audioLoader = new RecordingAudioLoader();
		presenter = new MenuBarPresenter(
			new TimelineEditorPresenter(() -> editor, time -> {}),
			() -> timeline,
			() -> editor,
			() -> layerManager,
			() -> audioLoader
		);
	}

	@Test
	void importAudioRejectsEmptyPath() {
		var result = presenter.importAudio("  ");
		assertFalse(result.ok());
	}

	@Test
	void importAudioDelegatesToLoader() {
		audioLoader.nextResult = true;
		var result = presenter.importAudio("C:/music/test.wav");
		assertTrue(result.ok());
		assertEquals("C:/music/test.wav", audioLoader.lastPath);
	}

	@Test
	void saveProjectRejectsEmptyPath() {
		assertFalse(presenter.saveProject("").ok());
	}

	@Test
	void defaultSaveProjectPathReadsMetadata() {
		timeline.setMetadata("projectPath", "D:/proj/show.osc");
		assertEquals("D:/proj/show.osc", presenter.defaultSaveProjectPath());
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
