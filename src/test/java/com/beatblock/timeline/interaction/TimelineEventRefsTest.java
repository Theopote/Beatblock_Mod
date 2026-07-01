package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventRefsTest {

	@Test
	void findReturnsNullForMissingEvent() {
		Timeline timeline = Timeline.createDefault();
		assertNull(TimelineEventRefs.find(timeline, null));
		assertNull(TimelineEventRefs.find(timeline, ""));
		assertNull(TimelineEventRefs.find(timeline, "missing"));
	}

	@Test
	void findLocatesEventAcrossTracks() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 4);
		TimelineEvent event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of());

		TimelineEventRef ref = TimelineEventRefs.find(timeline, event.getId());
		assertNotNull(ref);
		assertEquals(Timeline.TRACK_ID_ANIMATION_AUTO, ref.track().getId());
		assertEquals(clip.getId(), ref.clip().getId());
		assertEquals(event.getId(), ref.event().getId());
	}

	@Test
	void resolveFromSelectionUsesSelectedEvent() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_GLOBAL, 0, 4);
		TimelineEvent event = TimelineOperations.addEvent(clip, 0.5, EventType.GLOBAL, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectEvent(event.getId());

		TimelineSelectionRef ref = TimelineEventRefs.resolveFromSelection(timeline, selection);

		assertNotNull(ref);
		assertTrue(ref.hasEvent());
		assertEquals(event.getId(), ref.event().getId());
	}

	@Test
	void resolveFromSelectionUsesSelectedClipWhenNoEvent() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0, 6);
		SelectionState selection = new SelectionState();
		selection.selectClip(clip.getId());

		TimelineSelectionRef ref = TimelineEventRefs.resolveFromSelection(timeline, selection);

		assertNotNull(ref);
		assertNull(ref.event());
		assertEquals(clip.getId(), ref.clip().getId());
	}
}
