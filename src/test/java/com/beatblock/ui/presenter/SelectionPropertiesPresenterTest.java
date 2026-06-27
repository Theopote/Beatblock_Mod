package com.beatblock.ui.presenter;

import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionPropertiesPresenterTest {

	private BeatBlockSelectionManager manager;
	private SelectionPropertiesPresenter presenter;

	@BeforeEach
	void setUp() {
		manager = BeatBlockSelectionManager.get();
		manager.reset();
		manager.setMode(SelectionMode.BRUSH);
		manager.setOperation(SelectionOperation.ADD);
		manager.setBrushShape(BrushShape.CUBE);
		manager.setSphereBrushRadius(5);
		manager.setPlaneSliceFaceOverride(Direction.UP);
		presenter = new SelectionPropertiesPresenter(() -> manager);
	}

	@Test
	void currentViewStateReflectsManagerSettings() {
		SelectionPropertiesViewState state = presenter.currentViewState();
		assertEquals(SelectionMode.BRUSH, state.mode());
		assertEquals(SelectionOperation.ADD, state.operation());
		assertEquals(BrushShape.CUBE, state.brushShape());
		assertEquals(5, state.sphereBrushRadius());
		assertEquals(Direction.UP, state.planeSliceFaceOverride());
	}

	@Test
	void setOperationUpdatesManager() {
		presenter.setOperation(SelectionOperation.INTERSECT);
		assertEquals(SelectionOperation.INTERSECT, manager.getOperation());
	}

	@Test
	void labelHelpersReturnChineseLabels() {
		assertEquals("加选", SelectionPropertiesPresenter.operationLabel(SelectionOperation.ADD));
		assertEquals("笔刷", SelectionPropertiesPresenter.modeTitle(SelectionMode.BRUSH));
		assertEquals(2, SelectionPropertiesPresenter.planeFaceIndex(
			Direction.DOWN,
			new Direction[] { null, Direction.UP, Direction.DOWN }
		));
	}

	@Test
	void currentViewStateReturnsDefaultsWhenManagerMissing() {
		var missing = new SelectionPropertiesPresenter(() -> null);
		SelectionPropertiesViewState state = missing.currentViewState();
		assertEquals(SelectionMode.OFF, state.mode());
		assertEquals(SelectionOperation.NEW, state.operation());
		assertEquals(0, state.selectionCount());
	}

	@Test
	void settersNoOpWhenManagerMissing() {
		var missing = new SelectionPropertiesPresenter(() -> null);
		missing.setOperation(SelectionOperation.SUBTRACT);
		missing.setMaxBlocks(42);
		missing.clearSelection();
	}

	@Test
	void clearSelectionClearsManagerBlocks() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);
		assertEquals(1, manager.getSelectionCount());

		presenter.clearSelection();

		assertEquals(0, manager.getSelectionCount());
	}

	@Test
	void setIncludeAirAndMaxBlocksUpdateManager() {
		presenter.setIncludeAir(true);
		presenter.setMaxBlocks(50_000);
		assertTrue(manager.isIncludeAir());
		assertEquals(50_000, manager.getMaxBlocks());
	}

	@Test
	void setMaxDistanceAndLineThicknessUpdateManager() {
		presenter.setMaxDistanceFromCamera(256);
		presenter.setLineThicknessRadius(4);
		assertEquals(256, manager.getMaxDistanceFromCamera());
		assertEquals(4, manager.getLineThicknessRadius());
	}

	@Test
	void clearMessageClearsManagerMessage() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(), SelectionOperation.NEW);
		assertFalse(manager.getLastMessage().isBlank());

		presenter.clearMessage();

		assertTrue(manager.getLastMessage().isBlank());
	}
}
