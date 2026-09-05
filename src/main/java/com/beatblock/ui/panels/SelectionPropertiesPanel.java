package com.beatblock.ui.panels;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.SelectionPropertiesPresenter;
import com.beatblock.ui.presenter.SelectionPropertiesViewState;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.util.math.Direction;

/**
 * Advanced Selection Inspector：选区摘要、视图/上限与工具进阶参数。
 * <p>
 * 高频工具切换与 Operation 留在 {@link ToolPanel}；本面板不负责 Rhythm Drop 等生成动作。
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
	private final ImBoolean selectionFillProxy = new ImBoolean(false);

	private static String[] planeFaceLabels() {
		return BBTexts.labels(
			"beatblock.selection.plane_face.auto",
			"beatblock.selection.plane_face.up",
			"beatblock.selection.plane_face.down",
			"beatblock.selection.plane_face.east",
			"beatblock.selection.plane_face.west",
			"beatblock.selection.plane_face.south",
			"beatblock.selection.plane_face.north"
		);
	}

	private static final Direction[] PLANE_FACE_DIRS = {
		null,
		Direction.UP,
		Direction.DOWN,
		Direction.EAST,
		Direction.WEST,
		Direction.SOUTH,
		Direction.NORTH
	};

	private final SelectionPropertiesPresenter presenter;

	public SelectionPropertiesPanel() {
		this(PresenterFactories.selectionPropertiesPresenter());
	}

	SelectionPropertiesPanel(SelectionPropertiesPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.selectionPropertiesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.selectionPropertiesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			SelectionPropertiesViewState state = presenter.currentViewState();
			ImGui.text(BBTexts.get("beatblock.selection.title"));
			ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.75f, 1f, 1f);
			ImGui.text(BBTexts.get("beatblock.selection.current_tool", SelectionPropertiesPresenter.modeTitle(state.mode())));
			ImGui.popStyleColor();
			ImGui.separator();

			ImGui.textDisabled(BBTexts.get("beatblock.selection.section.summary"));
			ImGui.text(BBTexts.get("beatblock.selection.count", state.selectionCount()));
			var min = state.boundingMin();
			var max = state.boundingMax();
			if (min != null && max != null) {
				ImGui.textDisabled(BBTexts.get("beatblock.selection.bbox",
					min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
			}

			ImGui.separator();
			ImGui.textDisabled(BBTexts.get("beatblock.selection.section.view"));
			selectionFillProxy.set(state.selectionFillEnabled());
			if (ImGui.checkbox(BBTexts.get("beatblock.selection.fill") + "##selFill", selectionFillProxy)) {
				presenter.setSelectionFillEnabled(selectionFillProxy.get());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.fill.tooltip"));
			}

			includeAirProxy.set(state.includeAir());
			if (ImGui.checkbox(BBTexts.get("beatblock.selection.include_air") + "##selIncludeAir", includeAirProxy)) {
				presenter.setIncludeAir(includeAirProxy.get());
			}

			ImGui.separator();
			ImGui.textDisabled(BBTexts.get("beatblock.selection.section.limits"));
			maxCameraDistScratch[0] = state.maxDistanceFromCamera();
			ImGui.setNextItemWidth(200f);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.max_camera_distance") + "##selCamDist", maxCameraDistScratch, 16, 512)) {
				presenter.setMaxDistanceFromCamera(maxCameraDistScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.max_camera_distance.tooltip"));
			}

			maxBlocksScratch[0] = state.maxBlocks();
			ImGui.setNextItemWidth(180f);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.max_blocks") + "##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
				presenter.setMaxBlocks(maxBlocksScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.max_blocks.tooltip"));
			}

			ImGui.separator();
			ImGui.textDisabled(BBTexts.get("beatblock.selection.section.tool_advanced"));
			renderToolAdvanced(state);

			if (state.mode() == SelectionMode.BOX && state.boxFirstCorner() != null) {
				var c = state.boxFirstCorner();
				ImGui.textWrapped(BBTexts.get("beatblock.selection.box_in_progress", c.getX(), c.getY(), c.getZ()));
				if (ImGui.button(BBTexts.get("beatblock.selection.cancel_corner_a") + "##selCancelBoxA")) {
					presenter.cancelBoxCorner();
				}
			}

			if (state.mode() == SelectionMode.LINE && state.lineFirstCorner() != null) {
				var c = state.lineFirstCorner();
				ImGui.textWrapped(BBTexts.get("beatblock.selection.line_in_progress", c.getX(), c.getY(), c.getZ()));
				if (ImGui.button(BBTexts.get("beatblock.selection.cancel_endpoint_a") + "##selCancelLineA")) {
					presenter.cancelLineCorner();
				}
			}

			if (!state.lastMessage().isBlank()) {
				ImGui.textWrapped(state.lastMessage());
			}

			ImGui.separator();
			if (ImGui.button(BBTexts.get("beatblock.selection.clear") + "##selClearAll")) {
				presenter.clearSelection();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.selection.clear_message") + "##selClearMsg")) {
				presenter.clearMessage();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.selectionPropertiesWindow());
		}
	}

	private void renderToolAdvanced(SelectionPropertiesViewState state) {
		if (state.mode() == SelectionMode.LINE) {
			ImGui.textDisabled(BBTexts.get("beatblock.selection.line_mode"));
			lineThicknessScratch[0] = state.lineThicknessRadius();
			ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.line_thickness") + "##selLineThick", lineThicknessScratch, 0, 32)) {
				presenter.setLineThicknessRadius(lineThicknessScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.line_thickness.tooltip"));
			}
			return;
		}

		if (state.mode() == SelectionMode.BRUSH) {
			ImGui.textDisabled(BBTexts.get("beatblock.selection.brush_mode"));
			String shapePreview = state.brushShape() == BrushShape.SPHERE
				? BBTexts.get("beatblock.tool.shape.sphere")
				: BBTexts.get("beatblock.tool.shape.cube");
			ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
			if (ImGui.beginCombo(BBTexts.get("beatblock.tool.shape") + "##brushShapeCombo", shapePreview)) {
				if (ImGui.selectable(BBTexts.get("beatblock.tool.shape.sphere") + "##brushPickSph", state.brushShape() == BrushShape.SPHERE)) {
					presenter.setBrushShape(BrushShape.SPHERE);
				}
				if (ImGui.selectable(BBTexts.get("beatblock.tool.shape.cube") + "##brushPickCube", state.brushShape() == BrushShape.CUBE)) {
					presenter.setBrushShape(BrushShape.CUBE);
				}
				ImGui.endCombo();
			}
			sphereRadiusScratch[0] = state.sphereBrushRadius();
			ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.brush_size") + "##selBrushR", sphereRadiusScratch, 1, 32)) {
				presenter.setSphereBrushRadius(sphereRadiusScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.brush_size.tooltip"));
			}
			return;
		}

		if (state.mode() == SelectionMode.PLANE_SLICE) {
			ImGui.textDisabled(BBTexts.get("beatblock.selection.plane_slice"));
			int pIdx = SelectionPropertiesPresenter.planeFaceIndex(state.planeSliceFaceOverride(), PLANE_FACE_DIRS);
			ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
			if (ImGui.beginCombo(BBTexts.get("beatblock.selection.plane_face") + "##planeFaceCombo", planeFaceLabels()[pIdx])) {
				for (int i = 0; i < planeFaceLabels().length; i++) {
					if (ImGui.selectable(planeFaceLabels()[i] + "##pf" + i, i == pIdx)) {
						presenter.setPlaneSliceFaceOverride(PLANE_FACE_DIRS[i]);
					}
					if (i == pIdx) {
						ImGui.setItemDefaultFocus();
					}
				}
				ImGui.endCombo();
			}
			ImGui.textWrapped(BBTexts.get("beatblock.selection.plane_hint"));
			return;
		}

		if (state.mode() == SelectionMode.CONNECTED || state.mode() == SelectionMode.SELECTION_WAND) {
			ImGui.textDisabled(BBTexts.get("beatblock.selection.wand_mode"));
			ImGui.textWrapped(BBTexts.get("beatblock.selection.wand_hint"));
			maxWandSpreadScratch[0] = state.maxMagicWandSpreadFromSeed();
			ImGui.setNextItemWidth(200f);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.wand_spread") + "##selWandSpread", maxWandSpreadScratch, 1, 256)) {
				presenter.setMaxMagicWandSpreadFromSeed(maxWandSpreadScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.wand_spread.tooltip"));
			}
			connectedFullStateProxy.set(state.connectedMatchFullState());
			if (ImGui.checkbox(BBTexts.get("beatblock.selection.full_state") + "##selConnFull", connectedFullStateProxy)) {
				presenter.setConnectedMatchFullState(connectedFullStateProxy.get());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.full_state.tooltip"));
			}
			return;
		}

		ImGui.textDisabled(BBTexts.get("beatblock.tool.no_special_settings"));
	}
}
