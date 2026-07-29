package com.beatblock.timeline.editing;

import com.beatblock.timeline.payload.DispatchModel;
import com.beatblock.timeline.payload.StageEventPayload;
import com.beatblock.timeline.payload.StageEventPayloadCodec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEventPropertiesEditorTest {

	@Test
	void burstModeOmitsStepParameters() {
		AnimationEventFormInput input = new AnimationEventFormInput(
			1.0, 0.5, 1f, 0.2f,
			"ANIMATE", "Pulse", "stage-main",
			true, "ALL", 0.0,
			false, "NEXT_BEAT", "KEEP", "BEAT_GRID",
			1, 0.5, 0.1,
			false, false, 0.0,
			false, 20, 60, 20,
			8, 48, 0.6, 1.5,
			"", "", true
		);

		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input, Map.of(), id -> true, blockId -> true);
		assertInstanceOf(AnimationEventPropertiesEditor.Result.Ok.class, result);
		Map<String, Object> params = ((AnimationEventPropertiesEditor.Result.Ok) result).snapshot().parameters();
		assertEquals("BURST", params.get("dispatchModel"));
		assertFalse(params.containsKey("blocksPerBeat"));
		assertFalse(params.containsKey("stepStartMode"));
	}

	@Test
	void stepModeIncludesPacingParameters() {
		AnimationEventFormInput input = new AnimationEventFormInput(
			0.0, 1.0, 1f, 0.15f,
			"ANIMATE", "Pulse", "stage-main",
			false, "SEQUENTIAL", 0.25,
			true, "IMMEDIATE", "KEEP", "BEAT_GRID",
			2, 0.5, 0.1,
			true, true, 0.5,
			true, 30, 40, 30,
			6, 40, 0.5, 1.2,
			"", "minecraft:gold_block", false
		);

		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input, Map.of(), id -> true, blockId -> blockId.startsWith("minecraft:"));
		assertInstanceOf(AnimationEventPropertiesEditor.Result.Ok.class, result);
		Map<String, Object> params = ((AnimationEventPropertiesEditor.Result.Ok) result).snapshot().parameters();
		assertEquals("STEP", params.get("dispatchModel"));
		assertEquals(2, params.get("blocksPerBeat"));
		assertEquals("SEQUENTIAL", params.get("spatialMode"));
		assertTrue(params.containsKey("entryDurationPercent"));
	}

	@Test
	void rejectsMissingTargetObject() {
		AnimationEventFormInput input = new AnimationEventFormInput(
			0.0, 1.0, 1f, 0.15f,
			"ANIMATE", "Pulse", "",
			true, "ALL", 0.0,
			false, "NEXT_BEAT", "KEEP", "BEAT_GRID",
			1, 0.5, 0.1,
			false, false, 0.0,
			false, 20, 60, 20,
			8, 48, 0.6, 1.5,
			"", "", true
		);
		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input, Map.of(), id -> true, blockId -> true);
		assertInstanceOf(AnimationEventPropertiesEditor.Result.Err.class, result);
	}

	@Test
	void preservesUnmanagedMetadataParameters() {
		Map<String, Object> existing = new HashMap<>(Map.of(
			"mappingProfile", "auto-kick",
			"sourceStem", "drums",
			"energy", 0.4
		));
		AnimationEventFormInput input = new AnimationEventFormInput(
			1.0, 0.5, 1f, 0.2f,
			"ANIMATE", "Pulse", "stage-main",
			true, "ALL", 0.0,
			false, "NEXT_BEAT", "KEEP", "BEAT_GRID",
			1, 0.5, 0.1,
			false, false, 0.0,
			false, 20, 60, 20,
			8, 48, 0.6, 1.5,
			"", "", true
		);

		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input, existing, id -> true, blockId -> true);
		Map<String, Object> params = ((AnimationEventPropertiesEditor.Result.Ok) result).snapshot().parameters();
		assertEquals("auto-kick", params.get("mappingProfile"));
		assertEquals("drums", params.get("sourceStem"));
		assertEquals(1f, params.get("energy"));

		StageEventPayload payload = StageEventPayloadCodec.decode(params);
		assertInstanceOf(StageEventPayload.Animate.class, payload);
		assertEquals(DispatchModel.BURST, ((StageEventPayload.Animate) payload).dispatchModel());
		assertEquals("auto-kick", payload.extensions().get("mappingProfile"));
	}

	@Test
	void placeModeProducesPlacePayload() {
		AnimationEventFormInput input = new AnimationEventFormInput(
			0.0, 0.2, 1f, 0f,
			"PLACE", "pulse", "stage-main",
			true, "ALL", 0.0,
			false, "NEXT_BEAT", "KEEP", "BEAT_GRID",
			1, 0.5, 0.1,
			false, false, 0.0,
			false, 20, 60, 20,
			8, 48, 0.6, 1.5,
			"minecraft:stone", "", true
		);
		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input, Map.of(), id -> true, blockId -> blockId.startsWith("minecraft:"));
		Map<String, Object> params = ((AnimationEventPropertiesEditor.Result.Ok) result).snapshot().parameters();
		StageEventPayload payload = StageEventPayloadCodec.decode(params);
		assertInstanceOf(StageEventPayload.Place.class, payload);
		assertEquals("minecraft:stone", ((StageEventPayload.Place) payload).placeBlockId());
	}
}
