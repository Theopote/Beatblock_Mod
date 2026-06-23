package com.beatblock.timeline.editor;

import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapHelperTest {

	@Test
	void snapsToGridWhenWithinThreshold() {
		Timeline timeline = Timeline.createDefault();
		double snapped = SnapHelper.snap(1.03, timeline, 120, true, 0.5);
		assertEquals(1.0, snapped, 1e-9);
	}

	@Test
	void snapsToBeatWhenCloserThanGrid() {
		Timeline timeline = Timeline.createDefault();
		// 120 BPM => beat every 0.5s; 0.48 is closer to 0.5 than grid at 0.5 when grid step is 0.5
		double snapped = SnapHelper.snap(0.48, timeline, 120, true, 0.5);
		assertEquals(0.5, snapped, 1e-9);
	}

	@Test
	void returnsOriginalTimeWhenNoSnapTargetsApply() {
		Timeline timeline = Timeline.createDefault();
		double snapped = SnapHelper.snap(1.37, timeline, 0, false, 0);
		assertEquals(1.37, snapped, 1e-9);
	}

	@Test
	void returnsOriginalTimeWhenTimelineIsNull() {
		assertEquals(2.5, SnapHelper.snap(2.5, null, 120, true, 0.5), 1e-9);
	}
}
