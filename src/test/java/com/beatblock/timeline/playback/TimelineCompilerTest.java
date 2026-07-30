package com.beatblock.timeline.playback;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineCompilerTest {

	@Test
	@SuppressWarnings("unchecked")
	void compiledSnapshotIsSortedAndIsolatedFromDocumentEdits() {
		Timeline timeline = Timeline.createDefault();
		List<String> mutableTags = new ArrayList<>(List.of("initial"));
		timeline.addAutoAnimationEvent(event("later", 4.0, Map.of("tags", mutableTags)));
		timeline.addAutoAnimationEvent(event("earlier", 1.0, Map.of()));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline);
		timeline.addAutoAnimationEvent(event("added-after-compile", 2.0, Map.of()));
		mutableTags.add("mutated");

		assertEquals(List.of("earlier", "later"), snapshot.stageEvents().stream()
			.map(TimelineAnimationEvent::getAnimationTypeId).toList());
		assertEquals(2, snapshot.stageEvents().size());
		Object frozenTags = snapshot.stageEvents().get(1).getParameters().get("tags");
		assertEquals(List.of("initial"), frozenTags);
		assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) frozenTags).add("x"));
	}

	@Test
	void snapshotDefensivelyCopiesBeatArrayAndCapturesPlaybackPolicy() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 128.0);
		timeline.setMetadata("timelineActionRollbackMode", "performance");
		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline);

		double[] first = snapshot.referenceBeatTimesSeconds();
		double[] second = snapshot.referenceBeatTimesSeconds();
		assertNotSame(first, second);
		assertEquals(128.0, snapshot.bpm(), 1e-9);
		assertTrue(!snapshot.restoreWorldMutations());
	}

	private static TimelineAnimationEvent event(String id, double time, Map<String, Object> params) {
		return new TimelineAnimationEvent(id, time, 1.0, id, "stage", 1f, params);
	}
}
