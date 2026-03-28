package com.beatblock.ui.panels;

import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import java.util.Locale;

/**
 * 选择工具属性：操作模式、空气、上限、统计与清空（对应 ChronoBlocks 属性面板中的工具上下文，精简版）。
 */
public class SelectionPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final int[] maxBlocksScratch = new int[1];
	private final int[] sphereRadiusScratch = new int[1];
	private final ImBoolean includeAirProxy = new ImBoolean(false);
	private final ImBoolean connectedFullStateProxy = new ImBoolean(true);

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		var mgr = BeatBlockSelectionManager.get();
		ImGui.text("方块选择");
		ImGui.separator();

		ImGui.textDisabled("操作模式");
		for (SelectionOperation op : SelectionOperation.values()) {
			if (ImGui.radioButton(operationLabel(op) + "##selOp" + op.name(), mgr.getOperation() == op)) {
				mgr.setOperation(op);
			}
		}
		ImGui.textWrapped("说明：新建=替换选区。点击 / 球 / 连通 / 整列 下按住 Shift 可强制加选（与点击工具一致）。");

		sphereRadiusScratch[0] = mgr.getSphereBrushRadius();
		ImGui.setNextItemWidth(160f);
		if (ImGui.sliderInt("球选半径（格）##selSphereR", sphereRadiusScratch, 1, 32)) {
			mgr.setSphereBrushRadius(sphereRadiusScratch[0]);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("欧氏距离 ≤ 半径的方块计入球选（预览为包络盒）。");
		}

		connectedFullStateProxy.set(mgr.isConnectedMatchFullState());
		ImGui.checkbox("魔棒：完整方块状态一致##selConnFull", connectedFullStateProxy);
		mgr.setConnectedMatchFullState(connectedFullStateProxy.get());
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("勾选：与起点 BlockState 完全相同才算同色；关闭：仅方块类型一致。");
		}

		includeAirProxy.set(mgr.isIncludeAir());
		ImGui.checkbox("包含空气方块##selIncludeAir", includeAirProxy);
		mgr.setIncludeAir(includeAirProxy.get());

		maxBlocksScratch[0] = mgr.getMaxBlocks();
		ImGui.setNextItemWidth(180f);
		if (ImGui.sliderInt("框选方块上限##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
			mgr.setMaxBlocks(maxBlocksScratch[0]);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("框选体积、线选经过格数、球内方块数、连通展开数等超过此值时拒绝或截断，避免卡死。");
		}

		ImGui.separator();
		ImGui.textDisabled(String.format(Locale.ROOT, "已选方块: %d", mgr.getSelectionCount()));
		var min = mgr.getBoundingMin();
		var max = mgr.getBoundingMax();
		if (min != null && max != null) {
			ImGui.textDisabled(String.format(Locale.ROOT,
				"包围盒: [%d,%d,%d] — [%d,%d,%d]",
				min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
		}

		if (mgr.getMode() == SelectionMode.BOX && mgr.getBoxFirstCorner() != null) {
			var c = mgr.getBoxFirstCorner();
			ImGui.textWrapped(String.format(Locale.ROOT,
				"框选进行中：角点 A = %d, %d, %d。在场景区移动鼠标可预览选框，再左键点 B。", c.getX(), c.getY(), c.getZ()));
			if (ImGui.button("取消角点 A##selCancelBoxA")) {
				mgr.cancelBoxCorner();
			}
		}

		if (mgr.getMode() == SelectionMode.LINE && mgr.getLineFirstCorner() != null) {
			var c = mgr.getLineFirstCorner();
			ImGui.textWrapped(String.format(Locale.ROOT,
				"线选进行中：端点 A = %d, %d, %d。移动鼠标可预览范围，再左键点 B。", c.getX(), c.getY(), c.getZ()));
			if (ImGui.button("取消端点 A##selCancelLineA")) {
				mgr.cancelLineCorner();
			}
		}

		if (!mgr.getLastMessage().isBlank()) {
			ImGui.textWrapped(mgr.getLastMessage());
		}
		if (ImGui.button("清空选区##selClearAll")) {
			mgr.clearSelection();
		}
		ImGui.sameLine();
		if (ImGui.button("清除提示##selClearMsg")) {
			mgr.clearMessage();
		}

		ImGui.end();
	}

	private static String operationLabel(SelectionOperation op) {
		return switch (op) {
			case NEW -> "新建选区";
			case ADD -> "加选";
			case SUBTRACT -> "减选";
			case INTERSECT -> "交集";
		};
	}
}
