package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventSelectionHandlerTest {

	@Test
	void shiftClickSelectsTimeRangeOnSameTrack() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent e1 = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		TimelineEvent e2 = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());
		TimelineEvent e3 = TimelineOperations.addEvent(clip, 3.0, EventType.ANIMATION, Map.of());
		TimelineEvent e4 = TimelineOperations.addEvent(clip, 5.0, EventType.ANIMATION, Map.of());

		SelectionState selection = new SelectionState();
		selection.setRangeAnchorEventId(e1.getId());

		HitResult hit = HitResult.event(
			Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), e3.getId(), 3.0);
		TimelineEventSelectionHandler.applyClickSelection(timeline, selection, hit, false, true);

		Set<String> selected = selection.getSelectedEvents();
		assertEquals(3, selected.size());
		assertTrue(selected.contains(e1.getId()));
		assertTrue(selected.contains(e2.getId()));
		assertTrue(selected.contains(e3.getId()));
		assertFalse(selected.contains(e4.getId()));
	}

	@Test
	void ctrlClickTogglesEventSelection() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		SelectionState selection = new SelectionState();
		HitResult hit = HitResult.event(
			Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), event.getId(), 1.0);

		TimelineEventSelectionHandler.applyClickSelection(timeline, selection, hit, true, false);
		assertTrue(selection.isEventSelected(event.getId()));

		TimelineEventSelectionHandler.applyClickSelection(timeline, selection, hit, true, false);
		assertFalse(selection.isEventSelected(event.getId()));
	}

	@Test
	void plainClickSetsAnchorAndSingleSelection() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineEvent e1 = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());
		TimelineEvent e2 = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());

		SelectionState selection = new SelectionState();
		selection.selectEvent(e1.getId());
		selection.setRangeAnchorEventId(e1.getId());

		HitResult hit = HitResult.event(
			Timeline.TRACK_ID_ANIMATION_AUTO, clip.getId(), e2.getId(), 2.0);
		TimelineEventSelectionHandler.applyClickSelection(timeline, selection, hit, false, false);

		assertEquals(Set.of(e2.getId()), selection.getSelectedEvents());
		assertEquals(e2.getId(), selection.getRangeAnchorEventId());
	}
}
