package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimelineInteractionClipboardTest {

	@Test
	void copyCollectsSelectedEventsSortedByTime() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 6);
		TimelineEvent first = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("a", 1));
		TimelineEvent second = TimelineOperations.addEvent(clip, 3.0, EventType.ANIMATION, Map.of("b", 2));

		SelectionState selection = new SelectionState();
		selection.selectEvent(second.getId());
		selection.selectEvent(first.getId());

		List<TimelineInteractionClipboard.ClipboardEvent> clipboard = new ArrayList<>();
		TimelineInteractionClipboard.copy(clipboard, timeline, selection);

		assertEquals(2, clipboard.size());
		assertEquals(1.0, clipboard.get(0).timeSeconds(), 1e-9);
		assertEquals(3.0, clipboard.get(1).timeSeconds(), 1e-9);
		assertEquals(EventType.ANIMATION, clipboard.get(0).type());
	}

	@Test
	void pasteCreatesEventsAtAnchorOffset() {
		Timeline timeline = Timeline.createDefault();
		Clip sourceClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 6);
		TimelineOperations.addEvent(sourceClip, 1.0, EventType.ANIMATION, Map.of());
		TimelineOperations.addEvent(sourceClip, 2.0, EventType.ANIMATION, Map.of());

		List<TimelineInteractionClipboard.ClipboardEvent> clipboard = List.of(
			new TimelineInteractionClipboard.ClipboardEvent(
				Timeline.TRACK_ID_ANIMATION_AUTO, sourceClip.getId(), 1.0, EventType.ANIMATION, Map.of()),
			new TimelineInteractionClipboard.ClipboardEvent(
				Timeline.TRACK_ID_ANIMATION_AUTO, sourceClip.getId(), 2.0, EventType.ANIMATION, Map.of())
		);

		SelectionState selection = new SelectionState();
		TimelineInteractionClipboard.paste(new TimelineInteractionClipboard.PasteRequest(
			timeline,
			selection,
			clipboard,
			5.0,
			Timeline.TRACK_ID_ANIMATION_AUTO,
			sourceClip.getId(),
			new TimelineTrackListState()
		));

		assertEquals(2, selection.getSelectedEvents().size());
		assertEquals(4, sourceClip.getEvents().size());
		double[] times = sourceClip.getEvents().stream().mapToDouble(TimelineEvent::getTimeSeconds).sorted().toArray();
		assertEquals(1.0, times[0], 1e-9);
		assertEquals(2.0, times[1], 1e-9);
		assertEquals(5.0, times[2], 1e-9);
		assertEquals(6.0, times[3], 1e-9);
	}

	@Test
	void clipboardAndPasteResultSnapshotCollections() {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("value", 1);
		var event = new TimelineInteractionClipboard.ClipboardEvent(
			"track", "clip", 1.0, EventType.ANIMATION, parameters);
		parameters.put("value", 2);

		assertEquals(1, event.parameters().get("value"));
		assertThrows(UnsupportedOperationException.class, () -> event.parameters().clear());

		var pasted = new ArrayList<TimelineInteractionClipboard.PastedEventRef>();
		var result = new TimelineInteractionClipboard.PasteResult(pasted, List.of());
		pasted.add(new TimelineInteractionClipboard.PastedEventRef(
			"track", "clip", new TimelineEvent("event", 1.0, EventType.ANIMATION, Map.of())));
		assertEquals(0, result.pastedEvents().size());
		assertThrows(UnsupportedOperationException.class, () -> result.pastedEvents().clear());
	}
}
