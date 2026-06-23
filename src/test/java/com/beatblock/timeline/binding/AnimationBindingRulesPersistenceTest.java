package com.beatblock.timeline.binding;

import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationBindingRulesPersistenceTest {

	@Test
	void saveAndLoadRulesRoundTripThroughMetadata() {
		Timeline timeline = Timeline.createDefault();
		AnimationBindingRule rule = AnimationBindingRule.builder()
			.id("rule-kick")
			.name("Kick Pulse")
			.sourceFeatureKey("kick")
			.animationTypeId("Pulse")
			.targetObjectId("stage-a")
			.energyThreshold(0.3f)
			.spatialMode(SpatialDispatchMode.SEQUENTIAL)
			.build();

		AnimationBindingEngine.saveRules(timeline, List.of(rule));
		List<AnimationBindingRule> loaded = AnimationBindingEngine.loadRules(timeline);

		assertEquals(1, loaded.size());
		assertEquals("rule-kick", loaded.getFirst().id());
		assertEquals("kick", loaded.getFirst().sourceFeatureKey());
		assertEquals(SpatialDispatchMode.SEQUENTIAL, loaded.getFirst().spatialMode());
	}

	@Test
	void saveEmptyRulesClearsMetadata() {
		Timeline timeline = Timeline.createDefault();
		AnimationBindingEngine.saveRules(timeline, List.of(
			AnimationBindingRule.builder()
				.sourceFeatureKey("snare")
				.animationTypeId("Pulse")
				.targetObjectId("stage")
				.build()));
		assertFalse(AnimationBindingEngine.loadRules(timeline).isEmpty());

		AnimationBindingEngine.saveRules(timeline, List.of());
		assertTrue(AnimationBindingEngine.loadRules(timeline).isEmpty());
	}
}
