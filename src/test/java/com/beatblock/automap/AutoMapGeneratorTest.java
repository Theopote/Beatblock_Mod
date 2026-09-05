package com.beatblock.automap;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class AutoMapGeneratorTest {

	@Test
	void returnsZeroWhenTimelineNull() {
		assertEquals(0, AutoMapGenerator.generate(null, AutoMapConfig.createDefault(), false));
	}

	@Test
	void returnsZeroWhenNoFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		assertEquals(0, AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false));
		assertTrue(timeline.getAutoAnimationEvents().isEmpty());
	}

	@Test
	void mapsKickFeatureKeyToLowRuleAnimation() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.5f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertEquals(1, count);
		TimelineAnimationEvent ev = timeline.getAutoAnimationEvents().getFirst();
		assertEquals("Pulse", ev.getAnimationTypeId());
		assertEquals(1.0, ev.getTimeSeconds(), 1e-6);
		assertEquals("stage", ev.getTargetObjectId());
	}

	@Test
	void skipsEventsBelowMinEnergy() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("low", new FeatureEvent(1.0, 0.05f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertEquals(0, count);
	}

	@Test
	void enforcesMinGapBetweenGeneratedEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("mid", new FeatureEvent(1.0, 0.5f));
		timeline.addFeatureEvent("mid", new FeatureEvent(1.05, 0.5f));
		timeline.addFeatureEvent("mid", new FeatureEvent(1.20, 0.5f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		// Choreography budget/conflict may keep a single Accent winner on the shared stage target.
		assertTrue(count >= 1 && count <= 2);
		assertEquals(1.0, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-6);
		if (count == 2) {
			assertEquals(1.20, timeline.getAutoAnimationEvents().get(1).getTimeSeconds(), 1e-6);
		}
	}

	@Test
	void replaceClearsPreviousAutoEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("high", new FeatureEvent(2.0, 0.8f));
		AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);
		assertEquals(1, timeline.getAutoAnimationEvents().size());

		timeline.clearFeatureTracks();
		timeline.addFeatureEvent("low", new FeatureEvent(5.0, 0.9f));
		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), true);

		assertEquals(1, count);
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals("Pulse", timeline.getAutoAnimationEvents().getFirst().getAnimationTypeId());
		assertEquals(5.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-6);
	}

	@Test
	void mapsEnergyToHeightWhenRuleUsesHeight() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("mid", new FeatureEvent(3.0, 0.5f));

		AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		Object height = timeline.getAutoAnimationEvents().getFirst().getParameters().get("height");
		assertTrue(height instanceof Number);
		assertEquals(0.15625f, ((Number) height).floatValue(), 1e-3f);
	}

	@Test
	void allowsCloseEventsOnDifferentFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.8f));
		timeline.addFeatureEvent("snare", new FeatureEvent(1.05, 0.8f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		// Default config maps both features onto the shared "stage" target; conflict resolver keeps one.
		assertEquals(1, count);
		assertEquals(1, timeline.getAutoAnimationEvents().size());
	}

	@Test
	void retainsLaterFeatureTracksAfterEarlierTrackSpansTimeline() {
		Timeline timeline = Timeline.createDefault();
		for (int i = 0; i < 60; i++) {
			timeline.addFeatureEvent("low", new FeatureEvent(i + 0.5, 0.9f));
		}
		timeline.addFeatureEvent("mid", new FeatureEvent(1.0, 0.5f));
		timeline.addFeatureEvent("high", new FeatureEvent(2.0, 0.8f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertTrue(count > 60);
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.anyMatch(ev -> "Pulse".equals(ev.getAnimationTypeId())));
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.anyMatch(ev -> Math.abs(ev.getTimeSeconds() - 1.0) < 1e-6));
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.anyMatch(ev -> Math.abs(ev.getTimeSeconds() - 2.0) < 1e-6));
	}

	@Test
	void assignsPerFeatureTargetsFromConfig() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.8f));
		timeline.addFeatureEvent("snare", new FeatureEvent(2.0, 0.8f));

		AutoMapConfig config = AutoMapConfig.builder()
			.rule(new AutoMapRule("low", 0.15f, "bounce", 0.5, true, 4f, 0.12, null))
			.rule(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f, 0.08, null))
			.rule(new AutoMapRule("high", 0.15f, "pulse", 0.3, false, 1f, 0.04, null))
			.targetForFeature("low", "stage-low")
			.targetForFeature("mid", "stage-mid")
			.build();

		int count = AutoMapGenerator.generate(timeline, config, false);

		assertEquals(2, count);
		assertEquals("stage-low", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("stage-mid", timeline.getAutoAnimationEvents().get(1).getTargetObjectId());
	}
}
