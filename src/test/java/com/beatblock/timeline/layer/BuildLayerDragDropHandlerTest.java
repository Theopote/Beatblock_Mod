package com.beatblock.timeline.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.layer.BindLayerToTrackCommand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerDragDropHandlerTest {

	@Test
	void validateRejectsVisibleAndBoundLayers() {
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Layer", List.of(new BlockPos(0, 64, 0)));
		BuildLayer visible = new BuildLayer(
			"v", "Visible", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		BuildLayer bound = new BuildLayer(
			"b", "Bound", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), "clip-1");

		assertNotNull(BuildLayerDragDropHandler.validateLayerForBind(null));
		assertNotNull(BuildLayerDragDropHandler.validateLayerForBind(visible));
		assertNotNull(BuildLayerDragDropHandler.validateLayerForBind(bound));
		assertNull(BuildLayerDragDropHandler.validateLayerForBind(new BuildLayer(
			"h", "Hidden", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), null)));
	}

	@Test
	void computeClipDurationScalesWithBlockCount() {
		Timeline timeline = Timeline.createDefault();
		timeline.setBpm(120.0);

		List<BlockPos> blocks = IntStream.range(0, 8)
			.mapToObj(i -> new BlockPos(i, 64, 0))
			.toList();
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Big", blocks);
		BuildLayer layer = new BuildLayer(
			"big", "Big", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), null);

		double duration = BuildLayerDragDropHandler.computeClipDuration(layer, timeline);
		assertTrue(duration >= BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS);
	}

	@Test
	void decodeLayerIdStripsNullTerminator() {
		byte[] raw = new byte[]{'l', 'a', 'y', 'e', 'r', '-', '1', 0, 0};
		assertEquals("layer-1", BuildLayerDragDropHandler.decodeLayerId(raw));
	}
}
