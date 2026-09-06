package com.beatblock.timeline.interaction;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DragControllerMarkerSnapTest {

	@Test
	void computeMarkerDragTimeSnapsToBeatAndSetsGuides() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		timeline.addMarker(new TimelineMarker("drag", 0.5, "A", MarkerType.GENERIC));

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(true);
		toolbar.setMagnetSnap(false);

		TimelineViewState viewState = new TimelineViewState();
		InteractionState interaction = new InteractionState();

		double snapped = DragController.computeMarkerDragTime(
			1.02, "drag", 60.0, timeline, toolbar, viewState, interaction);

		assertEquals(1.0, snapped, 1e-9);
		assertArrayEquals(new double[] {1.0}, interaction.getAlignmentGuideTimes(), 1e-9);
	}

	@Test
	void computeMarkerDragTimeDoesNotMagnetToSelf() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker("drag", 2.0, "A", MarkerType.GENERIC));
		timeline.addMarker(new TimelineMarker("other", 5.0, "B", MarkerType.GENERIC));

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(true);

		double nearSelf = DragController.computeMarkerDragTime(
			2.04, "drag", 60.0, timeline, toolbar, new TimelineViewState(), new InteractionState());
		assertEquals(2.04, nearSelf, 1e-9);

		double nearOther = DragController.computeMarkerDragTime(
			5.03, "drag", 60.0, timeline, toolbar, new TimelineViewState(), new InteractionState());
		assertEquals(5.0, nearOther, 1e-9);
	}

	@Test
	void computeMarkerDragTimeClampsToDuration() {
		Timeline timeline = Timeline.createDefault();
		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);
		toolbar.setMagnetSnap(false);

		double clamped = DragController.computeMarkerDragTime(
			99.0, "x", 10.0, timeline, toolbar, new TimelineViewState(), null);
		assertEquals(10.0, clamped, 1e-9);
	}
}
