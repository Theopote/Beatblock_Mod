package com.beatblock.timeline.project;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSessionStateTest {

	@BeforeEach
	void setUp() {
		ProjectSessionState.resetForTests();
	}

	@AfterEach
	void tearDown() {
		ProjectSessionState.resetForTests();
	}

	@Test
	void notifierMarksSessionDirty() {
		assertFalse(ProjectSessionState.get().isDirty());
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		assertTrue(ProjectSessionState.get().isDirty());
	}

	@Test
	void saveClearsDirtyWithoutClearingUndoConcept(@TempDir Path tempDir) {
		ProjectSessionState session = ProjectSessionState.get();
		session.markDocumentEdited();
		assertTrue(session.isDirty());
		Path path = tempDir.resolve("a.osc");
		session.syncIdentity("id-1", path.toString());
		session.markSaved(path);
		assertFalse(session.isDirty());
		assertEquals("id-1", session.projectId());
		assertEquals(path.toAbsolutePath().normalize(), session.projectPath());
	}

	@Test
	void openFailureKeepsLiveTimelineUntouched(@TempDir Path tempDir) throws Exception {
		Timeline live = Timeline.createDefault();
		live.setName("ProjectA");
		live.setDurationSeconds(12);
		live.setMetadata("projectId", "A");
		live.setMetadata("projectPath", "A.osc");
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());

		Path corrupt = tempDir.resolve("bad.osc");
		Files.writeString(corrupt, "{ not-json");

		boolean failed = false;
		try {
			ProjectDocumentLoader.openTransactional(corrupt, live, layers);
		} catch (Exception expected) {
			failed = true;
		}
		assertTrue(failed);
		assertEquals("ProjectA", live.getName());
		assertEquals(12.0, live.getDurationSeconds(), 1e-9);
		assertEquals("A", String.valueOf(live.getMetadata("projectId")));
	}

	@Test
	void openSuccessReplacesLiveDocument(@TempDir Path tempDir) throws Exception {
		Timeline source = Timeline.createDefault();
		source.setName("ProjectB");
		source.setDurationSeconds(8);
		source.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev-b", 1.0, 1.0, "pulse", "stage", 1f, Map.of()));
		Path file = tempDir.resolve("b.osc");
		OscProjectStore.save(file, source);

		Timeline live = Timeline.createDefault();
		live.setName("ProjectA");
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());

		var result = ProjectDocumentLoader.openTransactional(file, live, layers);
		assertTrue(result.committed());
		assertEquals("ProjectB", live.getName());
		assertEquals(8.0, live.getDurationSeconds(), 1e-9);
		assertFalse(live.getStageEvents().isEmpty());
	}
}
