package com.beatblock.ui.presenter;

import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
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
	void applyMarkerEditUpdatesTimelineViaUndoableCommand() {
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

		assertTrue(editor.getCommandManager().canUndo());
		editor.getCommandManager().undo();
		assertEquals("Old", presenter.findMarker(timeline, marker.getId()).getName());
	}

	@Test
	void applyMarkerEditAcceptsResolvedSeconds() {
		TimelineMarker marker = new TimelineMarker(1.0, "A", MarkerType.GENERIC);
		timeline.addMarker(marker);

		var outcome = presenter.applyMarkerEdit(
			timeline, marker.getId(), "A", 4.0, MarkerType.GENERIC.ordinal(), false);
		assertTrue(outcome.result().ok());
		assertEquals(4.0, presenter.findMarker(timeline, marker.getId()).getTimeSeconds(), 1e-9);
		assertEquals("4.000", outcome.formSnapshot().timeText());
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
	void generatedSectionTypeChangeRequiresConfirm() {
		TimelineMarker marker = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		timeline.addMarker(marker);

		assertTrue(presenter.requiresTypeChangeConfirm(marker, MarkerType.FX.ordinal()));
		var blocked = presenter.applyMarkerEdit(
			timeline, marker.getId(), marker.getName(), "1.000", MarkerType.FX.ordinal(), false);
		assertFalse(blocked.result().ok());
		assertEquals(MarkerType.SECTION, presenter.findMarker(timeline, marker.getId()).getType());

		var allowed = presenter.applyMarkerEdit(
			timeline, marker.getId(), marker.getName(), "1.000", MarkerType.FX.ordinal(), true);
		assertTrue(allowed.result().ok());
		assertEquals(MarkerType.FX, presenter.findMarker(timeline, marker.getId()).getType());
		assertEquals(MarkerEditState.USER_EDITED, presenter.findMarker(timeline, marker.getId()).getEditState());
	}

	@Test
	void lockedMarkerRejectsDeleteAndEdit() {
		TimelineMarker marker = new TimelineMarker(
			"lock", 1.0, "SECTION A", MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS, MarkerEditState.LOCKED);
		timeline.addMarker(marker);

		assertFalse(presenter.deleteMarker(timeline, marker.getId(), true).ok());
		assertFalse(presenter.applyMarkerEdit(
			timeline, marker.getId(), "B", "2.0", MarkerType.SECTION.ordinal(), true).result().ok());
		assertNotNull(presenter.findMarker(timeline, marker.getId()));
	}

	@Test
	void lockAndUnlockAreUndoableAndUnlockBecomesUserEdited() {
		TimelineMarker marker = TimelineMarker.audioAnalysisSection(1.0, "SECTION A");
		timeline.addMarker(marker);

		assertTrue(presenter.setMarkerLocked(timeline, marker.getId(), true).ok());
		assertEquals(MarkerEditState.LOCKED, presenter.findMarker(timeline, marker.getId()).getEditState());

		assertTrue(presenter.setMarkerLocked(timeline, marker.getId(), false).ok());
		assertEquals(MarkerEditState.USER_EDITED, presenter.findMarker(timeline, marker.getId()).getEditState());

		assertTrue(editor.getCommandManager().canUndo());
		editor.getCommandManager().undo();
		assertEquals(MarkerEditState.LOCKED, presenter.findMarker(timeline, marker.getId()).getEditState());
	}

	@Test
	void deleteMarkerRemovesEntry() {
		TimelineMarker marker = new TimelineMarker(1.0, "A", MarkerType.GENERIC);
		timeline.addMarker(marker);
		assertTrue(presenter.deleteMarker(timeline, marker.getId()).ok());
		assertNull(presenter.findMarker(timeline, marker.getId()));
		assertTrue(editor.getCommandManager().canUndo());
	}

	@Test
	void insertAtPlayheadCreatesManualMarker() {
		editor.getPlaybackSession().seek(7.5);
		assertTrue(presenter.insertAtPlayhead(MarkerType.CAMERA, "Cam").ok());
		assertEquals(1, timeline.getMarkers().size());
		TimelineMarker created = timeline.getMarkers().getFirst();
		assertEquals(7.5, created.getTimeSeconds(), 1e-9);
		assertEquals(MarkerType.CAMERA, created.getType());
		assertEquals(MarkerOrigin.MANUAL, created.getOrigin());
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
