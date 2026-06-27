package com.beatblock.ui.panels;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.i18n.BBTexts;
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

			ImGui.textDisabled(BBTexts.get("beatblock.selection.operation_mode"));
			for (SelectionOperation op : SelectionOperation.values()) {
				if (ImGui.radioButton(SelectionPropertiesPresenter.operationLabel(op) + "##selOp" + op.name(), state.operation() == op)) {
					presenter.setOperation(op);
				}
			}
			ImGui.textWrapped(BBTexts.get("beatblock.selection.operation_hint"));

			maxCameraDistScratch[0] = state.maxDistanceFromCamera();
			ImGui.setNextItemWidth(200f);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.max_camera_distance") + "##selCamDist", maxCameraDistScratch, 16, 512)) {
				presenter.setMaxDistanceFromCamera(maxCameraDistScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.max_camera_distance.tooltip"));
			}

			selectionFillProxy.set(state.selectionFillEnabled());
			ImGui.checkbox(BBTexts.get("beatblock.selection.fill") + "##selFill", selectionFillProxy);
			presenter.setSelectionFillEnabled(selectionFillProxy.get());
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.fill.tooltip"));
			}

			includeAirProxy.set(state.includeAir());
			ImGui.checkbox(BBTexts.get("beatblock.selection.include_air") + "##selIncludeAir", includeAirProxy);
			presenter.setIncludeAir(includeAirProxy.get());

			maxBlocksScratch[0] = state.maxBlocks();
			ImGui.setNextItemWidth(180f);
			if (ImGui.sliderInt(BBTexts.get("beatblock.selection.max_blocks") + "##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
				presenter.setMaxBlocks(maxBlocksScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.selection.max_blocks.tooltip"));
			}

			if (state.mode() == SelectionMode.LINE) {
				ImGui.separator();
				ImGui.textDisabled(BBTexts.get("beatblock.selection.line_mode"));
				lineThicknessScratch[0] = state.lineThicknessRadius();
				ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
				if (ImGui.sliderInt(BBTexts.get("beatblock.selection.line_thickness") + "##selLineThick", lineThicknessScratch, 0, 32)) {
					presenter.setLineThicknessRadius(lineThicknessScratch[0]);
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.selection.line_thickness.tooltip"));
				}
			}

			if (state.mode() == SelectionMode.BRUSH) {
				ImGui.separator();
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
			}

			if (state.mode() == SelectionMode.PLANE_SLICE) {
				ImGui.separator();
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
			}

			if (state.mode() == SelectionMode.CONNECTED || state.mode() == SelectionMode.SELECTION_WAND) {
				ImGui.separator();
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
				ImGui.checkbox(BBTexts.get("beatblock.selection.full_state") + "##selConnFull", connectedFullStateProxy);
				presenter.setConnectedMatchFullState(connectedFullStateProxy.get());
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.selection.full_state.tooltip"));
				}
			}

			ImGui.separator();
			ImGui.textDisabled(BBTexts.get("beatblock.selection.count", state.selectionCount()));
			var min = state.boundingMin();
			var max = state.boundingMax();
			if (min != null && max != null) {
				ImGui.textDisabled(BBTexts.get("beatblock.selection.bbox",
					min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
			}

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
			ImGui.textDisabled(BBTexts.get("beatblock.selection.rhythm_drop_section"));
			ImGui.textWrapped(BBTexts.get("beatblock.selection.rhythm_drop_hint"));
			if (state.selectionCount() <= 0) ImGui.beginDisabled();
			if (ImGui.button(BBTexts.get("beatblock.selection.generate_rhythm_drop") + "##selRhythmDrop", -1f, 0f)) {
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
}
