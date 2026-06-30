package com.beatblock.ui.presenter;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayersPresenterTest {

	private BuildLayerManager layerManager;
	private CommandManager commandManager;
	private BuildLayersPresenter presenter;

	@BeforeEach
	void setUp() {
		layerManager = new BuildLayerManager(new StageObjectSystem());
		commandManager = new CommandManager();
		presenter = new BuildLayersPresenter(() -> commandManager, () -> layerManager);
	}

	@Test
	void renameLayerRejectsEmptyName() {
		BlockPos pos = new BlockPos(0, 64, 0);
		CreateLayerCommand create = new CreateLayerCommand(layerManager, "Tower", List.of(pos));
		commandManager.execute(create);
		BuildLayer layer = create.getCreatedLayer();
		assertNotNull(layer);

		var outcome = presenter.renameLayer(layer.getId(), "   ");
		assertFalse(outcome.result().ok());
		assertEquals(layer.getName(), outcome.committedName());
	}

	@Test
	void renameLayerExecutesCommandWhenValid() {
		BlockPos pos = new BlockPos(1, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Old", List.of(pos)));
		BuildLayer layer = layerManager.getAll().iterator().next();

		var outcome = presenter.renameLayer(layer.getId(), "New Name");
		assertTrue(outcome.result().ok());
		assertEquals("New Name", layerManager.get(layer.getId()).getName());
		assertEquals("New Name", outcome.committedName());
	}

	@Test
	void createLayerFromSelectionReturnsCreatedId() {
		BlockPos pos = new BlockPos(2, 64, 0);
		var outcome = presenter.createLayerFromSelection("Layer A", List.of(pos));

		assertTrue(outcome.result().ok());
		assertNotNull(outcome.createdLayerId());
		assertEquals(1, outcome.blocksToRemoveFromSelection().size());
		assertTrue(layerManager.isBlockClaimed(pos));
	}

	@Test
	void createLayerFromSelectionRejectsEmptySelection() {
		var outcome = presenter.createLayerFromSelection("Layer A", List.of());
		assertFalse(outcome.result().ok());
		assertEquals(BBTexts.get("beatblock.message.create_selection_first"), outcome.result().messageOrEmpty());
	}

	@Test
	void renameLayerRejectsDuplicateName() {
		BlockPos first = new BlockPos(3, 64, 0);
		BlockPos second = new BlockPos(4, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Alpha", List.of(first)));
		commandManager.execute(new CreateLayerCommand(layerManager, "Beta", List.of(second)));

		BuildLayer beta = layerManager.getAll().stream()
			.filter(layer -> "Beta".equals(layer.getName()))
			.findFirst()
			.orElseThrow();

		var outcome = presenter.renameLayer(beta.getId(), "Alpha");
		assertFalse(outcome.result().ok());
		assertEquals("Beta", outcome.committedName());
	}

	@Test
	void deleteLayerRemovesLayer() {
		BlockPos pos = new BlockPos(5, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Delete Me", List.of(pos)));
		BuildLayer layer = layerManager.getAll().iterator().next();

		var outcome = presenter.deleteLayer(layer.getId());
		assertTrue(outcome.result().ok());
		assertTrue(layerManager.getAll().isEmpty());
	}

	@Test
	void registerRestoredLayerCanBeRenamed() {
		layerManager.registerRestored(new BuildLayer(
			"layer-x",
			"Original",
			StageObjectSystem.fromBlocks("s1", "Original", List.of(new BlockPos(6, 64, 0))),
			LayerVisibilityState.FREE_VISIBLE,
			Map.of(),
			null
		));

		var outcome = presenter.renameLayer("layer-x", "Updated");
		assertTrue(outcome.result().ok());
		assertEquals("Updated", layerManager.get("layer-x").getName());
	}

	@Test
	void toggleVisibilityFailsWithoutWorldContext() {
		BlockPos pos = new BlockPos(7, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Visible", List.of(pos)));
		BuildLayer layer = layerManager.getAll().iterator().next();

		var outcome = presenter.toggleVisibility(layer.getId());

		assertFalse(outcome.result().ok());
		assertEquals(BBTexts.get("beatblock.message.no_world_context"), outcome.result().messageOrEmpty());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layerManager.get(layer.getId()).getState());
	}

	@Test
	void createLayerFromSelectionRejectsFullyClaimedBlocks() {
		BlockPos pos = new BlockPos(8, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Existing", List.of(pos)));

		var outcome = presenter.createLayerFromSelection("Another", List.of(pos));

		assertFalse(outcome.result().ok());
		assertTrue(outcome.result().messageOrEmpty().contains(BBTexts.get("beatblock.message.all_blocks_claimed")));
	}

	@Test
	void createLayerFromSelectionSkipsAlreadyClaimedBlocks() {
		BlockPos claimed = new BlockPos(9, 64, 0);
		BlockPos free = new BlockPos(10, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Existing", List.of(claimed)));

		var outcome = presenter.createLayerFromSelection("Mixed", List.of(claimed, free));

		assertTrue(outcome.result().ok());
		assertTrue(layerManager.isBlockClaimed(free));
		assertEquals(BBTexts.get("beatblock.message.layer_created_skipped", "Mixed", 1), outcome.result().messageOrEmpty());
		assertEquals(1, outcome.blocksToRemoveFromSelection().size());
	}

	@Test
	void renameLayerFailsWhenLayerMissing() {
		var outcome = presenter.renameLayer("missing-layer", "Name");
		assertFalse(outcome.result().ok());
		assertEquals(BBTexts.get("beatblock.message.layer_not_found"), outcome.result().messageOrEmpty());
	}

	@Test
	void deleteLayerFailsWhenLayerMissing() {
		var outcome = presenter.deleteLayer("missing-layer");
		assertFalse(outcome.result().ok());
		assertEquals(BBTexts.get("beatblock.message.layer_not_found"), outcome.result().messageOrEmpty());
	}

	@Test
	void selectLayerShiftSelectsRange() {
		BlockPos a = new BlockPos(11, 64, 0);
		BlockPos b = new BlockPos(12, 64, 0);
		BlockPos c = new BlockPos(13, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "A", List.of(a)));
		commandManager.execute(new CreateLayerCommand(layerManager, "B", List.of(b)));
		commandManager.execute(new CreateLayerCommand(layerManager, "C", List.of(c)));
		List<String> order = presenter.buildDisplayOrder();
		assertEquals(3, order.size());

		presenter.selectLayer(order.get(0), false, false, order);
		presenter.selectLayer(order.get(2), false, true, order);

		assertEquals(Set.of(order.get(0), order.get(1), order.get(2)), presenter.selectedLayerIds());
	}

	@Test
	void selectLayerCtrlTogglesSelection() {
		BlockPos a = new BlockPos(14, 64, 0);
		BlockPos b = new BlockPos(15, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "One", List.of(a)));
		commandManager.execute(new CreateLayerCommand(layerManager, "Two", List.of(b)));
		List<String> order = presenter.buildDisplayOrder();

		presenter.selectLayer(order.get(0), false, false, order);
		presenter.selectLayer(order.get(1), true, false, order);
		assertEquals(Set.of(order.get(0), order.get(1)), presenter.selectedLayerIds());

		presenter.selectLayer(order.get(0), true, false, order);
		assertEquals(Set.of(order.get(1)), presenter.selectedLayerIds());
	}

	@Test
	void reorderLayerBeforeMovesLayer() {
		BlockPos a = new BlockPos(16, 64, 0);
		BlockPos b = new BlockPos(17, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "First", List.of(a)));
		commandManager.execute(new CreateLayerCommand(layerManager, "Second", List.of(b)));
		List<String> order = presenter.buildDisplayOrder();
		assertEquals(2, order.size());

		var result = presenter.reorderLayerBefore(order.get(1), order.get(0));
		assertTrue(result.ok());

		List<String> reordered = presenter.buildDisplayOrder();
		assertEquals(order.get(1), reordered.get(0));
		assertEquals(order.get(0), reordered.get(1));
	}
}
