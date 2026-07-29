package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineFrameTrackSnapshotTest {

	@Test
	void reusesSnapshotWhenTimelineUnchanged() {
		Timeline timeline = Timeline.createDefault();
		TimelineFrameTrackSnapshot first = TimelineFrameTrackSnapshot.build(timeline, null);
		TimelineFrameTrackSnapshot second = TimelineFrameTrackSnapshot.build(timeline, first);
		assertSame(first, second);
	}

	@Test
	void rebuildsWhenFeatureTrackAppears() {
		Timeline timeline = Timeline.createDefault();
		TimelineFrameTrackSnapshot first = TimelineFrameTrackSnapshot.build(timeline, null);
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 1f));
		TimelineFrameTrackSnapshot second = TimelineFrameTrackSnapshot.build(timeline, first);
		assertNotSame(first, second);
		assertTrue(second.audioSubTracks().size() >= first.audioSubTracks().size());
	}

	@Test
	void rebuildsAnimationControlWhenFeatureAnimTrackAdded() {
		Timeline timeline = Timeline.createDefault();
		TimelineFrameTrackSnapshot first = TimelineFrameTrackSnapshot.build(timeline, null);
		assertEquals(0, first.animationSubTracks().size());

		String trackId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(trackId, "Kick Ctrl", TrackType.ANIMATION));
		TimelineFrameTrackSnapshot second = TimelineFrameTrackSnapshot.build(timeline, first);
		assertNotSame(first, second);
		assertEquals(1, second.animationSubTracks().size());
	}
}
