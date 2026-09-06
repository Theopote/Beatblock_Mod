package com.beatblock.timeline.editing;

import com.beatblock.automap.camera.CameraSegmentSemantics;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraSegmentParamSchema;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraEventPropertiesEditorTest {

	@Test
	void buildSegmentSnapshotUpdatesClipEndAndMetadata() {
		Timeline timeline = Timeline.createDefault();
		Map<String, Object> existing = new HashMap<>(Map.of("kind", "DOLLY", "startX", 1.0));
		var result = CameraEventPropertiesEditor.buildSegmentSnapshot(
			2.0, 3.0, false, CameraSegmentKind.DOLLY, existing, Map.of("endX", "5"),
			timeline, "clip-1"
		);
		assertInstanceOf(CameraEventPropertiesEditor.Result.Ok.class, result);
		AnimationEventSnapshot snapshot = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals(2.0, snapshot.clipStartSeconds(), 1e-9);
		assertEquals(5.0, snapshot.clipEndSeconds(), 1e-9);
		assertEquals(5.0, snapshot.parameters().get("endX"));
		assertEquals("0", snapshot.timelineMetadata().values().iterator().next());
	}

	@Test
	void buildKeyframeSnapshotClampsTimeToClipRange() {
		var result = CameraEventPropertiesEditor.buildKeyframeSnapshot(
			1.0, 4.0, 10.0, 0, 64, 0, 90, 0, "LINEAR", Map.of("kind", "PATH")
		);
		AnimationEventSnapshot snapshot = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals(4.0, snapshot.timeSeconds(), 1e-9);
		assertEquals("LINEAR", snapshot.parameters().get("ease"));
	}

	@Test
	void buildKindChangeSnapshotRemovesStaleParamsAndSetsKind() {
		Map<String, Object> existing = new HashMap<>(Map.of(
			"kind", "DOLLY",
			"startX", 1.0,
			"endX", 5.0,
			"radius", 99.0
		));
		var result = CameraEventPropertiesEditor.buildKindChangeSnapshot(
			CameraSegmentKind.ORBIT,
			existing,
			Map.of("height", 3.0),
			0.0,
			4.0
		);
		AnimationEventSnapshot snapshot = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals("ORBIT", snapshot.parameters().get("kind"));
		assertFalse(snapshot.parameters().containsKey("startX"));
		assertFalse(snapshot.parameters().containsKey("endX"));
		assertEquals(99.0, snapshot.parameters().get("radius"));
		assertEquals(3.0, snapshot.parameters().get("height"));
	}

	@Test
	void orbitToCraneDropsOrbitOnlyFieldsAndKeepsSharedSemantics() {
		Map<String, Object> existing = new HashMap<>();
		existing.put("kind", "ORBIT");
		existing.put("targetX", 10.0);
		existing.put("targetY", 64.0);
		existing.put("targetZ", 2.0);
		existing.put("radius", 12.0);
		existing.put("height", 4.0);
		existing.put("yawStartDeg", 0.0);
		existing.put("yawEndDeg", 270.0);
		existing.put(CameraSegmentSemantics.KEY_EASE, "EASE_OUT");
		existing.put(CameraSegmentSemantics.KEY_TRANSITION, "SMOOTH_MOVE");
		existing.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_KIND, "STAGE_OBJECT");
		existing.put(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF, "main-building");
		existing.put(TimelineGenerationMetadataSupport.PARAM_ORIGIN, TimelineEventOrigin.MANUAL.name());

		Map<String, Object> defaults = Map.of(
			"startX", 1.0,
			"startY", 70.0,
			"startZ", 3.0,
			"endX", 1.0,
			"endY", 78.0,
			"endZ", 3.0,
			"yawDeg", 45.0,
			"pitchDeg", -10.0
		);

		var reminted = CameraSegmentParamSchema.remintForKind(existing, CameraSegmentKind.CRANE, defaults);
		assertEquals("CRANE", reminted.get("kind"));
		assertFalse(reminted.containsKey("targetX"));
		assertFalse(reminted.containsKey("radius"));
		assertFalse(reminted.containsKey("yawStartDeg"));
		assertFalse(reminted.containsKey("yawEndDeg"));
		assertEquals(1.0, reminted.get("startX"));
		assertEquals(45.0, reminted.get("yawDeg"));
		assertEquals("EASE_OUT", reminted.get(CameraSegmentSemantics.KEY_EASE));
		assertEquals("SMOOTH_MOVE", reminted.get(CameraSegmentSemantics.KEY_TRANSITION));
		assertEquals("main-building", reminted.get(CameraSegmentSemantics.KEY_FOLLOW_SUBJECT_REF));
		assertEquals(TimelineEventOrigin.MANUAL.name(), reminted.get(TimelineGenerationMetadataSupport.PARAM_ORIGIN));

		var result = CameraEventPropertiesEditor.buildKindChangeSnapshot(
			CameraSegmentKind.CRANE, existing, defaults, 0.0, 4.0);
		AnimationEventSnapshot snapshot = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals(reminted, snapshot.parameters());
	}

	@Test
	void buildSegmentSnapshotPreservesSharedSemantics() {
		Map<String, Object> existing = new HashMap<>();
		existing.put("kind", "DOLLY");
		existing.put("startX", 1.0);
		existing.put(CameraSegmentSemantics.KEY_COLLISION_POLICY, "AVOID_BLOCKS");
		existing.put(TimelineGenerationMetadataSupport.PARAM_ORIGIN, "MANUAL");
		existing.put("radius", 99.0); // stale pollution

		var result = CameraEventPropertiesEditor.buildSegmentSnapshot(
			0.0, 2.0, true, CameraSegmentKind.DOLLY, existing, Map.of("startY", "65"),
			null, "clip-1"
		);
		Map<String, Object> params = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot().parameters();
		assertEquals(65.0, params.get("startY"));
		assertEquals("AVOID_BLOCKS", params.get(CameraSegmentSemantics.KEY_COLLISION_POLICY));
		assertEquals("MANUAL", params.get(TimelineGenerationMetadataSupport.PARAM_ORIGIN));
		assertFalse(params.containsKey("radius"));
		assertTrue(params.containsKey("startX"));
	}

	@Test
	void shiftClipEventTimesAppliesDeltaAndClamp() {
		Map<String, Double> shifted = CameraEventPropertiesEditor.shiftClipEventTimes(
			Map.of("e1", 2.0, "e2", 3.5).entrySet(),
			1.0, 2.0, 4.5
		);
		assertEquals(3.0, shifted.get("e1"), 1e-9);
		assertEquals(4.5, shifted.get("e2"), 1e-9);
	}

	@Test
	void buildSegmentSnapshotClampsShortDuration() {
		var result = CameraEventPropertiesEditor.buildSegmentSnapshot(
			1.0, 0.01, true, CameraSegmentKind.DOLLY, Map.of("kind", "DOLLY"), Map.of(),
			null, "clip-1"
		);
		AnimationEventSnapshot snapshot = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals(1.05, snapshot.clipEndSeconds(), 1e-9);
	}
}
