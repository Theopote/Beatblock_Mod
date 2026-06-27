package com.beatblock.ui.panels;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.RhythmDropPanelPresenter;
import com.beatblock.ui.presenter.SelectionPropertiesPresenter;
import com.beatblock.ui.presenter.SelectionPropertiesViewState;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.util.math.Direction;

import java.util.Locale;

/**
 * 选择工具属性：操作模式、空气、上限、统计与清空（对应 ChronoBlocks 属性面板中的工具上下文，精简版）。
 */
public class SelectionPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final int[] maxBlocksScratch = new int[1];
	private final int[] sphereRadiusScratch = new int[1];
	private final int[] maxCameraDistScratch = new int[1];
	private final int[] maxWandSpreadScratch = new int[1];
	private final int[] lineThicknessScratch = new int[1];
	private final ImBoolean includeAirProxy = new ImBoolean(false);
	private final ImBoolean connectedFullStateProxy = new ImBoolean(false);

	private static final String[] PLANE_FACE_LABELS = {
		"自动（跟随点击面）",
		"水平：顶面 (+Y)",
		"水平：底面 (-Y)",
		"竖直：东 (+X)",
		"竖直：西 (-X)",
		"竖直：南 (+Z)",
		"竖直：北 (-Z)"
	};
	private static final Direction[] PLANE_FACE_DIRS = {
		null,
		Direction.UP,
		Direction.DOWN,
		Direction.EAST,
		Direction.WEST,
		Direction.SOUTH,
		Direction.NORTH
	};
	private final ImBoolean selectionFillProxy = new ImBoolean(false);
	private final SelectionPropertiesPresenter presenter;
	private final RhythmDropPanelPresenter rhythmDropPresenter;
	private String rhythmDropMessage;

	public SelectionPropertiesPanel() {
		this(PresenterFactories.selectionPropertiesPresenter(), PresenterFactories.rhythmDropPanelPresenter());
	}

	SelectionPropertiesPanel(SelectionPropertiesPresenter presenter) {
		this(presenter, PresenterFactories.rhythmDropPanelPresenter());
	}

	SelectionPropertiesPanel(SelectionPropertiesPresenter presenter, RhythmDropPanelPresenter rhythmDropPresenter) {
		this.presenter = presenter;
		this.rhythmDropPresenter = rhythmDropPresenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			SelectionPropertiesViewState state = presenter.currentViewState();
			ImGui.text("方块选择");
			ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.75f, 1f, 1f);
			ImGui.text("当前工具：" + SelectionPropertiesPresenter.modeTitle(state.mode()));
			ImGui.popStyleColor();
			ImGui.separator();

			ImGui.textDisabled("操作模式");
			for (SelectionOperation op : SelectionOperation.values()) {
				if (ImGui.radioButton(SelectionPropertiesPresenter.operationLabel(op) + "##selOp" + op.name(), state.operation() == op)) {
					presenter.setOperation(op);
				}
			}
			ImGui.textWrapped("说明：新建=替换选区。单击类工具（含平面切片、选区魔棒）按住 Shift 可强制加选；笔刷/套索涂抹同样适用。");

			maxCameraDistScratch[0] = state.maxDistanceFromCamera();
			ImGui.setNextItemWidth(200f);
			if (ImGui.sliderInt("相对视角最大距离（格）##selCamDist", maxCameraDistScratch, 16, 512)) {
				presenter.setMaxDistanceFromCamera(maxCameraDistScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("候选方块中心到相机不能超过此距离。套索、魔棒、切片、框/线/列/笔刷等均生效，防止无界选中。");
			}

			selectionFillProxy.set(state.selectionFillEnabled());
			ImGui.checkbox("选区半透明填充（与描边叠加）##selFill", selectionFillProxy);
			presenter.setSelectionFillEnabled(selectionFillProxy.get());
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("在方块选择 UI 打开且选中方块数量不太多时，对选区外表面做贪婪合并后绘制半透明面（与合并后的轮廓线叠加）；大量选区时仅显示总包围盒。");
			}

			includeAirProxy.set(state.includeAir());
			ImGui.checkbox("包含空气方块##selIncludeAir", includeAirProxy);
			presenter.setIncludeAir(includeAirProxy.get());

			maxBlocksScratch[0] = state.maxBlocks();
			ImGui.setNextItemWidth(180f);
			if (ImGui.sliderInt("框选方块上限##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
				presenter.setMaxBlocks(maxBlocksScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("框/线（含圆柱线粗）/笔刷/列/切片体积、连通与选区魔棒展开、笔刷单次盖章等超过此值时拒绝或截断。");
			}

			if (state.mode() == SelectionMode.LINE) {
				ImGui.separator();
				ImGui.textDisabled("线选");
				lineThicknessScratch[0] = state.lineThicknessRadius();
				ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
				if (ImGui.sliderInt("线粗细（半径，格）##selLineThick", lineThicknessScratch, 0, 32)) {
					presenter.setLineThicknessRadius(lineThicknessScratch[0]);
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("0：仅穿过中心折线上的方块；大于 0：以两端中心连线为轴、该半径的圆柱形范围（欧氏距离到轴线）。");
				}
			}

			if (state.mode() == SelectionMode.BRUSH) {
				ImGui.separator();
				ImGui.textDisabled("笔刷");
				String shapePreview = state.brushShape() == BrushShape.SPHERE ? "球体" : "立方体";
				ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
				if (ImGui.beginCombo("形状##brushShapeCombo", shapePreview)) {
					if (ImGui.selectable("球体##brushPickSph", state.brushShape() == BrushShape.SPHERE)) {
						presenter.setBrushShape(BrushShape.SPHERE);
					}
					if (ImGui.selectable("立方体##brushPickCube", state.brushShape() == BrushShape.CUBE)) {
						presenter.setBrushShape(BrushShape.CUBE);
					}
					ImGui.endCombo();
				}
				sphereRadiusScratch[0] = state.sphereBrushRadius();
				ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
				if (ImGui.sliderInt("大小（半径，格）##selBrushR", sphereRadiusScratch, 1, 32)) {
					presenter.setSphereBrushRadius(sphereRadiusScratch[0]);
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("球体：欧氏距离 ≤ r；立方体：轴对齐边长 2r+1。场景区预览为包络盒；单击盖章或按住涂抹。");
				}
			}

			if (state.mode() == SelectionMode.PLANE_SLICE) {
				ImGui.separator();
				ImGui.textDisabled("平面切片");
				int pIdx = SelectionPropertiesPresenter.planeFaceIndex(state.planeSliceFaceOverride(), PLANE_FACE_DIRS);
				ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
				if (ImGui.beginCombo("切片朝向（法向）##planeFaceCombo", PLANE_FACE_LABELS[pIdx])) {
					for (int i = 0; i < PLANE_FACE_LABELS.length; i++) {
						if (ImGui.selectable(PLANE_FACE_LABELS[i] + "##pf" + i, i == pIdx)) {
							presenter.setPlaneSliceFaceOverride(PLANE_FACE_DIRS[i]);
						}
						if (i == pIdx) {
							ImGui.setItemDefaultFocus();
						}
					}
					ImGui.endCombo();
				}
				ImGui.textWrapped("自动：使用射线击中的面。锁定朝向后，仍用点击方块的坐标定切片位置（例如水平面用点击格的 Y）。");
			}

			if (state.mode() == SelectionMode.CONNECTED || state.mode() == SelectionMode.SELECTION_WAND) {
				ImGui.separator();
				ImGui.textDisabled("魔棒");
				ImGui.textWrapped("默认按方块类型连通（同一方块 ID 即向六邻域扩展）。若只选中一格，可勾选「完整方块状态」尝试更严匹配。");
				maxWandSpreadScratch[0] = state.maxMagicWandSpreadFromSeed();
				ImGui.setNextItemWidth(200f);
				if (ImGui.sliderInt("最大扩散半径（格）##selWandSpread", maxWandSpreadScratch, 1, 256)) {
					presenter.setMaxMagicWandSpreadFromSeed(maxWandSpreadScratch[0]);
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("从点击的种子方块算起，欧氏距离超过此值的格子不会入选（全图魔棒与选区魔棒均适用）。");
				}
				connectedFullStateProxy.set(state.connectedMatchFullState());
				ImGui.checkbox("完整方块状态一致##selConnFull", connectedFullStateProxy);
				presenter.setConnectedMatchFullState(connectedFullStateProxy.get());
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("勾选：与起点 BlockState 完全相同才算同色；关闭：仅方块类型一致。");
				}
			}

			ImGui.separator();
			ImGui.textDisabled(String.format(Locale.ROOT, "已选方块: %d", state.selectionCount()));
			var min = state.boundingMin();
			var max = state.boundingMax();
			if (min != null && max != null) {
				ImGui.textDisabled(String.format(Locale.ROOT,
					"包围盒: [%d,%d,%d] — [%d,%d,%d]",
					min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
			}

			if (state.mode() == SelectionMode.BOX && state.boxFirstCorner() != null) {
				var c = state.boxFirstCorner();
				ImGui.textWrapped(String.format(Locale.ROOT,
					"框选进行中：角点 A = %d, %d, %d。在场景区移动鼠标可预览选框，再左键点 B。", c.getX(), c.getY(), c.getZ()));
				if (ImGui.button("取消角点 A##selCancelBoxA")) {
					presenter.cancelBoxCorner();
				}
			}

			if (state.mode() == SelectionMode.LINE && state.lineFirstCorner() != null) {
				var c = state.lineFirstCorner();
				ImGui.textWrapped(String.format(Locale.ROOT,
					"线选进行中：端点 A = %d, %d, %d。移动鼠标可预览范围，再左键点 B。", c.getX(), c.getY(), c.getZ()));
				if (ImGui.button("取消端点 A##selCancelLineA")) {
					presenter.cancelLineCorner();
				}
			}

			if (!state.lastMessage().isBlank()) {
				ImGui.textWrapped(state.lastMessage());
			}

			ImGui.separator();
			ImGui.textDisabled("天降方块");
			ImGui.textWrapped("将当前选区中的方块作为落点，按播放头与节拍生成 RhythmDrop 动画事件。");
			if (state.selectionCount() <= 0) ImGui.beginDisabled();
			if (ImGui.button("生成天降方块##selRhythmDrop", -1f, 0f)) {
				var result = rhythmDropPresenter.generateFromSelectionWithDefaults();
				rhythmDropMessage = result.messageOrEmpty();
				if (!rhythmDropMessage.isBlank()) {
					com.beatblock.selection.BeatBlockSelectionManager.get().setMessage(rhythmDropMessage);
				}
			}
			if (state.selectionCount() <= 0) ImGui.endDisabled();
			if (rhythmDropMessage != null && !rhythmDropMessage.isBlank()) {
				ImGui.textWrapped(rhythmDropMessage);
			}

			if (ImGui.button("清空选区##selClearAll")) {
				presenter.clearSelection();
			}
			ImGui.sameLine();
			if (ImGui.button("清除提示##selClearMsg")) {
				presenter.clearMessage();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW);
		}
	}
}
