package com.beatblock.timeline.editing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTrajectoryEventParamsEditorTest {

	@Test
	void rhythmDropMergeSetsPreciseLandingParams() {
		Map<String, Object> params = new HashMap<>();
		var input = new WorldTrajectoryEventParamsEditor.FormInput(
			"10", "64", "-3", "8.0", "2.5", "0.95");

		var result = WorldTrajectoryEventParamsEditor.merge(params, "RhythmDrop", input);
		assertInstanceOf(WorldTrajectoryEventParamsEditor.MergeResult.Ok.class, result);

		Map<String, Object> merged = ((WorldTrajectoryEventParamsEditor.MergeResult.Ok) result).parameters();
		assertEquals(10, merged.get("singleBlockX"));
		assertEquals(64, merged.get("singleBlockY"));
		assertEquals(-3, merged.get("singleBlockZ"));
		assertEquals(8.0, merged.get("meteorHeight"));
		assertEquals(0.0, merged.get("meteorScatter"));
		assertEquals(0.95, merged.get("impactThreshold"));
		assertEquals("rhythm_impact", merged.get("impactVfxKind"));
	}

	@Test
	void meteorMergeKeepsScatter() {
		Map<String, Object> params = new HashMap<>();
		var input = new WorldTrajectoryEventParamsEditor.FormInput(
			"", "", "", "12", "3.5", "0.92");

		var result = WorldTrajectoryEventParamsEditor.merge(params, "Meteor", input);
		assertInstanceOf(WorldTrajectoryEventParamsEditor.MergeResult.Ok.class, result);
		Map<String, Object> merged = ((WorldTrajectoryEventParamsEditor.MergeResult.Ok) result).parameters();
		assertEquals(12.0, merged.get("meteorHeight"));
		assertEquals(3.5, merged.get("meteorScatter"));
		assertFalse(merged.containsKey("impactThreshold"));
		assertFalse(merged.containsKey("singleBlockX"));
	}

	@Test
	void rejectsPartialSingleBlockCoordinates() {
		Map<String, Object> params = new HashMap<>();
		var input = new WorldTrajectoryEventParamsEditor.FormInput(
			"1", "64", "", "6", "0", "0.92");

		var result = WorldTrajectoryEventParamsEditor.merge(params, "RhythmDrop", input);
		assertInstanceOf(WorldTrajectoryEventParamsEditor.MergeResult.Err.class, result);
	}

	@Test
	void clearRemovesTrajectoryKeys() {
		Map<String, Object> params = new HashMap<>(Map.of(
			"singleBlockX", 1,
			"meteorHeight", 6.0,
			"impactThreshold", 0.92
		));
		WorldTrajectoryEventParamsEditor.clear(params);
		assertTrue(params.isEmpty());
	}
}
