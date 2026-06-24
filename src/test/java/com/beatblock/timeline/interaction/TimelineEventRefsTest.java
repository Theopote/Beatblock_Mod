package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
	void resolveForPropertiesFallsBackToSelection() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_GLOBAL, 0, 4);
		TimelineEvent event = TimelineOperations.addEvent(clip, 0.5, EventType.GLOBAL, Map.of());
		SelectionState selection = new SelectionState();
		selection.selectEvent(event.getId());

		AtomicReference<String> updatedId = new AtomicReference<>();
		TimelineEventRef ref = TimelineEventRefs.resolveForProperties(
			timeline, selection, null, updatedId::set);

		assertNotNull(ref);
		assertEquals(event.getId(), updatedId.get());
	}
}
