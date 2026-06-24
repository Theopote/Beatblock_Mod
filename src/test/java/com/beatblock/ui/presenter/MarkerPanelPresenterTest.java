package com.beatblock.ui.presenter;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerPanelPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private MarkerPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		editor = new TimelineEditor(timeline);
		presenter = new MarkerPanelPresenter(
			new TimelineEditorPresenter(() -> editor, time -> {}),
			() -> timeline
		);
	}

	@Test
	void listMarkersBuildsDisplayLabels() {
		timeline.addMarker(new TimelineMarker(1.5, "Intro", MarkerType.SECTION));
		var items = presenter.listMarkers(timeline);
		assertEquals(1, items.size());
		assertTrue(items.get(0).listLabel().contains("Intro"));
		assertEquals(MarkerType.SECTION, items.get(0).type());
	}

	@Test
	void applyMarkerEditUpdatesTimeline() {
		TimelineMarker marker = new TimelineMarker(2.0, "Old", MarkerType.GENERIC);
		timeline.addMarker(marker);

		var outcome = presenter.applyMarkerEdit(timeline, marker.getId(), "New", "3.5", MarkerType.DROP.ordinal());
		assertTrue(outcome.result().ok());
		assertNotNull(outcome.formSnapshot());
		assertEquals("New", outcome.formSnapshot().name());
		assertEquals("3.500", outcome.formSnapshot().timeText());

		TimelineMarker updated = presenter.findMarker(timeline, marker.getId());
		assertEquals("New", updated.getName());
		assertEquals(3.5, updated.getTimeSeconds(), 1e-9);
		assertEquals(MarkerType.DROP, updated.getType());
	}

	@Test
	void applyMarkerEditRejectsInvalidTime() {
		TimelineMarker marker = new TimelineMarker(1.0, "A", MarkerType.GENERIC);
		timeline.addMarker(marker);

		var outcome = presenter.applyMarkerEdit(timeline, marker.getId(), "A", "bad", 0);
		assertFalse(outcome.result().ok());
		assertEquals("1.000", outcome.formSnapshot().timeText());
	}

	@Test
	void deleteMarkerRemovesEntry() {
		TimelineMarker marker = new TimelineMarker(1.0, "A", MarkerType.GENERIC);
		timeline.addMarker(marker);
		assertTrue(presenter.deleteMarker(timeline, marker.getId()).ok());
		assertNull(presenter.findMarker(timeline, marker.getId()));
	}

	@Test
	void neighborsOfReturnsAdjacentMarkers() {
		TimelineMarker first = new TimelineMarker(1.0, "A", MarkerType.GENERIC);
		TimelineMarker middle = new TimelineMarker(2.0, "B", MarkerType.GENERIC);
		TimelineMarker last = new TimelineMarker(3.0, "C", MarkerType.GENERIC);
		timeline.addMarker(first);
		timeline.addMarker(middle);
		timeline.addMarker(last);

		var neighbors = presenter.neighborsOf(timeline, middle.getId());
		assertEquals(first.getId(), neighbors.previous().getId());
		assertEquals(last.getId(), neighbors.next().getId());
	}

	@Test
	void jumpToMarkerSeeksPlayback() {
		TimelineMarker marker = new TimelineMarker(4.25, "Jump", MarkerType.GENERIC);
		assertTrue(presenter.jumpToMarker(marker));
		assertEquals(4.25, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}
}
