package com.beatblock.selection;

import com.beatblock.client.selection.BeatBlockLassoSelector;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.selection.tools.BrushSelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lasso / Brush 选区 stroke 生命周期集成测试（Manager 层，模拟客户端 tick 提交路径）。
 */
class LassoBrushSelectionIntegrationTest {

	private BeatBlockSelectionManager manager;

	@BeforeEach
	void setUp() {
		manager = BeatBlockSelectionManager.get();
		manager.reset();
		BlockAnimationEngine engine = new BlockAnimationEngine();
		BeatBlockContext context = BeatBlockContext.builder().blockAnimationEngine(engine).build();
		manager.bindContext(() -> context);
		manager.setInteractionCameraPos(new Vec3d(2.5, 64.5, 2.5));
		manager.setSphereBrushRadius(1);
		manager.setMaxBlocks(4096);
	}

	@AfterEach
	void tearDown() {
		BeatBlockSelectionManager.resetContextBindingForTests();
	}

	@Test
	void brushStrokeDedupesSameBlockAndReportsOnRelease() {
		manager.setMode(SelectionMode.BRUSH);
		manager.setOperation(SelectionOperation.NEW);

		BlockPos first = new BlockPos(0, 64, 0);
		BlockPos second = new BlockPos(8, 64, 0);

		manager.stampBrushIfNeeded(null, first, false);
		int afterFirstStamp = manager.getSelectionCount();
		assertTrue(afterFirstStamp > 0);

		manager.stampBrushIfNeeded(null, first, false);
		assertEquals(afterFirstStamp, manager.getSelectionCount(), "same block in one stroke should not re-merge");

		manager.setOperation(SelectionOperation.ADD);
		manager.stampBrushIfNeeded(null, second, false);
		assertTrue(manager.getSelectionCount() > afterFirstStamp);

		manager.finishBrushStroke();
		assertTrue(manager.getLastMessage().contains("笔刷结束"));
		assertTrue(manager.getLastMessage().contains(String.valueOf(manager.getSelectionCount())));
	}

	@Test
	void brushShiftDownUsesAddOperationDuringStroke() {
		manager.setMode(SelectionMode.BRUSH);
		manager.setOperation(SelectionOperation.NEW);

		manager.stampBrushIfNeeded(null, new BlockPos(0, 64, 0), false);
		int afterFirst = manager.getSelectionCount();
		assertTrue(afterFirst > 0);

		manager.stampBrushIfNeeded(null, new BlockPos(4, 64, 0), true);
		assertTrue(manager.getSelectionCount() >= afterFirst);
	}

	@Test
	void brushSubtractOperationRemovesStampedBlocks() {
		manager.setMode(SelectionMode.BRUSH);
		manager.setOperation(SelectionOperation.NEW);
		manager.stampBrushIfNeeded(null, new BlockPos(0, 64, 0), false);
		manager.setOperation(SelectionOperation.ADD);
		manager.stampBrushIfNeeded(null, new BlockPos(8, 64, 0), false);
		int beforeSubtract = manager.getSelectionCount();
		assertTrue(beforeSubtract > 0);

		manager.setOperation(SelectionOperation.SUBTRACT);
		manager.stampBrushIfNeeded(null, new BlockPos(0, 64, 0), false);

		assertTrue(manager.getSelectionCount() < beforeSubtract);
		assertFalse(manager.getSelectedBlocks().contains(new BlockPos(0, 64, 0)));
	}

	@Test
	void brushToolClickMergesSingleStamp() {
		manager.setMode(SelectionMode.BRUSH);
		manager.setOperation(SelectionOperation.NEW);

		BlockPos center = new BlockPos(3, 64, 3);
		BrushSelectionTool.handle(manager, null, center, Direction.UP, false);

		assertTrue(manager.getSelectionCount() > 0);
		assertTrue(manager.getSelectedBlocks().contains(center));
	}

	@Test
	void switchingAwayFromBrushClearsStrokeState() {
		manager.setMode(SelectionMode.BRUSH);
		manager.stampBrushIfNeeded(null, new BlockPos(0, 64, 0), false);

		manager.setMode(SelectionMode.LASSO);
		manager.finishBrushStroke();

		assertFalse(manager.getLastMessage().contains("笔刷结束"));
	}

	@Test
	void lassoCommitAfterPolygonFilterSelectsExpectedBlocks() {
		List<double[]> polygon = List.of(
			new double[] {0, 0},
			new double[] {25, 0},
			new double[] {25, 25},
			new double[] {0, 25}
		);

		List<BlockPos> inside = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				double screenX = x * 10.0;
				double screenY = z * 10.0;
				if (BeatBlockLassoSelector.pointInPolygon(screenX, screenY, polygon)) {
					inside.add(new BlockPos(x, 64, z));
				}
			}
		}

		assertEquals(9, inside.size(), "3×3 grid should fall inside 25×25 screen polygon");

		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(inside, SelectionOperation.NEW);

		assertEquals(9, manager.getSelectionCount());
		assertTrue(manager.getLastMessage().contains("套索"));
	}

	@Test
	void lassoAddThenIntersectNarrowsSelection() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)), SelectionOperation.NEW);

		manager.commitLassoSelection(List.of(new BlockPos(1, 64, 0)), SelectionOperation.ADD);
		assertEquals(3, manager.getSelectionCount());

		manager.commitLassoSelection(List.of(
			new BlockPos(1, 64, 0),
			new BlockPos(9, 64, 9)), SelectionOperation.INTERSECT);
		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getSelectedBlocks().contains(new BlockPos(1, 64, 0)));
	}

	@Test
	void lassoEmptyCommitPreservesExistingSelection() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);

		manager.commitLassoSelection(List.of(), SelectionOperation.ADD);

		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getLastMessage().contains("未选中"));
	}
}
