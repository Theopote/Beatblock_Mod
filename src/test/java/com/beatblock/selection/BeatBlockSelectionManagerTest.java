package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatBlockSelectionManagerTest {

	private BeatBlockSelectionManager manager;

	@BeforeEach
	void setUp() {
		manager = BeatBlockSelectionManager.get();
		manager.reset();
	}

	@Test
	void commitLassoSelectionReplacesSelectionOnNewOperation() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)), SelectionOperation.NEW);

		assertEquals(2, manager.getSelectionCount());
		assertTrue(manager.getSelectedBlocks().contains(new BlockPos(0, 64, 0)));
	}

	@Test
	void commitLassoSelectionSupportsAddSubtractAndIntersect() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)), SelectionOperation.NEW);

		manager.commitLassoSelection(List.of(new BlockPos(2, 64, 0)), SelectionOperation.ADD);
		assertEquals(3, manager.getSelectionCount());

		manager.commitLassoSelection(List.of(new BlockPos(1, 64, 0)), SelectionOperation.SUBTRACT);
		assertEquals(2, manager.getSelectionCount());

		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(9, 64, 0)), SelectionOperation.INTERSECT);
		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getSelectedBlocks().contains(new BlockPos(0, 64, 0)));
	}

	@Test
	void emptyLassoReportsMessageWithoutChangingSelection() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);
		manager.commitLassoSelection(List.of(), SelectionOperation.ADD);

		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getLastMessage().contains("未选中"));
	}

	@Test
	void clearSelectionResetsCountAndBoundingBox() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(5, 64, 5)), SelectionOperation.NEW);
		assertEquals(new BlockPos(5, 64, 5), manager.getBoundingMin());

		manager.clearSelection();
		assertEquals(0, manager.getSelectionCount());
		assertNull(manager.getBoundingMin());
	}

	@Test
	void clampsBrushAndReachSettings() {
		manager.setSphereBrushRadius(999);
		assertEquals(32, manager.getSphereBrushRadius());

		manager.setMaxBlocks(100);
		assertEquals(1024, manager.getMaxBlocks());

		manager.setMaxDistanceFromCamera(-5);
		assertEquals(8, manager.getMaxDistanceFromCamera());
	}
}
