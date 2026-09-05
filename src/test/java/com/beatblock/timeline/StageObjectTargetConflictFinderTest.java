package com.beatblock.timeline;

import com.beatblock.testutil.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageObjectTargetConflictFinderTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void findsOverlappingEventsOnSameTarget() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"a", 10.0, 1.0, "Pulse", "stage-c", 1f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"b", 10.5, 1.0, "Fall", "stage-c", 1f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"c", 20.0, 0.5, "Pulse", "stage-c", 1f, Map.of()));

		var summary = StageObjectTargetConflictFinder.findOverlaps(timeline, "stage-c");
		assertEquals(1, summary.count());
		var overlap = summary.overlaps().getFirst();
		assertEquals("stage-c", overlap.targetObjectId());
		assertEquals(10.5, overlap.overlapStartSeconds(), 1e-6);
		assertEquals(11.0, overlap.overlapEndSeconds(), 1e-6);
	}

	@Test
	void ignoresDifferentTargetsAndNonOverlappingWindows() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"a", 1.0, 0.5, "Pulse", "stage-a", 1f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"b", 1.0, 0.5, "Fall", "stage-b", 1f, Map.of()));
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"c", 2.0, 0.5, "Pulse", "stage-a", 1f, Map.of()));

		assertTrue(StageObjectTargetConflictFinder.findOverlaps(timeline, null).isEmpty());
	}
}
