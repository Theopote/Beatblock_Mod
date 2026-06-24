package com.beatblock.timeline.camera;

import com.beatblock.timeline.CameraKeyframe;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.interaction.DragController;
import com.beatblock.timeline.interaction.TimelineCameraClipResizeHandler;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.util.SnapSystem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REFACTOR 4.3 验收：相机轨与方块/动画轨共享时间刻度、吸附与视图映射。
 */
class CameraTrackAlignmentAcceptanceTest {

	@Test
	void cameraKeyframeListUsesEventTimeSeconds() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0, 10);
		TimelineOperations.addEvent(clip, 3.75, EventType.CAMERA_KEYFRAME, Map.of());

		List<CameraKeyframe> keyframes = timeline.getCameraKeyframes();

		assertEquals(1, keyframes.size());
		assertEquals(3.75, keyframes.getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void animationAndCameraEventsShareSecondsTimeBase() {
		Timeline timeline = Timeline.createDefault();
		double t = 2.25;

		Clip camClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0, 8);
		TimelineEvent camEvent = TimelineOperations.addEvent(
			camClip, t, EventType.CAMERA_KEYFRAME, Map.of());

		Clip animClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 8);
		TimelineOperations.addEvent(animClip, t, EventType.ANIMATION, Map.of("type", "block_jump"));

		List<TimelineAnimationEvent> animEvents = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);

		assertEquals(t, camEvent.getTimeSeconds(), 1e-9);
		assertEquals(t, timeline.getCameraKeyframes().getFirst().getTimeSeconds(), 1e-9);
		assertEquals(t, animEvents.getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void sharedViewStateMapsSameTimeToSameScreenXAcrossTracks() {
		TimelineViewState view = new TimelineViewState();
		view.setViewStartTimeSeconds(0);
		view.setZoom(100f);

		double time = 4.5;
		float camX = view.timeToScreen(time);
		float animX = view.timeToScreen(time);

		assertEquals(camX, animX, 1e-3f);
		assertEquals(time, view.screenToTime(camX), 1e-9);
	}

	@Test
	void magnetSnapAlignsCameraKeyframeToAnimationEvent() {
		Timeline timeline = Timeline.createDefault();
		Clip animClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 10);
		TimelineOperations.addEvent(animClip, 2.0, EventType.ANIMATION, Map.of());

		Clip camClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0, 10);
		TimelineEvent camEvent = TimelineOperations.addEvent(
			camClip, 1.5, EventType.CAMERA_KEYFRAME, Map.of());

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setMagnetSnap(true);
		TimelineViewState view = new TimelineViewState();
		InteractionState interaction = new InteractionState();

		double snapped = DragController.computeEventDragTime(
			1.98, camEvent.getId(), 60, timeline, toolbar, view, interaction);

		assertEquals(2.0, snapped, 1e-9);
		assertEquals(1, interaction.getAlignmentGuideTimes().length);
		assertEquals(2.0, interaction.getAlignmentGuideTimes()[0], 1e-9);
	}

	@Test
	void cameraClipDragStartUsesSnapSystem() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new com.beatblock.timeline.TimelineMarker("m", 5.0, "M", com.beatblock.timeline.MarkerType.GENERIC));
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 4.0);

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setMagnetSnap(true);
		TimelineViewState view = new TimelineViewState();
		InteractionState interaction = new InteractionState();

		double newStart = DragController.dragClip(
			timeline,
			Timeline.TRACK_ID_CAMERA,
			clip.getId(),
			5.04,
			1.0,
			1.0,
			3.0,
			60,
			toolbar,
			view,
			interaction
		);

		assertEquals(5.0, newStart, 1e-9);
		assertEquals(5.0, clip.getStartTimeSeconds(), 1e-9);
	}

	@Test
	void cameraClipResizeUsesSnapTime() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 3.0);
		var session = TimelineCameraClipResizeHandler.beginSession(timeline, clip, clip.getId());

		InteractionState state = new InteractionState();
		state.setActiveClipId(clip.getId());
		state.setResizeLeft(false);

		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;
		layout.contentWidth = 800f;

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToBeat(true);

		float mx = layout.contentLeft + viewState.timeToScreen(2.02);
		TimelineCameraClipResizeHandler.applyDuringDrag(
			timeline, session, state, viewState, toolbar, layout, mx);

		assertEquals(2.0, clip.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void cameraRowLivesInActionGroupWithAnimationRows() {
		assertEquals(TimelineTrackMeta.ROW_ACTION_GROUP, TimelineTrackMeta.getParentRowIndex(TimelineTrackMeta.ROW_CAMERA));
		assertTrue(TimelineTrackMeta.ROW_ANIM_BLOCK < TimelineTrackMeta.ROW_CAMERA);
		assertTrue(TimelineTrackMeta.ROW_CAMERA < TimelineTrackMeta.ROW_GLOBAL_EVENT);
	}

	@Test
	void snapSystemMagnetIncludesCameraClipEventsAndEdges() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 6.0, 8.0);
		TimelineOperations.addEvent(clip, 7.0, EventType.CAMERA_KEYFRAME, Map.of());

		assertEquals(7.0, SnapSystem.snap(6.93, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(6.0, SnapSystem.snap(5.94, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(8.0, SnapSystem.snap(8.06, timeline, false, 0, false, 0, true, null), 1e-9);
	}

	@Test
	void draggingCameraKeyframeDoesNotSnapToSelf() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 3.0, EventType.CAMERA_KEYFRAME, Map.of());

		double snapped = SnapSystem.snap(2.95, timeline, false, 0, false, 0, true, event.getId());
		assertEquals(2.95, snapped, 1e-9);
	}

	@Test
	void cameraAndAnimationShareSnapThresholdConstant() {
		assertFalse(Double.isNaN(SnapSystem.SNAP_THRESHOLD_SECONDS));
		assertTrue(SnapSystem.SNAP_THRESHOLD_SECONDS > 0);
	}
}
