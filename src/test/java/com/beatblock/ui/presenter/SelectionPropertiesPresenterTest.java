package com.beatblock.ui.presenter;

import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionPropertiesPresenterTest {

	private BeatBlockSelectionManager manager;
	private SelectionPropertiesPresenter presenter;

	@BeforeEach
	void setUp() {
		manager = BeatBlockSelectionManager.get();
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
}
