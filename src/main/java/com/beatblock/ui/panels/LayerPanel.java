package com.beatblock.ui.panels;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.BuildLayersPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 建造图层面板：列出 BuildLayer，支持重命名、图标切换可见性、右键删除确认与拖入时间线。
 */
public class LayerPanel {

	public static final String DRAG_PAYLOAD_TYPE = "BB_BUILD_LAYER_ID";
	private static final String CONTEXT_POPUP = "##LayerRowContext";
	private static final String DELETE_CONFIRM_POPUP = "##LayerDeleteConfirm";
	private static final float ICON_BTN = 22f;

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final ImString newLayerNameBuffer = new ImString("layer", 64);
	private final Map<String, ImString> nameEditBuffers = new HashMap<>();
	private final Map<String, String> nameCommitted = new HashMap<>();

	private String selectedLayerId;
	private String pendingDeleteLayerId;
	private String statusMessage = "";
	private final BuildLayersPresenter presenter;

	public LayerPanel() {
		this(PresenterFactories.buildLayersPresenter());
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
		renderLayerList();

		if (!statusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textWrapped(statusMessage);
		}
	}

	private void renderLayerList() {
		var manager = presenter.currentLayerManager();
		if (manager == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.layer.engine_not_ready"));
			return;
		}
		List<BuildLayer> layers = new ArrayList<>(manager.getAll());
		pruneNameBuffers(layers);

		if (layers.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.layer.no_layers"));
			return;
		}

		for (BuildLayer layer : layers) {
			renderLayerRow(layer, manager);
		}
	}

	private void renderLayerRow(BuildLayer layer, BuildLayerManager manager) {
		ImGui.pushID(layer.getId());
		boolean selected = layer.getId().equals(selectedLayerId);

		IconButtonStyle.pushBeatBlockIconButton();
		String visTooltip = renderVisibilityIconButton(layer);
		IconButtonStyle.popBeatBlockIconButton();
		if (visTooltip != null) {
			ImGui.setTooltip(visTooltip);
		}

		ImGui.sameLine();
		float nameWidth = Math.max(80f, ImGui.getContentRegionAvail().x - 8f);
		ImGui.setNextItemWidth(nameWidth);
		ImString nameBuf = nameBufferFor(layer);
		int flags = ImGuiInputTextFlags.EnterReturnsTrue;
		if (ImGui.inputText("##layerNameEdit", nameBuf, flags)) {
			commitLayerRename(layer, manager, nameBuf.get());
		}
		if (ImGui.isItemDeactivatedAfterEdit()) {
			commitLayerRename(layer, manager, nameBuf.get());
		}
		if (ImGui.isItemClicked()) {
			selectedLayerId = layer.getId();
		}
		boolean requestDeleteConfirm = false;
		if (ImGui.beginPopupContextItem(CONTEXT_POPUP)) {
			boolean canDelete = layer.canDelete();
			if (!canDelete) ImGui.beginDisabled();
			if (ImGui.menuItem(BBTexts.get("beatblock.layer.delete"))) {
				pendingDeleteLayerId = layer.getId();
				requestDeleteConfirm = true;
				ImGui.closeCurrentPopup();
			}
			if (!canDelete) {
				ImGui.endDisabled();
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.layer.cannot_delete_bound"));
				}
			}
			ImGui.endPopup();
		}
		if (requestDeleteConfirm) {
			ImGui.openPopup(DELETE_CONFIRM_POPUP);
		}

		if (ImGui.beginDragDropSource()) {
			if (layer.canBindToTrack()) {
				ImGui.text(BBTexts.get("beatblock.layer.drag_bind"));
				ImGui.setDragDropPayload(DRAG_PAYLOAD_TYPE, layer.getId().getBytes(), ImGuiCond.Once);
			} else {
				ImGui.textDisabled(stateHint(layer));
			}
			ImGui.endDragDropSource();
		}

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
			ImGui.unindent();
		}

		ImGui.popID();
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
		if (pendingDeleteLayerId == null) return;
		BuildLayer layer = presenter.findLayer(pendingDeleteLayerId);

		ImGui.setNextWindowSize(360f, 0f);
		if (!ImGui.beginPopupModal(DELETE_CONFIRM_POPUP, ImGuiWindowFlags.AlwaysAutoResize)) {
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

	private ImString nameBufferFor(BuildLayer layer) {
		ImString buf = nameEditBuffers.computeIfAbsent(layer.getId(), id -> new ImString(layer.getName(), 64));
		String committed = nameCommitted.get(layer.getId());
		if (!layer.getName().equals(committed)) {
			buf.set(layer.getName());
			nameCommitted.put(layer.getId(), layer.getName());
		}
		return buf;
	}

	private void commitLayerRename(BuildLayer layer, BuildLayerManager manager, String rawName) {
		if (layer == null || manager == null) {
			return;
		}
		var outcome = presenter.renameLayer(layer.getId(), rawName);
		if (outcome.committedName() != null) {
			nameBufferFor(layer).set(outcome.committedName());
			nameCommitted.put(layer.getId(), outcome.committedName());
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
			selectedLayerId = outcome.createdLayerId();
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
			if (layer.getId().equals(selectedLayerId)) {
				selectedLayerId = null;
			}
		}
	}

	private void pruneNameBuffers(List<BuildLayer> layers) {
		Set<String> alive = new HashSet<>();
		for (BuildLayer layer : layers) {
			alive.add(layer.getId());
		}
		nameEditBuffers.keySet().removeIf(id -> !alive.contains(id));
		nameCommitted.keySet().removeIf(id -> !alive.contains(id));
	}

	private static String stateHint(BuildLayer layer) {
		return switch (layer.getState()) {
			case FREE_VISIBLE -> BBTexts.get("beatblock.layer.hide_first");
			case BOUND_TO_TRACK -> BBTexts.get("beatblock.layer.already_bound");
			default -> "";
		};
	}
}
