package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CapturedCameraPose;
import com.beatblock.client.camera.CameraCreatorVisualization;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
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
import java.util.concurrent.atomic.AtomicReference;

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
	private final AtomicReference<CapturedCameraPose> poseRef = new AtomicReference<>(
		new CapturedCameraPose(5, 68, 1, 30, -5));

	@BeforeEach
	void setUp() {
		CameraCreatorVisualization.resetForTests();
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		editor.getCommandManager().clear();
		timeline.setDurationSeconds(60.0);
		editor.getClock().setDurationSeconds(60.0);
		editor.getSelectionState().clearAll();
		var cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam != null) {
			List.copyOf(cam.getClips()).forEach(c -> cam.removeClip(c.getId()));
		}
		stageObjects = context.blockAnimationEngine().getStageObjectSystem();
		stageObjects.clear();
		stageObjects.register(StageObjectSystem.fromBlocks(
			"solo-stage", "Solo", List.of(new BlockPos(0, 64, 0))));

		presenter = new CameraCreatorPanelPresenter(
			() -> timeline,
			() -> editor,
			() -> stageObjects,
			context::blockAnimationEngine,
			() -> null,
			poseRef::get
		);
	}

	@Test
	void createShotWritesManualCameraClipForSubject() {
		editor.getClock().setCurrentTimeSeconds(12.5);
		presenter.setSelectedSubjectId("solo-stage");
		presenter.setFraming(CameraShotFraming.MEDIUM);
		presenter.setMovement(CameraShotMovement.PUSH_IN);
		presenter.setDurationSeconds(3.0);

		editor.getSelectionState().selectClip("stale-clip");
		var outcome = presenter.createShot();

		assertTrue(outcome.success(), outcome.message());
		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertEquals(1, cameraTrack.getClips().size());
		var clip = cameraTrack.getClips().getFirst();
		assertEquals(12.5, clip.getStartTimeSeconds(), 1e-6);
		assertEquals(1, editor.getCommandManager().undoCount());
		assertTrue(editor.getSelectionState().isClipSelected(clip.getId()));
		assertFalse(editor.getSelectionState().isClipSelected("stale-clip"));
		var segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		assertNotNull(segment);
		assertTrue(editor.getSelectionState().isEventSelected(segment.getId()));

		var meta = TimelineClipOrigin.metadataFromClip(clip, Timeline.TRACK_ID_CAMERA);
		assertEquals(TimelineEventOrigin.MANUAL, meta.origin());

		assertEquals(
			TimelineEventOrigin.MANUAL.name(),
			String.valueOf(segment.getParameter(TimelineGenerationMetadataSupport.PARAM_ORIGIN))
		);

		editor.getCommandManager().undo();
		assertEquals(0, cameraTrack.getClips().size());
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
		assertTrue(state.showCameraPath());
		assertFalse(state.showFrustum());
		assertFalse(state.showSubjectBounds());
	}

	@Test
	void visualizationToolbarUpdatesSessionStoreAndSubjectBounds() {
		presenter.setShowCameraPath(false);
		presenter.setShowFrustum(true);
		presenter.setShowSubjectBounds(true);
		presenter.setSelectedSubjectId("solo-stage");

		var state = presenter.viewState();
		assertFalse(state.showCameraPath());
		assertTrue(state.showFrustum());
		assertTrue(state.showSubjectBounds());
		assertEquals(CameraSubject.stageObject("solo-stage"), CameraCreatorVisualization.subjectForBounds());
	}

	@Test
	void captureCurrentViewCreatesPathWhenNothingSelected() {
		editor.getClock().setCurrentTimeSeconds(3.0);
		var outcome = presenter.captureCurrentView();

		assertTrue(outcome.success(), outcome.message());
		assertEquals(1, editor.getCommandManager().undoCount());
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		assertTrue(editor.getSelectionState().isClipSelected(clip.getId()));
		assertFalse(editor.getSelectionState().getSelectedEvents().isEmpty());
		assertEquals(BBTexts.get("beatblock.camera_creator.captured_path"), outcome.message());
	}

	@Test
	void captureCurrentViewAddsKeyframeWhenPathSelected() {
		CameraTrackFactory.addPathSegment(timeline, 0.0, 0, 64, 0, 0, 0);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		editor.getSelectionState().selectClip(clip.getId());
		editor.getCommandManager().clear();
		editor.getClock().setCurrentTimeSeconds(1.25);

		var outcome = presenter.captureCurrentView();

		assertTrue(outcome.success(), outcome.message());
		assertEquals(1, editor.getCommandManager().undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.add_camera_keyframe"),
			editor.getCommandManager().undoDescriptionsNewestFirst().getFirst()
		);
		assertEquals(2, clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME).count());
		assertEquals(BBTexts.get("beatblock.camera_creator.captured_keyframe"), outcome.message());
	}
}
