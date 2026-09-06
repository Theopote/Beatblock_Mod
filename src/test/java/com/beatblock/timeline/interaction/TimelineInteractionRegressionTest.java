package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 interaction regressions: camera right-resize keyframe restore
 * and event drag clip-range clamp.
 */
class TimelineInteractionRegressionTest {

	@Test
	void cameraRightResizeRestoresKeyframesFromGestureOriginalsWhenExpandedAgain() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 10.0);
		TimelineEvent k1 = TimelineOperations.addEvent(clip, 2.0, EventType.CAMERA_KEYFRAME, Map.of());
		TimelineEvent k2 = TimelineOperations.addEvent(clip, 6.0, EventType.CAMERA_KEYFRAME, Map.of());
		TimelineEvent k3 = TimelineOperations.addEvent(clip, 9.0, EventType.CAMERA_KEYFRAME, Map.of());

		TimelineCameraClipResizeHandler.Session session =
			TimelineCameraClipResizeHandler.beginSession(timeline, clip, clip.getId());

		InteractionState state = new InteractionState();
		state.setActiveClipId(clip.getId());
		state.setResizeLeft(false);

		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;
		layout.contentWidth = 1200f;
		TimelineToolbarState toolbar = snapDisabled();

		applyRightResize(timeline, session, state, viewState, layout, toolbar, 5.0);
		assertEquals(5.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(2.0, k1.getTimeSeconds(), 1e-9);
		assertEquals(5.0, k2.getTimeSeconds(), 1e-9);
		assertEquals(5.0, k3.getTimeSeconds(), 1e-9);

		applyRightResize(timeline, session, state, viewState, layout, toolbar, 8.0);
		assertEquals(8.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(2.0, k1.getTimeSeconds(), 1e-9);
		assertEquals(6.0, k2.getTimeSeconds(), 1e-9);
		assertEquals(8.0, k3.getTimeSeconds(), 1e-9);
	}

	@Test
	void eventDragClampsToParentClipRangeNotTimelineDuration() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 10.0, 20.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 15.0, EventType.CAMERA_KEYFRAME, Map.of());

		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.DRAG_EVENT);
		interaction.setActiveTrackId(Timeline.TRACK_ID_CAMERA);
		interaction.setActiveClipId(clip.getId());
		interaction.setActiveEventId(event.getId());

		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;

		float mxPastClip = layout.contentLeft + viewState.timeToScreen(40.0);
		TimelineEventDragHandler.applyDuringDrag(
			timeline, interaction, null, viewState, layout, snapDisabled(), mxPastClip);

		assertEquals(20.0, event.getTimeSeconds(), 1e-9);
		assertEquals(10.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(20.0, clip.getEndTimeSeconds(), 1e-9);
	}

	private static void applyRightResize(
		Timeline timeline,
		TimelineCameraClipResizeHandler.Session session,
		InteractionState state,
		TimelineViewState viewState,
		TimelineLayout layout,
		TimelineToolbarState toolbar,
		double endTimeSeconds
	) {
		float mx = layout.contentLeft + viewState.timeToScreen(endTimeSeconds);
		TimelineCameraClipResizeHandler.applyDuringDrag(
			timeline, session, state, viewState, toolbar, layout, mx);
	}

	private static TimelineToolbarState snapDisabled() {
		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);
		return toolbar;
	}
}
