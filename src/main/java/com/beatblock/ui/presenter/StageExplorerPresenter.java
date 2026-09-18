package com.beatblock.ui.presenter;

import com.beatblock.engine.StageObjectLifecycleService;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Stage Explorer 统一视图：演出对象列表 + 建造图层选择状态。
 */
public final class StageExplorerPresenter {

	private final ToolPanelPresenter toolPanel;
	private final BuildLayersPresenter layers;
	private final Supplier<BuildLayerManager> layerManager;

	public StageExplorerPresenter(ToolPanelPresenter toolPanel, BuildLayersPresenter layers) {
		this(toolPanel, layers, () -> {
			BuildLayerManager manager = layers.currentLayerManager();
			return manager;
		});
	}

	StageExplorerPresenter(
		ToolPanelPresenter toolPanel,
		BuildLayersPresenter layers,
		Supplier<BuildLayerManager> layerManager
	) {
		this.toolPanel = toolPanel;
		this.layers = layers;
		this.layerManager = layerManager;
	}

	public static StageExplorerPresenter create(BeatBlockContext context) {
		return PresenterFactories.stageExplorerPresenter(context);
	}

	public ToolPanelPresenter toolPanel() {
		return toolPanel;
	}

	public BuildLayersPresenter layers() {
		return layers;
	}

	public List<ToolPanelPresenter.StageObjectListItem> performanceObjects() {
		return toolPanel.listStandaloneStageObjects();
	}

	public List<ToolPanelPresenter.StageObjectListItem> filteredPerformanceObjects(@Nullable String query) {
		String normalized = normalizeSearchQuery(query);
		List<ToolPanelPresenter.StageObjectListItem> out = new ArrayList<>();
		for (ToolPanelPresenter.StageObjectListItem item : performanceObjects()) {
			if (performanceObjectMatchesFilter(item, normalized)) {
				out.add(item);
			}
		}
		return out;
	}

	public static String normalizeSearchQuery(@Nullable String query) {
		return query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
	}

	public static boolean textMatchesQuery(String normalizedQuery, @Nullable String value) {
		if (normalizedQuery.isEmpty()) {
			return true;
		}
		return value != null
			&& !value.isBlank()
			&& value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
	}

	public static boolean matchesAnyQuery(String normalizedQuery, @Nullable String... values) {
		if (normalizedQuery.isEmpty()) {
			return true;
		}
		if (values == null) {
			return false;
		}
		for (String value : values) {
			if (textMatchesQuery(normalizedQuery, value)) {
				return true;
			}
		}
		return false;
	}

	public static boolean performanceObjectMatchesFilter(
		ToolPanelPresenter.StageObjectListItem item,
		@Nullable String query
	) {
		if (item == null) {
			return false;
		}
		String normalized = normalizeSearchQuery(query);
		return matchesAnyQuery(normalized, item.name(), item.id());
	}

	public static boolean layerMatchesFilter(BuildLayer layer, @Nullable String query) {
		if (layer == null) {
			return false;
		}
		String normalized = normalizeSearchQuery(query);
		if (normalized.isEmpty()) {
			return true;
		}
		if (matchesAnyQuery(
			normalized,
			layer.getName(),
			layer.getId(),
			layer.getStageObjectId(),
			layer.getBoundClipId()
		)) {
			return true;
		}
		if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) {
			return textMatchesQuery(normalized, BBTexts.get("beatblock.stage_explorer.badge.hidden"));
		}
		if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
			return textMatchesQuery(normalized, BBTexts.get("beatblock.stage_explorer.badge.bound"));
		}
		return false;
	}

	public static boolean groupNameMatchesFilter(BuildLayerGroup group, @Nullable String query) {
		if (group == null) {
			return false;
		}
		String normalized = normalizeSearchQuery(query);
		return matchesAnyQuery(normalized, group.getName(), group.getId());
	}

	public static int countFilteredLayers(BuildLayerManager manager, @Nullable String query) {
		if (manager == null) {
			return 0;
		}
		String normalized = normalizeSearchQuery(query);
		if (normalized.isEmpty()) {
			return manager.getAll().size();
		}
		int count = 0;
		for (BuildLayer layer : manager.getAll()) {
			if (layerMatchesFilter(layer, query)) {
				count++;
			}
		}
		return count;
	}

	public List<String> performanceObjectDisplayOrder() {
		return performanceObjects().stream().map(ToolPanelPresenter.StageObjectListItem::id).toList();
	}

	public boolean isPerformanceObjectSelected(String stageObjectId) {
		return layers.isStandaloneStageObjectSelected(stageObjectId);
	}

	public void selectPerformanceObject(String stageObjectId, boolean ctrl, boolean shift) {
		layers.selectStandaloneStageObject(stageObjectId, ctrl, shift, performanceObjectDisplayOrder());
	}

	public List<String> selectedAnimationTargetIds() {
		return layers.selectedStageObjectIds();
	}

	public int selectedAnimationTargetCount() {
		return selectedAnimationTargetIds().size();
	}

	public void clearAnimationTargetSelection() {
		layers.clearSelection();
	}

	public boolean isLayerOwnedStageObject(String stageObjectId) {
		return StageObjectLifecycleService.isOwnedByBuildLayer(layerManager.get(), stageObjectId);
	}

	/**
	 * 将对象设为当前动画目标（演出对象或 BuildLayer 内嵌 StageObject）。
	 */
	public PresenterResult enableBuildReveal(String stageObjectId) {
		var outcome = layers.enableBuildRevealForStageObject(stageObjectId, toolPanel.getStageObject(stageObjectId));
		return outcome.result();
	}

	public boolean canEnableBuildReveal(String stageObjectId) {
		if (stageObjectId == null || stageObjectId.isBlank()) {
			return false;
		}
		BuildLayerManager manager = layerManager.get();
		return manager != null && manager.findLayerOwningStageObject(stageObjectId) == null;
	}

	public void focusAnimationTarget(@Nullable String stageObjectId) {
		if (stageObjectId == null || stageObjectId.isBlank()) {
			return;
		}
		layers.clearSelection();
		BuildLayerManager manager = layerManager.get();
		if (manager != null && isLayerOwnedStageObject(stageObjectId)) {
			for (BuildLayer layer : manager.getAll()) {
				if (stageObjectId.equals(layer.getStageObjectId())) {
					layers.selectLayer(layer.getId(), false, false, layers.buildDisplayOrder());
					return;
				}
			}
		}
		selectPerformanceObject(stageObjectId, false, false);
	}
}
