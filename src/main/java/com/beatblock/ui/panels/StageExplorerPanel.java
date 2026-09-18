package com.beatblock.ui.panels;

import com.beatblock.timeline.StageObjectReferenceService;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.ImGuiModifierKeys;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.StageExplorerPresenter;
import com.beatblock.ui.presenter.ToolPanelPresenter;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 统一舞台对象浏览器：演出对象（standalone StageObject）+ 建造图层（BuildLayer）合一树形视图。
 */
public final class StageExplorerPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int SEARCH_CAPACITY = 128;
	private static final String STAGE_OBJECT_DELETE_POPUP = "##StageExplorerObjectDeleteConfirm";

	private final StageExplorerPresenter presenter;
	private final LayerPanel layerSection;
	private final ImString searchBuffer = new ImString(SEARCH_CAPACITY);
	private String pendingDeleteStageObjectId;
	private boolean requestDeleteStageObjectPopup;
	private String statusMessage = "";
	private String explorerStatusMessage = "";

	public StageExplorerPanel() {
		this(PresenterFactories.stageExplorerPresenter(), new LayerPanel());
	}

	public StageExplorerPanel(StageExplorerPresenter presenter, LayerPanel layerSection) {
		this.presenter = presenter;
		this.layerSection = layerSection;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.stageExplorerWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.stageExplorerWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderContent();
			renderStageObjectDeleteConfirmPopup();
			layerSection.renderOverlayModals();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.stageExplorerWindow());
		}
	}

	private void renderContent() {
		ImGui.text(BBTexts.get("beatblock.stage_explorer.title"));
		ImGui.separator();
		ImGui.textWrapped(BBTexts.get("beatblock.stage_explorer.hint"));
		renderSearchBar();
		renderSelectionFooter();
		ImGui.spacing();
		renderPerformanceObjectsSection();
		ImGui.spacing();
		layerSection.renderBuildLayersExplorerSection(searchBuffer.get());
		if (!explorerStatusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textWrapped(explorerStatusMessage);
		}
		if (!statusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textWrapped(statusMessage);
		}
	}

	private void renderSearchBar() {
		ImGui.spacing();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputTextWithHint("##StageExplorerSearch",
			BBTexts.get("beatblock.stage_explorer.search"), searchBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.stage_explorer.search.tooltip"));
		}
	}

	private void renderSelectionFooter() {
		int count = presenter.selectedAnimationTargetCount();
		if (count <= 0) {
			ImGui.textDisabled(BBTexts.get("beatblock.stage_explorer.no_selection"));
			return;
		}
		ImGui.textColored(0.45f, 0.85f, 1f, 1f,
			BBTexts.get("beatblock.stage_explorer.animation_targets", count));
		ImGui.sameLine();
		if (ImGui.smallButton(BBTexts.get("beatblock.stage_explorer.clear_selection") + "##clearTargets")) {
			presenter.clearAnimationTargetSelection();
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.stage_explorer.clear_selection.tooltip"));
		}
	}

	private void renderPerformanceObjectsSection() {
		var allObjects = presenter.performanceObjects();
		var objects = presenter.filteredPerformanceObjects(searchBuffer.get());
		String header = performanceObjectsHeader(allObjects.size(), objects.size());
		ImGui.setNextItemOpen(true, ImGuiCond.FirstUseEver);
		if (!ImGui.collapsingHeader(header + "##stageExplorerPerformance", ImGuiTreeNodeFlags.DefaultOpen)) {
			return;
		}

		if (allObjects.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.stage_explorer.no_performance_objects"));
			return;
		}
		if (objects.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.stage_explorer.no_filter_matches"));
			return;
		}

		float listHeight = Math.min(objects.size() * 28f + 8f, 240f);
		if (ImGui.beginChild("##StageExplorerPerformanceList", 0, listHeight, true)) {
			for (var obj : objects) {
				renderPerformanceObjectRow(obj);
			}
		}
		ImGui.endChild();
	}

	private static String performanceObjectsHeader(int total, int visible) {
		if (visible == total) {
			return BBTexts.get("beatblock.stage_explorer.performance_objects", total);
		}
		return BBTexts.get("beatblock.stage_explorer.performance_objects_filtered", visible, total);
	}

	private void renderPerformanceObjectRow(ToolPanelPresenter.StageObjectListItem obj) {
		ImGui.pushID(obj.id());
		boolean selected = presenter.isPerformanceObjectSelected(obj.id());
		if (selected) {
			ImGui.pushStyleColor(ImGuiCol.Header, 0.22f, 0.42f, 0.72f, 0.45f);
			ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.28f, 0.48f, 0.78f, 0.55f);
		}
		if (ImGui.selectable("• " + obj.name() + "##perfRow", selected, 0, -1f, 0f)) {
			presenter.selectPerformanceObject(obj.id(), ImGuiModifierKeys.ctrl(), ImGuiModifierKeys.shift());
		}
		if (selected) {
			ImGui.popStyleColor(2);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.stage_explorer.performance_row_tooltip"));
		}
		ImGui.textDisabled("  " + BBTexts.get(
			"beatblock.stage_explorer.object_meta_detail",
			obj.sourceTypeLabel(),
			obj.blockOrderLabel(),
			obj.blockCount(),
			obj.id()
		));
		if (presenter.canEnableBuildReveal(obj.id())) {
			if (ImGui.smallButton(BBTexts.get("beatblock.stage_explorer.enable_build_reveal") + "##enableBuild_" + obj.id())) {
				explorerStatusMessage = presenter.enableBuildReveal(obj.id()).messageOrEmpty();
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.stage_explorer.enable_build_reveal.tooltip"));
			}
			ImGui.sameLine();
		}
		if (ImGui.smallButton(BBTexts.get("beatblock.common.delete") + "##stageExplorerDel")) {
			pendingDeleteStageObjectId = obj.id();
			requestDeleteStageObjectPopup = true;
		}
		ImGui.popID();
	}

	private void renderStageObjectDeleteConfirmPopup() {
		if (requestDeleteStageObjectPopup && pendingDeleteStageObjectId != null) {
			ImGui.openPopup(STAGE_OBJECT_DELETE_POPUP);
			requestDeleteStageObjectPopup = false;
		}
		if (pendingDeleteStageObjectId == null) {
			return;
		}
		var toolPanel = presenter.toolPanel();
		var obj = toolPanel.getStageObject(pendingDeleteStageObjectId);
		var refs = toolPanel.findStageObjectReferences(pendingDeleteStageObjectId);

		ImGui.setNextWindowSize(420f, 0f, ImGuiCond.Appearing);
		if (!ImGui.beginPopupModal(STAGE_OBJECT_DELETE_POPUP)) {
			return;
		}

		ImGui.text(Icons.Action.WARNING + " " + BBTexts.get("beatblock.tool.delete_title"));
		ImGui.separator();

		if (obj == null) {
			ImGui.textWrapped(BBTexts.get("beatblock.tool.delete_gone"));
		} else {
			ImGui.textWrapped(BBTexts.get("beatblock.tool.delete_confirm", obj.getName()));
			if (!refs.isEmpty()) {
				ImGui.spacing();
				ImGui.textWrapped(BBTexts.get("beatblock.tool.delete_refs_warning", refs.count()));
				ImGui.spacing();
				renderReferenceCounts(refs);
			}
		}

		ImGui.spacing();
		if (!refs.isEmpty()) {
			if (ImGui.button(BBTexts.get("beatblock.tool.delete_clear_refs") + "##stageExplorerDeleteClearRefs", 220f, 0f)
				&& obj != null) {
				statusMessage = toolPanel.deleteStageObject(pendingDeleteStageObjectId, true).result().messageOrEmpty();
				pendingDeleteStageObjectId = null;
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##stageExplorerDeleteCancel", 120f, 0f)) {
				pendingDeleteStageObjectId = null;
				ImGui.closeCurrentPopup();
			}
		} else {
			if (ImGui.button(BBTexts.get("beatblock.tool.confirm_delete") + "##stageExplorerDeleteOk", 120f, 0f) && obj != null) {
				statusMessage = toolPanel.deleteStageObject(pendingDeleteStageObjectId, false).result().messageOrEmpty();
				pendingDeleteStageObjectId = null;
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##stageExplorerDeleteCancel", 120f, 0f)) {
				pendingDeleteStageObjectId = null;
				ImGui.closeCurrentPopup();
			}
		}

		ImGui.endPopup();
	}

	private void renderReferenceCounts(StageObjectReferenceService.ReferenceSummary refs) {
		StringBuilder lines = new StringBuilder();
		for (var entry : refs.countsByType().entrySet()) {
			if (!lines.isEmpty()) {
				lines.append('\n');
			}
			lines.append(referenceTypeLabel(entry.getKey())).append(": ").append(entry.getValue());
		}
		if (!lines.isEmpty()) {
			ImGui.textWrapped(lines.toString());
		}
	}

	private static String referenceTypeLabel(StageObjectReferenceService.ReferenceType type) {
		return switch (type) {
			case ANIMATION_EVENT -> BBTexts.get("beatblock.layer.reference.animation_event");
			case BINDING_RULE -> BBTexts.get("beatblock.layer.reference.binding_rule");
			case STAGE_ROLE -> BBTexts.get("beatblock.layer.reference.stage_role");
			case GRAMMAR_TARGET -> BBTexts.get("beatblock.layer.reference.grammar_target");
			case AUTOMAP_RULE -> BBTexts.get("beatblock.layer.reference.automap_rule");
			case AUTOMAP_FEATURE_TARGET -> BBTexts.get("beatblock.layer.reference.automap_feature_target");
			case CAMERA_PHRASE -> BBTexts.get("beatblock.layer.reference.camera_phrase");
			case VFX_TARGET -> BBTexts.get("beatblock.layer.reference.vfx_target");
			case CAMERA_SEGMENT -> BBTexts.get("beatblock.layer.reference.camera_segment");
			case AUTOMAP_SETTINGS -> BBTexts.get("beatblock.layer.reference.automap_settings");
		};
	}
}
