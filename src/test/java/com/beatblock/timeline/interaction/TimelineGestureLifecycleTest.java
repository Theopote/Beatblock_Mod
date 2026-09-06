package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interaction Preview Mutation ≠ Committed Document Mutation:
 * cancel restores gesture-start snapshot without creating Undo / notifyDocumentEdited.
 */
class TimelineGestureLifecycleTest {

	@Test
	void cancelEventDragRestoresInitialTimeWithoutCommand() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());

		InteractionState interaction = new InteractionState();
		HitResult hit = HitResult.event(Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(), 2.0);
		TimelineEventDragSession session = TimelineEventDragSession.begin(timeline, hit, interaction, 0f, 0f);
		event.setTimeSeconds(7.0);

		AtomicBoolean cleared = new AtomicBoolean();
		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, session, null, null,
			() -> cleared.set(true), null, null);

		assertEquals(2.0, event.getTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.NONE, interaction.getMode());
		assertNull(interaction.getActiveEventId());
		assertTrue(cleared.get());
	}

	@Test
	void cancelClipDragRestoresBoundsAndLinkedEventsFromSnapshot() {
		Timeline timeline = Timeline.createDefault();
		Clip cameraClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 5.0);
		TimelineEvent keyframe = TimelineOperations.addEvent(
			cameraClip, 2.0, EventType.CAMERA_KEYFRAME, Map.of());

		InteractionState interaction = new InteractionState();
		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;

		TimelineClipDragSession session = TimelineClipDragSession.beginCameraClipDrag(
			timeline, Timeline.TRACK_ID_CAMERA, cameraClip.getId(), cameraClip,
			interaction, viewState, layout, 200f, 100f);

		cameraClip.setStartTimeSeconds(3.0);
		cameraClip.setEndTimeSeconds(7.0);
		keyframe.setTimeSeconds(4.0);

		AtomicBoolean cleared = new AtomicBoolean();
		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, null, session, null,
			null, () -> cleared.set(true), null);

		assertEquals(1.0, cameraClip.getStartTimeSeconds(), 1e-9);
		assertEquals(5.0, cameraClip.getEndTimeSeconds(), 1e-9);
		assertEquals(2.0, keyframe.getTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.NONE, interaction.getMode());
		assertTrue(cleared.get());
	}

	@Test
	void cancelCameraResizeRestoresClipAndKeyframesFromSnapshot() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 10.0);
		TimelineEvent keyframe = TimelineOperations.addEvent(clip, 9.0, EventType.CAMERA_KEYFRAME, Map.of());
		var session = TimelineCameraClipResizeHandler.beginSession(timeline, clip, clip.getId());

		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.RESIZE_CLIP);
		interaction.setActiveClipId(clip.getId());
		interaction.setResizeLeft(false);

		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;
		layout.contentWidth = 1200f;
		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);

		float mx = layout.contentLeft + viewState.timeToScreen(5.0);
		TimelineCameraClipResizeHandler.applyDuringDrag(
			timeline, session, interaction, viewState, toolbar, layout, mx);
		assertEquals(5.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(5.0, keyframe.getTimeSeconds(), 1e-9);

		AtomicBoolean cleared = new AtomicBoolean();
		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, null, null, session,
			null, null, () -> cleared.set(true));

		assertEquals(10.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(9.0, keyframe.getTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.NONE, interaction.getMode());
		assertTrue(cleared.get());
	}

	@Test
	void cancelMarkerDragRestoresSnapshotWithoutCommand() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker marker = new TimelineMarker("m1", 2.0, "Cue", MarkerType.GENERIC);
		timeline.addMarker(marker);

		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.MARKER_DRAG);
		interaction.setActiveMarkerId(marker.getId());
		interaction.setMarkerDragBefore(marker);
		interaction.setMarkerDragStartTimeSeconds(2.0);
		timeline.updateMarkerTimeLive(marker.getId(), 6.5);

		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, null, null, null, null, null, null);

		assertEquals(2.0, timeline.getMarkers().getFirst().getTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.NONE, interaction.getMode());
		assertNull(interaction.getActiveMarkerId());
		assertNull(interaction.getMarkerDragBefore());
	}

	@Test
	void cancelIsNoOpWhenNotInDocumentPreviewMode() {
		Timeline timeline = Timeline.createDefault();
		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.BOX_SELECT);

		AtomicBoolean cleared = new AtomicBoolean();
		TimelineGestureLifecycle.cancelLiveDocumentPreview(
			timeline, interaction, null, null, null,
			() -> cleared.set(true), () -> cleared.set(true), () -> cleared.set(true));

		assertEquals(InteractionMode.BOX_SELECT, interaction.getMode());
		assertTrue(!cleared.get());
	}
}
