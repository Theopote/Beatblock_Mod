package com.beatblock.ui.panels;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerColorUtils;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.BuildLayersPresenter;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.imgui.ImGuiModifierKeys;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.layer.BuildLayerDragDropHandler;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

/**
 * 建造图层面板：列出 BuildLayer，支持重命名、可见性、分组、合并、颜色标记与拖入时间线。
 */
public class LayerPanel {

	public static final String DRAG_PAYLOAD_TYPE = BuildLayerDragDropHandler.PAYLOAD_TYPE;
	public static final String REORDER_DRAG_PAYLOAD_TYPE = "BB_BUILD_LAYER_REORDER";
	private static final String CONTEXT_POPUP = "##LayerRowContext";
	private static final String DELETE_CONFIRM_POPUP = "##LayerDeleteConfirm";
	private static final String COLOR_POPUP = "##LayerColorPopup";
	private static final float ICON_BTN = 22f;

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final ImString newLayerNameBuffer = new ImString("layer", 64);
	private final ImString groupNameBuffer = new ImString("group", 64);
	private final Map<String, ImString> nameEditBuffers = new HashMap<>();
	private final Map<String, String> nameCommitted = new HashMap<>();
	private final float[] activeColorRgb = new float[3];

	private List<String> displayOrder = List.of();
	private String renamingLayerId;
	private String renamingGroupId;
	private String colorTargetLayerId;
	private String colorTargetGroupId;
	private String pendingDeleteLayerId;
	private boolean requestDeleteConfirmPopup;
	private boolean requestColorPopup;
	private String statusMessage = "";
	private final BuildLayersPresenter presenter;

	public LayerPanel() {
		this(com.beatblock.ui.presenter.PresenterFactories.buildLayersPresenter());
	}

