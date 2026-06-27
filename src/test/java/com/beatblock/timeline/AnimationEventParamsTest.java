package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEventParamsTest {

	@Test
	void fromAnimationEventSeparatesCoreAndExtensionKeys() {
		var event = new TimelineAnimationEvent(
			"ev1", 1.0, 2.0, "pulse", "stage-a", 0.6f,
			Map.of("buildMode", "wall", "eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name()));

		AnimationEventParams params = AnimationEventParams.fromAnimationEvent(event);

		assertEquals(TimelineAnimationActionMode.ANIMATE, params.actionMode());
		assertEquals("pulse", params.animationType());
		assertEquals("stage-a", params.targetObject());
		assertEquals(0.6f, params.energy(), 1e-6);
		assertEquals(2.0, params.durationSeconds(), 1e-9);
		assertEquals(TimelineEventOrigin.AUTO_GENERATED, params.eventOrigin());
		assertEquals("wall", params.extensions().get("buildMode"));
		assertFalse(params.extensions().containsKey("actionMode"));
	}

	@Test
	void toParameterMapIncludesCompatibilityKeys() {
		AnimationEventParams params = new AnimationEventParams(
			TimelineAnimationActionMode.PLACE,
			"build",
			"target",
			0.8f,
			1.25,
			TimelineEventOrigin.MANUAL,
			Map.of("placeBlock", "minecraft:stone")
		);

		Map<String, Object> map = params.toParameterMap();

		assertEquals(TimelineAnimationActionMode.PLACE.name(), map.get("actionMode"));
		assertEquals(TimelineAnimationActionMode.PLACE.name(), map.get("mode"));
		assertEquals("build", map.get("animationType"));
		assertEquals("target", map.get("targetObject"));
		assertEquals(0.8f, ((Number) map.get("energy")).floatValue(), 1e-6);
		assertEquals(1.25, ((Number) map.get("durationSeconds")).doubleValue(), 1e-9);
		assertEquals(TimelineEventOrigin.MANUAL.name(), map.get("eventOrigin"));
		assertEquals("minecraft:stone", map.get("placeBlock"));
	}

	@Test
	void fromParameterMapRoundTripPreservesExtensions() {
		Map<String, Object> raw = new HashMap<>();
		raw.put("mode", TimelineAnimationActionMode.BUILD.name());
		raw.put("animationType", "build");
		raw.put("targetObject", "stage");
		raw.put("energy", 0.5);
		raw.put("durationSeconds", 3.0);
		raw.put("eventOrigin", TimelineEventOrigin.MANUAL.name());
		raw.put("blocksPerBeat", 2);

		AnimationEventParams parsed = AnimationEventParams.fromParameterMap(raw);
		Map<String, Object> roundTrip = parsed.toParameterMap();

		assertEquals(TimelineAnimationActionMode.BUILD, parsed.actionMode());
		assertEquals(2, roundTrip.get("blocksPerBeat"));
		assertEquals(TimelineAnimationActionMode.BUILD.name(), roundTrip.get("actionMode"));
	}

	@Test
	void isCoreParameterKeyRecognizesManagedKeys() {
		assertTrue(AnimationEventParams.isCoreParameterKey("actionMode"));
		assertTrue(AnimationEventParams.isCoreParameterKey("mode"));
		assertFalse(AnimationEventParams.isCoreParameterKey("buildMode"));
	}
}
