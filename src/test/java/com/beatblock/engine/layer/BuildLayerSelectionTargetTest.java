package com.beatblock.engine.layer;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.generation.AnimationDropTargetResolver;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer selection → preferred StageObject ids for animation preset drops.
 */
class BuildLayerSelectionTargetTest {

	private BuildLayerManager manager;
	private CommandManager commands;

	@BeforeEach
	void setUp() {
		manager = new BuildLayerManager(new StageObjectSystem());
		commands = new CommandManager();
	}

	@Test
	void selectedLayersProvidePreferredStageObjectIds() {
		commands.execute(new CreateLayerCommand(manager, "A", List.of(new BlockPos(1, 64, 0))));
		commands.execute(new CreateLayerCommand(manager, "B", List.of(new BlockPos(2, 64, 0))));
		List<String> order = manager.getLayerOrderIds();
		assertEquals(2, order.size());

		manager.selectLayer(order.get(0), false, false, order);
		List<String> preferred = manager.getSelectedStageObjectIds();
		assertEquals(1, preferred.size());
		assertEquals(manager.get(order.get(0)).getStageObjectId(), preferred.getFirst());

		var resolved = AnimationDropTargetResolver.resolve(
			preferred,
			List.of(),
			List.of("noise-a", "noise-b")
		);
		assertEquals(AnimationDropTargetResolver.Mode.SINGLE, resolved.mode());
		assertEquals(preferred, resolved.targetObjectIds());
	}

	@Test
	void multiLayerSelectionYieldsMultiMode() {
		commands.execute(new CreateLayerCommand(manager, "A", List.of(new BlockPos(3, 64, 0))));
		commands.execute(new CreateLayerCommand(manager, "B", List.of(new BlockPos(4, 64, 0))));
		List<String> order = manager.getLayerOrderIds();

		manager.selectLayer(order.get(0), false, false, order);
		manager.selectLayer(order.get(1), true, false, order);

		List<String> preferred = manager.getSelectedStageObjectIds();
		assertEquals(2, preferred.size());

		var resolved = AnimationDropTargetResolver.resolve(preferred, List.of(), preferred);
		assertEquals(AnimationDropTargetResolver.Mode.MULTI, resolved.mode());
		assertEquals(2, resolved.targetsForEventCreation().size());
	}

	@Test
	void deleteLayerPrunesSelection() {
		commands.execute(new CreateLayerCommand(manager, "Only", List.of(new BlockPos(5, 64, 0))));
		String layerId = manager.getLayerOrderIds().getFirst();
		manager.setSelectionTo(layerId);
		assertEquals(1, manager.getSelectedLayerIds().size());

		manager.dissolveLayer(manager.get(layerId));
		assertTrue(manager.getSelectedLayerIds().isEmpty());
		assertTrue(manager.getSelectedStageObjectIds().isEmpty());
	}
}
