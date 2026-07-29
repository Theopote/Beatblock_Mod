package com.beatblock.timeline.payload;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEventPayloadCodecTest {

	@Test
	void decodeAnimateStepRoundTripsKnownFields() {
		Map<String, Object> raw = new HashMap<>();
		raw.put("actionMode", "ANIMATE");
		raw.put("animationType", "BlockJump");
		raw.put("targetObject", "stage-a");
		raw.put("energy", 0.8f);
		raw.put("durationSeconds", 1.5);
		raw.put("eventOrigin", "AUTO_GENERATED");
		raw.put("energyThreshold", 0.2f);
		raw.put("dispatchModel", "STEP");
		raw.put("inheritGroupSpatial", false);
		raw.put("spatialMode", "RADIAL");
		raw.put("sequentialDelaySeconds", 0.05);
		raw.put("blocksPerBeat", 4);
		raw.put("stepStartMode", "IMMEDIATE");
		raw.put("vfxEnabled", true);
		raw.put("flashBlock", "minecraft:gold_block");
		raw.put("generatedBy", "binding");
		raw.put("customPluginKey", 42);

		StageEventPayload payload = StageEventPayloadCodec.decode(raw);
		assertInstanceOf(StageEventPayload.Animate.class, payload);
		StageEventPayload.Animate a = (StageEventPayload.Animate) payload;
		assertEquals(DispatchModel.STEP, a.dispatchModel());
		assertEquals(SpatialDispatchMode.RADIAL, a.spatial().mode());
		assertFalse(a.spatial().inheritGroupSpatial());
		assertEquals(4, a.step().blocksPerBeat());
		assertEquals("minecraft:gold_block", a.flashBlockId());
		assertEquals("binding", a.extensions().get("generatedBy"));
		assertEquals(42, a.extensions().get("customPluginKey"));
		assertTrue(payload.isStepDispatch());
		assertTrue(payload.passesEnergyGate());

		Map<String, Object> encoded = payload.toParameterMap();
		assertEquals("STEP", encoded.get("dispatchModel"));
		assertEquals("RADIAL", encoded.get("spatialMode"));
		assertEquals("binding", encoded.get("generatedBy"));
		assertEquals(42, encoded.get("customPluginKey"));
		assertEquals("minecraft:gold_block", encoded.get("flashBlock"));

		StageEventPayload again = StageEventPayloadCodec.decode(encoded);
		assertEquals(DispatchModel.STEP, ((StageEventPayload.Animate) again).dispatchModel());
		assertEquals(4, ((StageEventPayload.Animate) again).step().blocksPerBeat());
	}

	@Test
	void decodeBuildPayload() {
		Map<String, Object> raw = Map.of(
			"actionMode", "BUILD",
			"animationType", "build",
			"targetObject", "stage-b",
			"energy", 1f,
			"durationSeconds", 3.0,
			"buildMode", "TOWER",
			"buildDissolve", "true",
			"layerId", "layer-1",
			"placeBlock", "minecraft:quartz_block"
		);
		StageEventPayload payload = StageEventPayloadCodec.decode(raw);
		assertInstanceOf(StageEventPayload.Build.class, payload);
		StageEventPayload.Build b = (StageEventPayload.Build) payload;
		assertEquals(TimelineAnimationActionMode.BUILD, b.actionMode());
		assertEquals("TOWER", b.buildMode());
		assertTrue(b.dissolve());
		assertEquals("layer-1", b.layerId());
		assertEquals("minecraft:quartz_block", b.placeBlockId());
		assertEquals("minecraft:quartz_block", payload.resolvePlaceBlockId().orElse(""));

		Map<String, Object> encoded = payload.toParameterMap();
		assertEquals("BUILD", encoded.get("actionMode"));
		assertEquals("true", encoded.get("buildDissolve"));
		assertEquals("layer-1", encoded.get("layerId"));
	}

	@Test
	void fromAnimationEventUsesTopLevelFields() {
		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"ev1", 2.0, 0.5, "pulse", "obj", 0.9f,
			Map.of(
				"actionMode", "PLACE",
				"placeBlock", "minecraft:stone",
				"eventOrigin", TimelineEventOrigin.MANUAL.name()
			)
		);
		StageEventPayload payload = event.getPayload();
		assertInstanceOf(StageEventPayload.Place.class, payload);
		assertEquals("pulse", payload.animationType());
		assertEquals("obj", payload.targetObject());
		assertEquals(0.9f, payload.energy(), 1e-6);
		assertEquals("minecraft:stone", ((StageEventPayload.Place) payload).placeBlockId());
	}

	@Test
	void fromPayloadFactoryRoundTrips() {
		StageEventPayload.Clear clear = new StageEventPayload.Clear(
			"", "stage", 1f, 0.2, TimelineEventOrigin.MANUAL, 0f, Map.of("note", "x"));
		TimelineAnimationEvent event = TimelineAnimationEvent.fromPayload("c1", 1.25, clear);
		assertEquals(1.25, event.getTimeSeconds(), 1e-9);
		assertEquals(TimelineAnimationActionMode.CLEAR, event.getActionMode());
		assertEquals("x", event.getPayload().extensions().get("note"));
	}

	@Test
	void energyGateRespectsThreshold() {
		StageEventPayload.Animate low = new StageEventPayload.Animate(
			"pulse", "s", 0.1f, 0.5, TimelineEventOrigin.MANUAL, 0.5f,
			DispatchModel.BURST, SpatialParams.DEFAULT, StepParams.DEFAULT,
			null, true, null, Map.of());
		assertFalse(low.passesEnergyGate());
		StageEventPayload.Animate high = new StageEventPayload.Animate(
			"pulse", "s", 0.6f, 0.5, TimelineEventOrigin.MANUAL, 0.5f,
			DispatchModel.BURST, SpatialParams.DEFAULT, StepParams.DEFAULT,
			null, true, null, Map.of());
		assertTrue(high.passesEnergyGate());
	}
}
