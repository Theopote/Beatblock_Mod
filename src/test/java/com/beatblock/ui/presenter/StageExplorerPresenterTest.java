package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class StageExplorerPresenterTest {

	private StageExplorerPresenter presenter;
	private ToolPanelPresenter toolPanel;
	private BuildLayerManager layerManager;

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		layerManager = context.buildLayerManager();
		context.blockAnimationEngine().getStageObjectSystem().clear();
		toolPanel = PresenterFactories.toolPanelPresenter(context);
		presenter = PresenterFactories.stageExplorerPresenter(context);
	}

	@Test
	void listStandaloneStageObjectsExcludesLayerOwned() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(1, 64, 1)), SelectionOperation.NEW);
		var solo = toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Solo", false, GroupSortingStrategy.SEQUENTIAL, 0.0));
		selection.commitLassoSelection(List.of(new BlockPos(2, 64, 2)), SelectionOperation.NEW);
		layerManager.createFromSelection("Layer A", List.of(new BlockPos(2, 64, 2)));

		var standalone = presenter.performanceObjects();
		assertEquals(1, standalone.size());
		assertEquals(solo.objectId(), standalone.getFirst().id());
	}

	@Test
	void selectingPerformanceObjectFeedsAnimationTargets() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(1, 64, 1)), SelectionOperation.NEW);
		var solo = toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Tower", false, GroupSortingStrategy.SEQUENTIAL, 0.0));

		presenter.selectPerformanceObject(solo.objectId(), false, false);
		assertTrue(presenter.isPerformanceObjectSelected(solo.objectId()));
		assertEquals(List.of(solo.objectId()), presenter.selectedAnimationTargetIds());

		presenter.clearAnimationTargetSelection();
		assertEquals(0, presenter.selectedAnimationTargetCount());
	}

	@Test
	void focusAnimationTargetSelectsStandalonePerformanceObject() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(3, 64, 3)), SelectionOperation.NEW);
		var solo = toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Focus Me", false, GroupSortingStrategy.SEQUENTIAL, 0.0));

		presenter.focusAnimationTarget(solo.objectId());
		assertTrue(presenter.isPerformanceObjectSelected(solo.objectId()));
		assertEquals(List.of(solo.objectId()), presenter.selectedAnimationTargetIds());
	}

	@Test
	void filteredPerformanceObjectsMatchNameOrId() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(4, 64, 4)), SelectionOperation.NEW);
		var alpha = toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Alpha Tower", false, GroupSortingStrategy.SEQUENTIAL, 0.0));
		selection.commitLassoSelection(List.of(new BlockPos(5, 64, 5)), SelectionOperation.NEW);
		toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Beta", false, GroupSortingStrategy.SEQUENTIAL, 0.0));

		var byName = presenter.filteredPerformanceObjects("tower");
		assertEquals(1, byName.size());
		assertEquals(alpha.objectId(), byName.getFirst().id());

		var byId = presenter.filteredPerformanceObjects(alpha.objectId());
		assertEquals(1, byId.size());
		assertEquals("Alpha Tower", byId.getFirst().name());
	}

	@Test
	void layerFilterMatchesNameStateAndBoundClip() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(6, 64, 6)), SelectionOperation.NEW);
		BuildLayer hidden = layerManager.createFromSelection("Hidden Layer", List.of(new BlockPos(6, 64, 6)));
		selection.commitLassoSelection(List.of(new BlockPos(7, 64, 7)), SelectionOperation.NEW);
		BuildLayer bound = layerManager.createFromSelection("Visible Layer", List.of(new BlockPos(7, 64, 7)));
		layerManager.showLayer(bound, null);
		layerManager.bindToClip(bound, "clip-42");

		assertTrue(StageExplorerPresenter.layerMatchesFilter(hidden, "hidden"));
		assertTrue(StageExplorerPresenter.layerMatchesFilter(bound, "bound"));
		assertTrue(StageExplorerPresenter.layerMatchesFilter(bound, "clip-42"));
		assertEquals(1, StageExplorerPresenter.countFilteredLayers(layerManager, "Hidden"));
	}

	@Test
	void enableBuildRevealWrapsStandalonePerformanceObject() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(8, 64, 8)), SelectionOperation.NEW);
		var solo = toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Upgrade Me", false, GroupSortingStrategy.SEQUENTIAL, 0.0));

		assertTrue(presenter.canEnableBuildReveal(solo.objectId()));
		var result = presenter.enableBuildReveal(solo.objectId());
		assertTrue(result.ok());
		assertFalse(presenter.canEnableBuildReveal(solo.objectId()));
		assertEquals(0, presenter.performanceObjects().size());
	}

	@Test
	void listStandaloneStageObjectsIsSortedByName() {
		BeatBlockSelectionManager selection = BeatBlockSelectionManager.get();
		selection.reset();
		selection.setMode(SelectionMode.LASSO);
		selection.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);
		toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Zulu", false, GroupSortingStrategy.SEQUENTIAL, 0.0));
		selection.commitLassoSelection(List.of(new BlockPos(1, 64, 0)), SelectionOperation.NEW);
		toolPanel.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Alpha", false, GroupSortingStrategy.SEQUENTIAL, 0.0));

		var names = presenter.performanceObjects().stream().map(ToolPanelPresenter.StageObjectListItem::name).toList();
		assertEquals(List.of("Alpha", "Zulu"), names);
	}
}