	LayerPanel(BuildLayersPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.layerPanelWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.layerPanelWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderContent();
			renderDeleteConfirmPopup();
			renderColorPopup();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.layerPanelWindow());
		}
	}

	private void renderContent() {
		ImGui.text(BBTexts.get("beatblock.layer.title"));
		ImGui.separator();
		ImGui.textWrapped(BBTexts.get("beatblock.layer.hint"));

		var selMgr = BeatBlockSelectionManager.get();
		int selCount = selMgr.getSelectionCount();
		ImGui.textDisabled(BBTexts.get("beatblock.layer.current_selection", selCount));

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.layer.name") + "##layerName", newLayerNameBuffer);

		if (selCount <= 0) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.layer.create_from_selection") + "##layerCreate", -1f, 0f)) {
			createLayerFromSelection();
		}
		if (selCount <= 0) ImGui.endDisabled();

		ImGui.separator();
		renderLayerToolbar();
		ImGui.separator();
		renderLayerList();

		if (!statusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textWrapped(statusMessage);
		}
	}

	private void renderLayerToolbar() {
		int selectedCount = presenter.selectedLayerIds().size();
		if (selectedCount < 2) ImGui.beginDisabled();
		ImGui.setNextItemWidth(120f);
		ImGui.inputText(BBTexts.get("beatblock.layer.group_name") + "##groupName", groupNameBuffer);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.layer.group") + "##layerGroup")) {
			var outcome = presenter.groupSelectedLayers(groupNameBuffer.get());
			statusMessage = outcome.result().messageOrEmpty();
		}
		if (selectedCount < 2) ImGui.endDisabled();

		if (selectedCount < 1) ImGui.beginDisabled();
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.layer.ungroup") + "##layerUngroup")) {
			var outcome = presenter.ungroupSelectedLayers();
			statusMessage = outcome.result().messageOrEmpty();
		}
		if (selectedCount < 1) ImGui.endDisabled();

		if (selectedCount < 2) ImGui.beginDisabled();
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.layer.merge") + "##layerMerge")) {
			var outcome = presenter.mergeSelectedLayers("");
			statusMessage = outcome.result().messageOrEmpty();
		}
		if (selectedCount < 2) ImGui.endDisabled();

		ImGui.textDisabled(BBTexts.get("beatblock.layer.selected_count", selectedCount));
	}

	private void renderLayerList() {
		var manager = presenter.currentLayerManager();
		if (manager == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.layer.engine_not_ready"));
			return;
		}
		List<BuildLayer> layers = new ArrayList<>(manager.getAll());
		pruneNameBuffers(layers, manager);
		displayOrder = presenter.buildDisplayOrder();

		if (layers.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.layer.no_layers"));
			return;
		}

		Set<String> rendered = new HashSet<>();
		for (BuildLayerGroup group : manager.getAllGroups()) {
			renderGroupNode(group, manager, rendered);
		}
		for (BuildLayer layer : manager.getUngroupedLayers()) {
			renderLayerRow(layer, manager, 0);
			rendered.add(layer.getId());
		}
		for (BuildLayer layer : layers) {
			if (!rendered.contains(layer.getId())) {
				renderLayerRow(layer, manager, 0);
			}
		}
	}

	private void renderGroupNode(BuildLayerGroup group, BuildLayerManager manager, Set<String> rendered) {
		ImGui.pushID("group_" + group.getId());
		renderColorButton(group.getColorArgb(), null, group.getId());
		ImGui.sameLine();
		String label = group.getName();
		if (group.getColorArgb() != 0) {
			LayerColorUtils.pushTextColor(group.getColorArgb());
		}
		boolean open = ImGui.treeNodeEx(
			label + "##groupTree_" + group.getId(),
			ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.DefaultOpen
		);
		if (group.getColorArgb() != 0) {
			LayerColorUtils.popTextColor(group.getColorArgb());
		}
		if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
			renamingGroupId = group.getId();
		}
		if (group.getId().equals(renamingGroupId)) {
			ImGui.setNextItemWidth(-1f);
			ImString buf = nameBufferFor("group:" + group.getId(), group.getName());
			if (ImGui.inputText("##groupRename", buf, ImGuiInputTextFlags.EnterReturnsTrue)) {
				commitGroupRename(group, buf.get());
				renamingGroupId = null;
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				commitGroupRename(group, buf.get());
				renamingGroupId = null;
			}
		}
		if (open) {
			for (BuildLayer layer : manager.getLayersInGroup(group.getId())) {
				renderLayerRow(layer, manager, 1);
				rendered.add(layer.getId());
			}
			ImGui.treePop();
		}
		ImGui.popID();
	}

	private void renderLayerRow(BuildLayer layer, BuildLayerManager manager, int indentLevel) {
		ImGui.pushID(layer.getId());
		if (indentLevel > 0) {
			ImGui.indent(indentLevel * 12f);
		}

		boolean selected = presenter.isLayerSelected(layer.getId());
		int iconCount = 4 + (layer.canBindToTrack() ? 1 : 0);
		float iconSpacing = ImGui.getStyle().getItemSpacingX();
		float reservedIcons = iconCount * (ICON_BTN + iconSpacing);

		IconButtonStyle.pushBeatBlockIconButton();
		ImGui.button(Icons.Action.DRAG_HANDLE + "##layerReorder_" + layer.getId(), ICON_BTN, ICON_BTN);
		if (ImGui.isItemHovered()) {
			IconButtonStyle.setTooltipWithDefaultFont(BBTexts.get("beatblock.layer.reorder_tooltip"));
		}
		renderLayerReorderDragSource(layer);
		IconButtonStyle.popBeatBlockIconButton();
		ImGui.sameLine();

		renderColorButton(layer.getColorArgb(), layer.getId(), null);
		ImGui.sameLine();

		IconButtonStyle.pushBeatBlockIconButton();
		String visTooltip = renderVisibilityIconButton(layer);
		if (visTooltip != null) {
			IconButtonStyle.setTooltipWithDefaultFont(visTooltip);
		}
		IconButtonStyle.popBeatBlockIconButton();
		ImGui.sameLine();

		float nameWidth = Math.max(80f, ImGui.getContentRegionAvail().x - reservedIcons - 8f);
		renderLayerName(layer, manager, nameWidth, selected);
		ImGui.sameLine();

		if (layer.canBindToTrack()) {
			IconButtonStyle.pushBeatBlockIconButton();
			ImGui.button(Icons.Action.LINK + "##layerBindDrag_" + layer.getId(), ICON_BTN, ICON_BTN);
			if (ImGui.isItemHovered()) {
				IconButtonStyle.setTooltipWithDefaultFont(BBTexts.get("beatblock.layer.drag_bind"));
			}
			renderTimelineBindDragSource(layer);
			IconButtonStyle.popBeatBlockIconButton();
			ImGui.sameLine();
		}

		IconButtonStyle.pushBeatBlockIconButton();
		boolean canDelete = layer.canDelete();
		if (!canDelete) ImGui.beginDisabled();
		if (ImGui.button(Icons.Action.REMOVE + "##layerDelete_" + layer.getId(), ICON_BTN, ICON_BTN)) {
			pendingDeleteLayerId = layer.getId();
			requestDeleteConfirmPopup = true;
		}
		if (!canDelete) {
			ImGui.endDisabled();
			if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
				IconButtonStyle.setTooltipWithDefaultFont(BBTexts.get("beatblock.layer.cannot_delete_bound"));
			}
		}
		IconButtonStyle.popBeatBlockIconButton();

		renderLayerContextMenu(layer);

		if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
			ImGui.sameLine();
			ImGui.textDisabled(BBTexts.get("beatblock.layer.bound"));
		}

		if (selected) {
			ImGui.indent();
			ImGui.textDisabled(BBTexts.get("beatblock.layer.block_count", layer.getStageObject().getBlocks().size()));
			if (layer.getBoundClipId() != null) {
				ImGui.textDisabled(BBTexts.get("beatblock.layer.bound_clip", layer.getBoundClipId()));
			}
			ImGui.textDisabled(BBTexts.get("beatblock.layer.drag_hint"));
			ImGui.unindent();
		}

		if (indentLevel > 0) {
			ImGui.unindent(indentLevel * 12f);
		}
		ImGui.popID();
	}

	private void renderLayerName(BuildLayer layer, BuildLayerManager manager, float nameWidth, boolean selected) {
		if (layer.getId().equals(renamingLayerId)) {
			ImGui.setNextItemWidth(nameWidth);
			ImString nameBuf = nameBufferFor(layer.getId(), layer.getName());
			if (ImGui.inputText("##layerNameEdit", nameBuf, ImGuiInputTextFlags.EnterReturnsTrue)) {
				commitLayerRename(layer, manager, nameBuf.get());
				renamingLayerId = null;
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				commitLayerRename(layer, manager, nameBuf.get());
				renamingLayerId = null;
			}
			return;
		}

		if (selected) {
			ImGui.pushStyleColor(ImGuiCol.Header, 0.22f, 0.42f, 0.72f, 0.45f);
			ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.28f, 0.48f, 0.78f, 0.55f);
		}
		ImGui.setNextItemWidth(nameWidth);
		if (layer.getColorArgb() != 0) {
			LayerColorUtils.pushTextColor(layer.getColorArgb());
		}
		if (ImGui.selectable(layer.getName() + "##layerRow", selected, 0, nameWidth, ICON_BTN)) {
			presenter.selectLayer(
				layer.getId(),
				ImGuiModifierKeys.ctrl(),
				ImGuiModifierKeys.shift(),
				displayOrder
			);
		}
		float bindDragX = ImGui.getItemRectMinX();
		float bindDragY = ImGui.getItemRectMinY();
		float bindDragH = Math.max(ICON_BTN, ImGui.getItemRectSizeY());
		renderLayerReorderDropTarget(layer.getId());
		if (layer.canBindToTrack()) {
			ImGui.setCursorScreenPos(bindDragX, bindDragY);
			ImGui.invisibleButton("##layerBindDragName_" + layer.getId(), nameWidth, bindDragH);
			renderTimelineBindDragSource(layer);
		}
		if (layer.getColorArgb() != 0) {
			LayerColorUtils.popTextColor(layer.getColorArgb());
		}
		if (selected) {
			ImGui.popStyleColor(2);
		}
		if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
			renamingLayerId = layer.getId();
		}
	}

	private void renderColorButton(int colorArgb, String layerId, String groupId) {
		float[] rgb = LayerColorUtils.toFloatRgb(colorArgb);
		ImGui.pushStyleColor(ImGuiCol.Button, rgb[0], rgb[1], rgb[2], 1f);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, rgb[0] * 1.1f, rgb[1] * 1.1f, rgb[2] * 1.1f, 1f);
		if (ImGui.button("##layerColor", ICON_BTN, ICON_BTN)) {
			openColorPicker(layerId, groupId, colorArgb);
		}
		ImGui.popStyleColor(2);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.layer.color_tooltip"));
		}
	}

	private void openColorPicker(String layerId, String groupId, int colorArgb) {
		colorTargetLayerId = layerId;
		colorTargetGroupId = groupId;
		float[] rgb = LayerColorUtils.toFloatRgb(colorArgb);
		activeColorRgb[0] = rgb[0];
		activeColorRgb[1] = rgb[1];
		activeColorRgb[2] = rgb[2];
		requestColorPopup = true;
	}

	private void renderColorPopup() {
		if (requestColorPopup) {
			ImGui.openPopup(COLOR_POPUP);
			requestColorPopup = false;
		}
		ImGui.setNextWindowSize(320f, 0f, ImGuiCond.Appearing);
		if (!ImGui.beginPopupModal(COLOR_POPUP)) {
			return;
		}
		if (ImGui.colorEdit3(BBTexts.get("beatblock.layer.color_label") + "##layerColorEdit", activeColorRgb)) {
			int argb = LayerColorUtils.fromFloatRgb(activeColorRgb[0], activeColorRgb[1], activeColorRgb[2]);
			if (colorTargetLayerId != null) {
				statusMessage = presenter.setLayerColor(colorTargetLayerId, argb).messageOrEmpty();
			} else if (colorTargetGroupId != null) {
				statusMessage = presenter.setGroupColor(colorTargetGroupId, argb).messageOrEmpty();
			}
		}
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.close") + "##layerColorClose", 120f, 0f)) {
			colorTargetLayerId = null;
			colorTargetGroupId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private void renderLayerContextMenu(BuildLayer layer) {
		if (!ImGui.beginPopupContextItem(CONTEXT_POPUP)) {
			return;
		}
		if (ImGui.menuItem(BBTexts.get("beatblock.layer.rename"))) {
			renamingLayerId = layer.getId();
		}
		boolean canDelete = layer.canDelete();
		if (!canDelete) ImGui.beginDisabled();
		if (ImGui.menuItem(BBTexts.get("beatblock.layer.delete"))) {
			pendingDeleteLayerId = layer.getId();
			requestDeleteConfirmPopup = true;
		}
		if (!canDelete) {
			ImGui.endDisabled();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.layer.cannot_delete_bound"));
			}
		}
		ImGui.endPopup();
	}

	private void renderLayerReorderDragSource(BuildLayer layer) {
		if (!ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
			return;
		}
		ImGui.text(BBTexts.get("beatblock.layer.reorder_drag", layer.getName()));
		ImGui.setDragDropPayload(REORDER_DRAG_PAYLOAD_TYPE, layer.getId().getBytes(StandardCharsets.UTF_8), ImGuiCond.Once);
		ImGui.endDragDropSource();
	}

	private void renderLayerReorderDropTarget(String layerId) {
		if (!ImGui.beginDragDropTarget()) {
			return;
		}
		byte[] raw = ImGui.acceptDragDropPayload(REORDER_DRAG_PAYLOAD_TYPE);
		if (raw != null) {
			String movingId = decodePayload(raw);
			if (!movingId.isBlank() && !movingId.equals(layerId)) {
				statusMessage = presenter.reorderLayerBefore(movingId, layerId).messageOrEmpty();
			}
		}
		ImGui.endDragDropTarget();
	}

	private void renderTimelineBindDragSource(BuildLayer layer) {
		if (!layer.canBindToTrack()) {
			return;
		}
		if (!ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
			return;
		}
		ImGui.text(BBTexts.get("beatblock.layer.drag_bind") + ": " + layer.getName());
		ImGui.setDragDropPayload(DRAG_PAYLOAD_TYPE, layer.getId().getBytes(StandardCharsets.UTF_8), ImGuiCond.Once);
		ImGui.endDragDropSource();
	}

	private static String decodePayload(byte[] raw) {
		return BuildLayerDragDropHandler.decodeLayerId(raw);
	}

	private String renderVisibilityIconButton(BuildLayer layer) {
		boolean canToggle = layer.canToggleVisibility();
		boolean visible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		String icon = visible ? Icons.EYE : Icons.Action.HIDDEN;

		if (!canToggle) {
			ImGui.beginDisabled();
			icon = Icons.Action.LOCK;
		} else if (!visible) {
			ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.45f, 0.45f, 1f);
		}

		if (ImGui.button(icon + "##layerVis_" + layer.getId(), ICON_BTN, ICON_BTN)) {
			toggleVisibility(layer);
		}

		if (!canToggle) {
			ImGui.endDisabled();
		} else if (!visible) {
			ImGui.popStyleColor();
		}

		String tooltip = null;
		if (ImGui.isItemHovered()) {
			if (!canToggle) {
				tooltip = BBTexts.get("beatblock.layer.tooltip.bound_visibility");
			} else if (visible) {
				tooltip = BBTexts.get("beatblock.layer.tooltip.hide");
			} else {
				tooltip = BBTexts.get("beatblock.layer.tooltip.show");
			}
		}
		return tooltip;
	}

	private void renderDeleteConfirmPopup() {
		if (requestDeleteConfirmPopup && pendingDeleteLayerId != null) {
			ImGui.openPopup(DELETE_CONFIRM_POPUP);
			requestDeleteConfirmPopup = false;
		}
		if (pendingDeleteLayerId == null) {
			return;
		}
		BuildLayer layer = presenter.findLayer(pendingDeleteLayerId);

		ImGui.setNextWindowSize(360f, 0f, ImGuiCond.Appearing);
		if (!ImGui.beginPopupModal(DELETE_CONFIRM_POPUP)) {
			return;
		}

		ImGui.text(Icons.Action.WARNING + " " + BBTexts.get("beatblock.layer.delete_title"));
		ImGui.separator();

		if (layer == null) {
			ImGui.textWrapped(BBTexts.get("beatblock.layer.delete_gone"));
		} else {
			ImGui.textWrapped(BBTexts.get("beatblock.layer.delete_confirm", layer.getName()));
			if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) {
				ImGui.spacing();
				ImGui.textWrapped(BBTexts.get("beatblock.layer.delete_hidden_hint"));
			}
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.layer.confirm_delete") + "##layerDeleteOk", 120f, 0f) && layer != null) {
			deleteLayer(layer);
			pendingDeleteLayerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##layerDeleteCancel", 120f, 0f)) {
			pendingDeleteLayerId = null;
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}

	private ImString nameBufferFor(String key, String currentName) {
		ImString buf = nameEditBuffers.computeIfAbsent(key, id -> new ImString(currentName, 64));
		String committed = nameCommitted.get(key);
		if (!currentName.equals(committed)) {
			buf.set(currentName);
			nameCommitted.put(key, currentName);
		}
		return buf;
	}

	private void commitLayerRename(BuildLayer layer, BuildLayerManager manager, String rawName) {
		if (layer == null || manager == null) {
			return;
		}
		var outcome = presenter.renameLayer(layer.getId(), rawName);
		if (outcome.committedName() != null) {
			nameBufferFor(layer.getId(), layer.getName()).set(outcome.committedName());
			nameCommitted.put(layer.getId(), outcome.committedName());
		}
		statusMessage = outcome.result().messageOrEmpty();
	}

	private void commitGroupRename(BuildLayerGroup group, String rawName) {
		if (group == null) {
			return;
		}
		var outcome = presenter.renameGroup(group.getId(), rawName);
		if (outcome.committedName() != null) {
			nameBufferFor("group:" + group.getId(), group.getName()).set(outcome.committedName());
			nameCommitted.put("group:" + group.getId(), outcome.committedName());
		}
		statusMessage = outcome.result().messageOrEmpty();
	}

	private void createLayerFromSelection() {
		var selMgr = BeatBlockSelectionManager.get();
		var outcome = presenter.createLayerFromSelection(
			newLayerNameBuffer.get(),
			new ArrayList<>(selMgr.getSelectedBlocks())
		);
		statusMessage = outcome.result().messageOrEmpty();
		if (outcome.createdLayerId() != null) {
			BuildLayer created = presenter.findLayer(outcome.createdLayerId());
			if (created != null) {
				nameCommitted.put(created.getId(), created.getName());
			}
			if (!outcome.blocksToRemoveFromSelection().isEmpty()) {
				selMgr.removeBlocks(outcome.blocksToRemoveFromSelection());
			}
		}
	}

	private void toggleVisibility(BuildLayer layer) {
		if (layer == null) {
			return;
		}
		var outcome = presenter.toggleVisibility(layer.getId());
		statusMessage = outcome.result().messageOrEmpty();
	}

	private void deleteLayer(BuildLayer layer) {
		if (layer == null) {
			return;
		}
		var outcome = presenter.deleteLayer(layer.getId());
		statusMessage = outcome.result().messageOrEmpty();
		if (outcome.result().ok()) {
			nameEditBuffers.remove(layer.getId());
			nameCommitted.remove(layer.getId());
		}
	}

	private void pruneNameBuffers(List<BuildLayer> layers, BuildLayerManager manager) {
		Set<String> alive = new HashSet<>();
		for (BuildLayer layer : layers) {
			alive.add(layer.getId());
		}
		for (BuildLayerGroup group : manager.getAllGroups()) {
			alive.add("group:" + group.getId());
		}
		nameEditBuffers.keySet().removeIf(id -> !alive.contains(id) && !id.startsWith("group:"));
		nameCommitted.keySet().removeIf(id -> !alive.contains(id) && !id.startsWith("group:"));
	}

}
