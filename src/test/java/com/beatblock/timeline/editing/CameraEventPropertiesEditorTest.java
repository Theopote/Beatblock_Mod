package com.beatblock.timeline.editing;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.camera.CameraSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
