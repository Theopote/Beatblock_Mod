package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineClipOrigin;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class CameraCreatorPanelPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private StageObjectSystem stageObjects;
	private CameraCreatorPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		timeline.setDurationSeconds(60.0);
		editor.getClock().setDurationSeconds(60.0);
		stageObjects = context.blockAnimationEngine().getStageObjectSystem();
		stageObjects.clear();
		stageObjects.register(StageObjectSystem.fromBlocks(
			"solo-stage", "Solo", List.of(new BlockPos(0, 64, 0))));

		presenter = new CameraCreatorPanelPresenter(
			() -> timeline,
			() -> editor,
			() -> stageObjects,
			context::blockAnimationEngine,
			() -> null
		);
	}

	@Test
	void createShotWritesManualCameraClipForSubject() {
		editor.getClock().setCurrentTimeSeconds(12.5);
		presenter.setSelectedSubjectId("solo-stage");
		presenter.setFraming(CameraShotFraming.MEDIUM);
		presenter.setMovement(CameraShotMovement.PUSH_IN);
		presenter.setDurationSeconds(3.0);

		var outcome = presenter.createShot();

		assertTrue(outcome.success(), outcome.message());
		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertEquals(1, cameraTrack.getClips().size());
		var clip = cameraTrack.getClips().getFirst();
		assertEquals(12.5, clip.getStartTimeSeconds(), 1e-6);

		var meta = TimelineClipOrigin.metadataFromClip(clip, Timeline.TRACK_ID_CAMERA);
		assertEquals(TimelineEventOrigin.MANUAL, meta.origin());

		var segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		assertNotNull(segment);
		assertEquals(
			TimelineEventOrigin.MANUAL.name(),
			String.valueOf(segment.getParameter(TimelineGenerationMetadataSupport.PARAM_ORIGIN))
		);
	}

	@Test
	void createShotFailsWithoutTimeline() {
		presenter = new CameraCreatorPanelPresenter(() -> null, () -> null, () -> stageObjects);
		var outcome = presenter.createShot();
		assertFalse(outcome.success());
		assertEquals(BBTexts.get("beatblock.common.timeline_not_initialized"), outcome.message());
	}

	@Test
	void createShotFailsWhenSubjectMissing() {
		stageObjects.clear();
		presenter.setSelectedSubjectId("gone");
		presenter.setMovement(CameraShotMovement.ORBIT);

		var outcome = presenter.createShot();
		assertFalse(outcome.success());
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
	}

	@Test
	void viewStateExposesPlayheadAndSubjects() {
		editor.getClock().setCurrentTimeSeconds(4.25);
		var state = presenter.viewState();
		assertTrue(state.editorReady());
		assertEquals(4.25, state.playheadSeconds(), 1e-6);
		assertTrue(state.subjects().stream().anyMatch(s -> "solo-stage".equals(s.id())));
	}
}
