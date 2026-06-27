package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventDragHandlerTest {

	@Test
	void beginCapturesInitialEventTime() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 2.5, EventType.ANIMATION, Map.of());
		HitResult hit = HitResult.event(
			Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(), 2.5);

		InteractionState interaction = new InteractionState();
		TimelineEventDragSession session = TimelineEventDragHandler.tryBeginFromHit(
			timeline, hit, interaction, new SelectionState(), 100f, 50f, false, false);

		assertNotNull(session);
		assertEquals(2.5, session.initialTimeSeconds(), 1e-9);
		assertEquals(InteractionMode.DRAG_EVENT, interaction.getMode());
		assertEquals(event.getId(), interaction.getActiveEventId());
	}

	@Test
	void finishBelowThresholdRevertsDraggedTime() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());
		HitResult hit = HitResult.event(
			Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(), 2.0);

		InteractionState interaction = new InteractionState();
		TimelineEventDragSession session = TimelineEventDragHandler.tryBeginFromHit(
			timeline, hit, interaction, new SelectionState(), 100f, 50f, false, false);
		event.setTimeSeconds(5.0);

		TimelineEventDragHandler.finishOnMouseRelease(
			timeline, null, session, interaction, new SelectionState(), 100f, 50f);

		assertEquals(2.0, event.getTimeSeconds(), 1e-9);
	}

	@Test
	void applyDuringDragMovesEventWithinDuration() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10.0);
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		InteractionState interaction = new InteractionState();
		interaction.setMode(InteractionMode.DRAG_EVENT);
		interaction.setActiveTrackId(Timeline.TRACK_ID_ANIMATION_AUTO);
		interaction.setActiveClipId(clip.getId());
		interaction.setActiveEventId(event.getId());

		com.beatblock.timeline.editor.TimelineViewState viewState = new com.beatblock.timeline.editor.TimelineViewState();
		viewState.setZoom(100f);
		com.beatblock.timeline.rendering.TimelineLayout layout = new com.beatblock.timeline.rendering.TimelineLayout();
		layout.contentLeft = 0f;

		com.beatblock.timeline.rendering.TimelineToolbarState toolbar = new com.beatblock.timeline.rendering.TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);

		float mx = layout.contentLeft + viewState.timeToScreen(4.0);
		TimelineEventDragHandler.applyDuringDrag(
			timeline, interaction, null, viewState, layout, toolbar, 10.0, mx);

		assertTrue(event.getTimeSeconds() > 1.0);
		assertEquals(4.0, event.getTimeSeconds(), 1e-9);
	}
}
