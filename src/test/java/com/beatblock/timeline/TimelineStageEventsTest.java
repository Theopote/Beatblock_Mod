package com.beatblock.timeline;

import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineStageEventsTest {

	@Test
	void getStageEventsMergesAllBucketsSortedByTime() {
		Timeline timeline = Timeline.createDefault();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"block-late", 5.0, 0.5, "pulse", "stage-a", 1f, Map.of()));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"auto-mid", 2.0, 0.5, "jump", "stage-a", 1f,
			Map.of("eventOrigin", TimelineEventOrigin.GENERATED.name())));
		String buildTrackId = BuildLayerTrackSupport.DEFAULT_FIRST_TRACK_ID;
		timeline.addAnimationEvent(buildTrackId, new TimelineAnimationEvent(
			"build-early", 1.0, 1.0, "build", "stage-a", 1f, Map.of("buildMode", "wall")));

		String featureTrackId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(featureTrackId, "Kick", TrackType.ANIMATION));
		timeline.addAnimationEvent(featureTrackId, new TimelineAnimationEvent(
			"feat", 3.0, 0.4, "BlockJump", "stage-a", 0.7f, Map.of()));

		List<TimelineAnimationEvent> stage = timeline.getStageEvents();
		assertEquals(4, stage.size());
		// TimelineOperations 会生成新 eventId，按时间与动画类型校验合并排�?		assertEquals(1.0, stage.get(0).getTimeSeconds(), 1e-9);
		assertEquals("build", stage.get(0).getAnimationTypeId());
		assertEquals(2.0, stage.get(1).getTimeSeconds(), 1e-9);
		assertEquals("jump", stage.get(1).getAnimationTypeId());
		assertEquals(3.0, stage.get(2).getTimeSeconds(), 1e-9);
		assertEquals("BlockJump", stage.get(2).getAnimationTypeId());
		assertEquals(5.0, stage.get(3).getTimeSeconds(), 1e-9);
		assertEquals("pulse", stage.get(3).getAnimationTypeId());

		// 兼容过滤视图仍可�?		assertEquals(2, timeline.getBlockAnimationEvents().size()); // block + feature
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals(1, timeline.getBuildReverseEvents().size());
	}

	@Test
	void getStageEventsRebuildsAfterMarkDirty() {
		Timeline timeline = Timeline.createDefault();
		assertTrue(timeline.getStageEvents().isEmpty());

		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"e1", 1.0, 0.5, "pulse", "s", 1f, Map.of()));
		assertEquals(1, timeline.getStageEvents().size());

		timeline.clearBlockAnimationEvents();
		assertTrue(timeline.getStageEvents().isEmpty());
	}
}
