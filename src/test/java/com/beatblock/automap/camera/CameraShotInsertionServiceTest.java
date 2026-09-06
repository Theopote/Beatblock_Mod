package com.beatblock.automap.camera;

import com.beatblock.BeatBlock;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineClipOrigin;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.SelectionState;
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
class CameraShotInsertionServiceTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commandManager;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		commandManager = editor.getCommandManager();
		commandManager.clear();
		timeline.setDurationSeconds(60.0);
		editor.getClock().setDurationSeconds(60.0);
		editor.getClock().setCurrentTimeSeconds(0.0);
		var camera = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (camera != null) {
			List.copyOf(camera.getClips()).forEach(c -> camera.removeClip(c.getId()));
		}
		StageObjectSystem stageObjects = context.blockAnimationEngine().getStageObjectSystem();
		stageObjects.clear();
		stageObjects.register(StageObjectSystem.fromBlocks(
			"solo-stage", "Solo", List.of(new BlockPos(0, 64, 0))));
	}

	@Test
	void insertManualShotIsOneUndoSelectsClipAndTagsManual() {
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent("stale");
		selection.selectClip("stale-clip");

		CameraShot shot = new CameraShot(
			12.5,
			3.0,
			CameraSubject.stageObject("solo-stage"),
			CameraShotFraming.MEDIUM,
			CameraShotMovement.PUSH_IN,
			null,
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			-1
		);

		var result = CameraShotInsertionService.insertManualShot(timeline, editor, shot);

		assertTrue(result.written());
		assertEquals(1, commandManager.undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.create_camera_shot"),
			commandManager.undoDescriptionsNewestFirst().getFirst()
		);

		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertEquals(1, cameraTrack.getClips().size());
		String clipId = cameraTrack.getClips().getFirst().getId();
		assertEquals(clipId, result.clipId());
		assertTrue(selection.isClipSelected(clipId));
		assertFalse(selection.isEventSelected("stale"));
		var segment = CameraTrackFactory.findSegmentHeadEvent(cameraTrack.getClips().getFirst());
		assertNotNull(segment);
		assertTrue(selection.isEventSelected(segment.getId()));

		var meta = TimelineClipOrigin.metadataFromClip(cameraTrack.getClips().getFirst(), Timeline.TRACK_ID_CAMERA);
		assertEquals(TimelineEventOrigin.MANUAL, meta.origin());
		assertEquals(
			TimelineEventOrigin.MANUAL.name(),
			String.valueOf(segment.getParameter(TimelineGenerationMetadataSupport.PARAM_ORIGIN))
		);

		commandManager.undo();
		assertEquals(0, cameraTrack.getClips().size());
		assertEquals(0, commandManager.undoCount());
	}

	@Test
	void insertManualPoseDraftUndoesAndRedoes() {
		double durationBefore = timeline.getDurationSeconds();
		CameraShotDraft draft = CameraShotDraft.fromLivePose(
			2.0,
			CameraShotInsertionService.DEFAULT_PATH_DURATION_SECONDS,
			CameraShotMovement.HOLD,
			new CapturedCameraPose(10, 70, 20, 45, -10)
		);

		var result = CameraShotInsertionService.insertManualDraft(timeline, editor, draft);

		assertTrue(result.written());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertTrue(timeline.getDurationSeconds() >= 6.0);

		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		var segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		assertNotNull(segment);
		assertEquals(CameraSegmentKind.PATH, CameraSegmentKind.fromParam(segment.getParameters().get("kind")));
		assertEquals(
			TimelineEventOrigin.MANUAL.name(),
			String.valueOf(segment.getParameter(TimelineGenerationMetadataSupport.PARAM_ORIGIN))
		);

		commandManager.undo();
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(durationBefore, timeline.getDurationSeconds(), 1e-6);

		commandManager.redo();
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
	}
}
