package com.beatblock.ui.presenter;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPanelPresenterTest {

	private BeatBlockSelectionManager selectionManager;
	private StageObjectSystem stageObjectSystem;
	private ToolPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		selectionManager = BeatBlockSelectionManager.get();
		selectionManager.reset();
		selectionManager.setMode(SelectionMode.BOX);
		stageObjectSystem = new StageObjectSystem();
		presenter = new ToolPanelPresenter(
			() -> selectionManager,
			() -> stageObjectSystem,
			() -> null,
			() -> new BlockPos(10, 64, 10)
		);
	}

	@Test
	void fillCornersFromSelectionUsesBoundingBox() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(
			new BlockPos(1, 64, 2),
			new BlockPos(5, 70, 8)
		), SelectionOperation.NEW);

		var outcome = presenter.fillCornersFromSelection();
		assertTrue(outcome.result().ok());
		assertEquals(new BlockPos(1, 64, 2), outcome.corners().posA());
		assertEquals(new BlockPos(5, 70, 8), outcome.corners().posB());
	}

	@Test
	void createFromSelectionSnapshotRegistersObject() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		var outcome = presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Tower",
			false,
			GroupSortingStrategy.SEQUENTIAL,
			0.25
		));

		assertTrue(outcome.result().ok());
		assertNotNull(outcome.objectId());
		assertEquals(1, stageObjectSystem.size());
		assertEquals("Tower", stageObjectSystem.get(outcome.objectId()).getName());
	}

	@Test
	void buildUniqueStageObjectIdAvoidsCollisions() {
		stageObjectSystem.register(StageObjectSystem.fromBlocks("tower", "Tower", List.of(new BlockPos(0, 64, 0))));
		String id = ToolPanelPresenter.buildUniqueStageObjectId(stageObjectSystem, "Tower");
		assertEquals("tower_2", id);
	}

	@Test
	void estimateSelectionVolumeComputesInclusiveBounds() {
		long volume = ToolPanelPresenter.estimateSelectionVolume(
			new BlockPos(0, 0, 0),
			new BlockPos(1, 1, 1)
		);
		assertEquals(8, volume);
	}

	@Test
	void setCornerFromCrosshairUsesPicker() {
		var outcome = presenter.setCornerFromCrosshair(true);
		assertTrue(outcome.result().ok());
		assertEquals(new BlockPos(10, 64, 10), outcome.corners().posA());
	}

	@Test
	void removeStageObjectDeletesRegisteredObject() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(new BlockPos(2, 64, 2)), SelectionOperation.NEW);
		var created = presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Obj", false, GroupSortingStrategy.ALL, 0.0));
		assertTrue(created.result().ok());

		var removed = presenter.removeStageObject(created.objectId());
		assertTrue(removed.ok());
		assertEquals(0, stageObjectSystem.size());
	}

	@Test
	void fillCornersFromSelectionFailsWithoutSelection() {
		var outcome = presenter.fillCornersFromSelection();
		assertFalse(outcome.result().ok());
	}
}
