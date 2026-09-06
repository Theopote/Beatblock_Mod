package com.beatblock.timeline.editing;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.timeline.payload.DispatchModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTypeReplaceTest {

	@Test
	void keepsTargetEnergyOriginAndSetsAnimateDuration() {
		AnimationEventParams before = new AnimationEventParams(
			TimelineAnimationActionMode.BUILD,
			"RhythmDrop",
			"building-a",
			0.42f,
			1.5,
			TimelineEventOrigin.MANUAL,
			Map.of(
				"meteorHeight", 8.0,
				"impactThreshold", 0.9,
				"dispatchModel", DispatchModel.STEP.name(),
				TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID, "rhythm-drop"
			)
		);

		AnimationEventParams after = AnimationTypeReplace.apply(before, "Pulse", 0.35);

		assertEquals(TimelineAnimationActionMode.ANIMATE, after.actionMode());
		assertEquals("Pulse", after.animationType());
		assertEquals("building-a", after.targetObject());
		assertEquals(0.42f, after.energy(), 1e-6);
		assertEquals(0.35, after.durationSeconds(), 1e-9);
		assertEquals(TimelineEventOrigin.MANUAL, after.eventOrigin());
		assertEquals("rhythm-drop", after.extensions().get(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID));
		assertFalse(after.extensions().containsKey("meteorHeight"));
		assertFalse(after.extensions().containsKey("impactThreshold"));
		assertFalse(after.extensions().containsKey("dispatchModel"));
	}

	@Test
	void stripsTrajectoryAndStepParamsFromParameterMapRoundTrip() {
		Map<String, Object> params = new HashMap<>();
		params.put("actionMode", "ANIMATE");
		params.put("animationType", "Meteor");
		params.put("targetObject", "stage");
		params.put("energy", 0.8f);
		params.put("durationSeconds", 1.0);
		params.put("eventOrigin", "MANUAL");
		params.put("meteorHeight", 12.0);
		params.put("meteorScatter", 2.5);
		params.put("impactThreshold", 0.92);
		params.put("dispatchModel", "STEP");
		params.put("spatialMode", "SEQUENTIAL");
		params.put("sequentialDelaySeconds", 0.1);
		params.put("flashBlockId", "minecraft:gold_block");
		params.put("generatedBy", "binding");

		AnimationEventParams replaced = AnimationTypeReplace.apply(
			AnimationEventParams.fromParameterMap(params),
			"Pulse",
			0.35
		);
		Map<String, Object> out = replaced.toParameterMap();

		assertEquals("Pulse", out.get("animationType"));
		assertEquals(0.35, ((Number) out.get("durationSeconds")).doubleValue(), 1e-9);
		assertEquals("stage", out.get("targetObject"));
		assertEquals("binding", out.get("generatedBy"));
		assertFalse(out.containsKey("meteorHeight"));
		assertFalse(out.containsKey("meteorScatter"));
		assertFalse(out.containsKey("impactThreshold"));
		assertFalse(out.containsKey("dispatchModel"));
		assertFalse(out.containsKey("spatialMode"));
		assertFalse(out.containsKey("flashBlockId"));
	}

	@Test
	void preserveExtensionsKeepsOnlyAllowlistedKeys() {
		Map<String, Object> kept = AnimationTypeReplace.preserveExtensions(Map.of(
			"meteorHeight", 9.0,
			"generatorId", "auto",
			"bb.customTag", "ok",
			"dispatchModel", "STEP"
		));

		assertEquals(2, kept.size());
		assertEquals("auto", kept.get("generatorId"));
		assertEquals("ok", kept.get("bb.customTag"));
		assertTrue(AnimationTypeReplace.isPreservedExtensionKey("generatorId"));
		assertFalse(AnimationTypeReplace.isPreservedExtensionKey("meteorHeight"));
	}
}
