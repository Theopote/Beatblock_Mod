package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CapturedCameraPose;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class CameraViewCaptureServiceTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commands;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		commands = editor.getCommandManager();
		commands.clear();
		timeline.setDurationSeconds(60.0);
		editor.getClock().setDurationSeconds(60.0);
		editor.getClock().setCurrentTimeSeconds(2.0);
		editor.getSelectionState().clearAll();
		var cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam != null) {
			List.copyOf(cam.getClips()).forEach(c -> cam.removeClip(c.getId()));
		}
	}

	@Test
	void withoutSelectionCreatesPathClipWithOneUndoAndSelectsKeyframe() {
		CapturedCameraPose pose = new CapturedCameraPose(11, 70, -2, 45, -8);

		var result = CameraViewCaptureService.captureCurrentView(timeline, editor, pose);

		assertTrue(result.success(), result.message());
		assertEquals(1, commands.undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.create_camera_shot"),
			commands.undoDescriptionsNewestFirst().getFirst()
		);

		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		assertEquals(result.clipId(), clip.getId());
		assertEquals(2.0, clip.getStartTimeSeconds(), 1e-6);
		var segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		assertNotNull(segment);
		assertEquals(CameraSegmentKind.PATH, CameraSegmentKind.fromParam(segment.getParameters().get("kind")));

		assertTrue(editor.getSelectionState().isEventSelected(result.eventId()));
		assertTrue(editor.getSelectionState().isClipSelected(clip.getId()));

		var keyframe = clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME)
			.findFirst()
			.orElseThrow();
		assertEquals(11.0, ((Number) keyframe.getParameter("x")).doubleValue(), 1e-6);
		assertEquals(45.0, ((Number) keyframe.getParameter("yawDeg")).doubleValue(), 1e-6);

		commands.undo();
		assertEquals(0, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
	}

	@Test
	void withSelectedPathClipAddsKeyframeAsOneUndo() {
		CameraTrackFactory.addPathSegment(timeline, 0.0, 0, 64, 0, 0, 0);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		editor.getSelectionState().selectClip(clip.getId());
		commands.clear();
		editor.getClock().setCurrentTimeSeconds(1.5);

		int keyframesBefore = (int) clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME).count();

		var result = CameraViewCaptureService.captureCurrentView(
			timeline, editor, new CapturedCameraPose(3, 65, 4, 90, 0));

		assertTrue(result.success(), result.message());
		assertEquals(1, commands.undoCount());
		assertEquals(
			BBTexts.get("beatblock.undo.add_camera_keyframe"),
			commands.undoDescriptionsNewestFirst().getFirst()
		);
		assertEquals(keyframesBefore + 1, clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME).count());
		assertTrue(editor.getSelectionState().isEventSelected(result.eventId()));

		commands.undo();
		assertEquals(keyframesBefore, clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME).count());
	}

	@Test
	void withSelectedNonPathClipFails() {
		CameraTrackFactory.addOrbitSegment(timeline, 0.0, 0, 64, 0, 8, 4, 0, 120);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		editor.getSelectionState().selectClip(clip.getId());
		commands.clear();

		var result = CameraViewCaptureService.captureCurrentView(
			timeline, editor, new CapturedCameraPose(0, 64, 0, 0, 0));

		assertFalse(result.success());
		assertEquals(BBTexts.get("beatblock.camera_creator.capture_need_path"), result.message());
		assertEquals(0, commands.undoCount());
	}

	@Test
	void addKeyframeAtTimeUsesCommand() {
		CameraTrackFactory.addPathSegment(timeline, 0.0, 0, 64, 0, 0, 0);
		commands.clear();

		var result = CameraViewCaptureService.addKeyframeAtTime(
			timeline, editor, 2.0, new CapturedCameraPose(1, 66, 2, 10, -5));

		assertTrue(result.success(), result.message());
		assertEquals(1, commands.undoCount());
		commands.undo();
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst().getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME).count());
	}
}
