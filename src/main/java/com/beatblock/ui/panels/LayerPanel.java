package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 建造图层面板：列出 BuildLayer，支持从选区创建、显示/隐藏切换、删除与拖入时间线。
 */
public class LayerPanel {

	public static final String DRAG_PAYLOAD_TYPE = "BB_BUILD_LAYER_ID";

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int BUILD_REVERSE_COLOR = 0xFF_66_CC_88;

	private final ImString newLayerNameBuffer = new ImString("layer", 64);
	private String selectedLayerId;
	private String statusMessage = "";

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderContent();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW);
		}
	}

	private void renderContent() {
		ImGui.text("建造图层");
		ImGui.separator();
		ImGui.textWrapped("从选区创建图层 → 隐藏捕获快照 → 拖入「建造还原」轨道绑定片段播放。");

		var selMgr = BeatBlockSelectionManager.get();
		int selCount = selMgr.getSelectionCount();
		ImGui.textDisabled(String.format(Locale.ROOT, "当前选区：%d 个方块", selCount));

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("图层名称##layerName", newLayerNameBuffer);

		if (selCount <= 0) ImGui.beginDisabled();
		if (ImGui.button("从选区新建图层##layerCreate", -1f, 0f)) {
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
		if (BeatBlock.blockAnimationEngine == null) {
			ImGui.textDisabled("动画引擎未就绪。");
			return;
		}
		var manager = BeatBlock.blockAnimationEngine.getBuildLayerManager();
		List<BuildLayer> layers = new ArrayList<>(manager.getAll());
		if (layers.isEmpty()) {
			ImGui.textDisabled("暂无图层。");
			return;
		}

		for (BuildLayer layer : layers) {
			ImGui.pushID(layer.getId());
			boolean selected = layer.getId().equals(selectedLayerId);
			if (ImGui.selectable(layerLabel(layer), selected)) {
				selectedLayerId = layer.getId();
			}

			if (ImGui.beginDragDropSource()) {
				if (layer.canBindToTrack()) {
					ImGui.text("绑定到建造还原轨道");
					ImGui.setDragDropPayload(DRAG_PAYLOAD_TYPE, layer.getId().getBytes(), ImGuiCond.Once);
				} else {
					ImGui.textDisabled(stateHint(layer));
				}
				ImGui.endDragDropSource();
			}

			ImGui.sameLine();
			renderVisibilityButton(layer);
			ImGui.sameLine();
			renderDeleteButton(layer);

			if (selected) {
				ImGui.indent();
				ImGui.textDisabled(String.format(Locale.ROOT, "方块数：%d", layer.getStageObject().getBlocks().size()));
				if (layer.getBoundClipId() != null) {
					ImGui.textDisabled("绑定片段：" + layer.getBoundClipId());
				}
				ImGui.unindent();
			}

			ImGui.popID();
		}
	}

	private void renderVisibilityButton(BuildLayer layer) {
		boolean canToggle = layer.canToggleVisibility();
		if (!canToggle) ImGui.beginDisabled();
		String label = layer.getState() == LayerVisibilityState.FREE_VISIBLE ? "隐藏" : "显示";
		if (ImGui.smallButton(label + "##layerVis_" + layer.getId())) {
			toggleVisibility(layer);
		}
		if (!canToggle) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(canToggle ? "切换 FREE_VISIBLE ↔ FREE_HIDDEN" : "已绑定轨道，不可手动切换");
		}
	}

	private void renderDeleteButton(BuildLayer layer) {
		boolean canDelete = layer.canDelete();
		if (!canDelete) ImGui.beginDisabled();
		ImGui.pushStyleColor(ImGuiCol.Button, 0.45f, 0.15f, 0.15f, 1f);
		if (ImGui.smallButton("删除##layerDel_" + layer.getId())) {
			deleteLayer(layer);
		}
		ImGui.popStyleColor();
		if (!canDelete) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(canDelete ? "释放图层（隐藏状态下会先恢复方块）" : "已绑定轨道，不可删除");
		}
	}

	private void createLayerFromSelection() {
		if (BeatBlock.blockAnimationEngine == null || BeatBlock.timelineEditor == null) {
			statusMessage = "引擎或时间线编辑器不可用。";
			return;
		}
		var selMgr = BeatBlockSelectionManager.get();
		List<BlockPos> blocks = new ArrayList<>(selMgr.getSelectedBlocks());
		if (blocks.isEmpty()) {
			statusMessage = "请先建立方块选区。";
			return;
		}
		String name = newLayerNameBuffer.get() != null ? newLayerNameBuffer.get().trim() : "";
		var cmd = new CreateLayerCommand(
			BeatBlock.blockAnimationEngine.getBuildLayerManager(),
			name.isEmpty() ? "layer" : name,
			blocks
		);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		if (cmd.getCreatedLayer() != null) {
			selectedLayerId = cmd.getCreatedLayer().getId();
			statusMessage = "已创建图层：" + cmd.getCreatedLayer().getName();
		}
	}

	private void toggleVisibility(BuildLayer layer) {
		if (BeatBlock.timelineEditor == null || BeatBlock.blockAnimationEngine == null) return;
		var cmd = new ToggleLayerVisibilityCommand(
			BeatBlock.blockAnimationEngine.getBuildLayerManager(),
			layer.getId()
		);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		statusMessage = layer.getState() == LayerVisibilityState.FREE_VISIBLE ? "已隐藏图层" : "已显示图层";
	}

	private void deleteLayer(BuildLayer layer) {
		if (BeatBlock.timelineEditor == null || BeatBlock.blockAnimationEngine == null) return;
		var cmd = new DeleteLayerCommand(
			BeatBlock.blockAnimationEngine.getBuildLayerManager(),
			layer.getId()
		);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		if (layer.getId().equals(selectedLayerId)) selectedLayerId = null;
		statusMessage = "已删除图层：" + layer.getName();
	}

	private static String layerLabel(BuildLayer layer) {
		return String.format(Locale.ROOT, "%s  [%s]", layer.getName(), stateLabel(layer.getState()));
	}

	private static String stateLabel(LayerVisibilityState state) {
		return switch (state) {
			case FREE_VISIBLE -> "可见";
			case FREE_HIDDEN -> "已隐藏";
			case BOUND_TO_TRACK -> "已绑定";
		};
	}

	private static String stateHint(BuildLayer layer) {
		return switch (layer.getState()) {
			case FREE_VISIBLE -> "请先隐藏图层再拖入轨道";
			case BOUND_TO_TRACK -> "已绑定到轨道";
			default -> "";
		};
	}
}
