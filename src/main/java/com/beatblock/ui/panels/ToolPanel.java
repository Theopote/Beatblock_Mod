package com.beatblock.ui.panels;

import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.ToolPanelPresenter;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 左侧工具面板：层次为「场景选区 → 自动化编排 → 动画场景对象」。
 * Marker 管理与时间线动作调试已拆分至 {@link MarkerPanel}；
 * 天降方块（RhythmDrop）已拆分至 {@link RhythmDropPanel}。
 * 方块选择由 {@link BeatBlockSelectionManager} 管理；StageObject 创建使用轴对齐包围盒，
 * 默认从当前方块选区的外接 AABB 一键填入，避免与「框选工具」语义重复。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
	private final ToolPanelPresenter presenter;
	private final ImString stageObjectNameBuffer = new ImString(64);
	private final ImBoolean stageObjectIncludeAir = new ImBoolean(false);
	private final ImInt stageObjectSortingIndex = new ImInt(0);
	private final ImString stageObjectStaggerBuffer = new ImString(16);
	private final ImString selectionPresetNameBuffer = new ImString(48);
	private final ImInt selectedPresetIndex = new ImInt(-1);
	private String selectionPresetMessage;
	private String stageObjectMessage;
	private long stageObjectMessageTimeMs;
	private static String[] stageGroupSortingLabels() {
		return BBTexts.labels(
			"beatblock.tool.sorting.sequential",
			"beatblock.tool.sorting.radial",
			"beatblock.tool.sorting.spiral",
			"beatblock.tool.sorting.random",
			"beatblock.tool.sorting.all"
		);
	}
	private final Runnable onSelectionToolChosen;

	public ToolPanel() {
		this(null);
	}

	public ToolPanel(Runnable onSelectionToolChosen) {
		this(onSelectionToolChosen, PresenterFactories.toolPanelPresenter());
	}

	ToolPanel(Runnable onSelectionToolChosen, ToolPanelPresenter presenter) {
		this.onSelectionToolChosen = onSelectionToolChosen;
		this.presenter = presenter;
		stageObjectNameBuffer.set("selection_object");
		stageObjectStaggerBuffer.set("0.00");
	}

	/** 由菜单栏「演出 → Smart Auto Map」调用，打开设置弹窗 */
	public void setShowAutoMapSettings(boolean show) {
		this.showAutoMapSettings = show;
	}
	/** 上次生成统计 */
	private SmartAutoMapEngine.AutoMapResult lastAutoMapResult = null;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.toolPanelWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.toolPanelWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text(BBTexts.get("beatblock.tool.title"));
			ImGui.separator();

			renderBlockSelectionTools();

			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.tool.automation"));
			ImGui.separator();
			if (ImGui.button(BBTexts.get("beatblock.tool.smart_auto_map"))) {
				showAutoMapSettings = true;
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.tool.smart_auto_map.tooltip"));
			}
			if (lastAutoMapResult != null) {
				ImGui.sameLine();
				ImGui.textDisabled(BBTexts.get("beatblock.tool.last_result",
					lastAutoMapResult.getAnimationEvents(),
					lastAutoMapResult.getCameraEvents(),
					lastAutoMapResult.getParticleEvents()));
			}

			renderStageObjectCreator();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.toolPanelWindow());
		}

		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}

	private void renderBlockSelectionTools() {
		ImGui.text(BBTexts.get("beatblock.tool.block_selection"));
		var state = presenter.selectionToolViewState();
		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		if (ImGui.beginCombo("##bselCombo", ToolPanelPresenter.selectionModeLabel(state.mode()))) {
			for (SelectionMode mode : ToolPanelPresenter.selectionComboOrder()) {
				boolean selected = state.mode() == mode;
				if (ImGui.selectable(ToolPanelPresenter.selectionModeLabel(mode), selected)) {
					if (state.mode() != mode) {
						presenter.setSelectionMode(mode);
						if (onSelectionToolChosen != null) {
							onSelectionToolChosen.run();
						}
					}
				}
				if (selected) {
					ImGui.setItemDefaultFocus();
				}
			}
			ImGui.endCombo();
		}

		// === 动态显示当前工具的属性（集成选择属性面板功能） ===
		ImGui.spacing();
		ImGui.textColored(0.7f, 0.9f, 1f, 1f, BBTexts.get("beatblock.tool.tool_settings"));
		ImGui.separator();
		renderToolSpecificProperties(state.mode());

		// === 通用属性 ===
		ImGui.spacing();
		ImGui.textColored(0.7f, 0.9f, 1f, 1f, BBTexts.get("beatblock.tool.common_settings"));
		ImGui.separator();
		renderCommonSelectionProperties();

		ImGui.separator();
	}

	/**
	 * 根据当前选择的工具动态显示相应的属性（原SelectionPropertiesPanel的功能）
	 */
	private void renderToolSpecificProperties(SelectionMode mode) {
		var selMgr = com.beatblock.selection.BeatBlockSelectionManager.get();

		switch (mode) {
			case BRUSH -> {
				com.beatblock.selection.BrushShape shape = selMgr.getBrushShape();
				String shapeLabel = shape == com.beatblock.selection.BrushShape.SPHERE
					? BBTexts.get("beatblock.tool.shape.sphere")
					: BBTexts.get("beatblock.tool.shape.cube");
				if (ImGui.beginCombo(BBTexts.get("beatblock.tool.shape") + "##brushShape", shapeLabel)) {
					if (ImGui.selectable(BBTexts.get("beatblock.tool.shape.sphere") + "##sphereOpt", shape == com.beatblock.selection.BrushShape.SPHERE)) {
						selMgr.setBrushShape(com.beatblock.selection.BrushShape.SPHERE);
					}
					if (ImGui.selectable(BBTexts.get("beatblock.tool.shape.cube") + "##cubeOpt", shape == com.beatblock.selection.BrushShape.CUBE)) {
						selMgr.setBrushShape(com.beatblock.selection.BrushShape.CUBE);
					}
					ImGui.endCombo();
				}
				int[] radius = {selMgr.getSphereBrushRadius()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.size") + "##brushSize", radius, 1, 32)) {
					selMgr.setSphereBrushRadius(radius[0]);
				}
			}
			case LINE -> {
				int[] thickness = {selMgr.getLineThicknessRadius()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.line_thickness") + "##lineThick", thickness, 0, 32)) {
					selMgr.setLineThicknessRadius(thickness[0]);
				}
			}
			case CONNECTED, SELECTION_WAND -> {
				int[] spread = {selMgr.getMaxMagicWandSpreadFromSeed()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.spread_radius") + "##wandSpread", spread, 1, 256)) {
					selMgr.setMaxMagicWandSpreadFromSeed(spread[0]);
				}
				boolean fullState = selMgr.isConnectedMatchFullState();
				if (ImGui.checkbox(BBTexts.get("beatblock.tool.full_state_match") + "##fullState", new ImBoolean(fullState))) {
					selMgr.setConnectedMatchFullState(!fullState);
				}
			}
			case PLANE_SLICE -> {
				net.minecraft.util.math.Direction override = selMgr.getPlaneSliceFaceOverride();
				String[] faceLabels = BBTexts.labels(
					"beatblock.tool.face.auto", "+Y", "-Y", "+X", "-X", "+Z", "-Z");
				int faceIndex = override == null ? 0 :
					switch (override) {
						case UP -> 1; case DOWN -> 2; case EAST -> 3;
						case WEST -> 4; case SOUTH -> 5; case NORTH -> 6;
					};
				ImInt faceIndexImInt = new ImInt(faceIndex);
				if (ImGui.combo(BBTexts.get("beatblock.tool.slice_direction") + "##planeDir", faceIndexImInt, faceLabels)) {
					net.minecraft.util.math.Direction newDir = switch (faceIndexImInt.get()) {
						case 1 -> net.minecraft.util.math.Direction.UP;
						case 2 -> net.minecraft.util.math.Direction.DOWN;
						case 3 -> net.minecraft.util.math.Direction.EAST;
						case 4 -> net.minecraft.util.math.Direction.WEST;
						case 5 -> net.minecraft.util.math.Direction.SOUTH;
						case 6 -> net.minecraft.util.math.Direction.NORTH;
						default -> null;
					};
					selMgr.setPlaneSliceFaceOverride(newDir);
				}
			}
			case OFF, CLICK, BOX, COLUMN, LASSO -> ImGui.textDisabled(BBTexts.get("beatblock.tool.no_special_settings"));
		}
	}

	/**
	 * 渲染所有工具通用的属性
	 */
	private void renderCommonSelectionProperties() {
		var selMgr = com.beatblock.selection.BeatBlockSelectionManager.get();

		ImGui.text(BBTexts.get("beatblock.tool.operations"));
		com.beatblock.selection.SelectionOperation op = selMgr.getOperation();
		if (ImGui.radioButton(BBTexts.get("beatblock.tool.op.new") + "##opNew", op == com.beatblock.selection.SelectionOperation.NEW)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.NEW);
		}
		ImGui.sameLine();
		if (ImGui.radioButton(BBTexts.get("beatblock.tool.op.add") + "##opAdd", op == com.beatblock.selection.SelectionOperation.ADD)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.ADD);
		}
		ImGui.sameLine();
		if (ImGui.radioButton(BBTexts.get("beatblock.tool.op.subtract") + "##opSub", op == com.beatblock.selection.SelectionOperation.SUBTRACT)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.SUBTRACT);
		}

		int[] maxDist = {selMgr.getMaxDistanceFromCamera()};
		ImGui.setNextItemWidth(-1f);
		if (ImGui.sliderInt(BBTexts.get("beatblock.tool.camera_distance") + "##camDist", maxDist, 16, 512)) {
			selMgr.setMaxDistanceFromCamera(maxDist[0]);
		}

		boolean includeAir = selMgr.isIncludeAir();
		if (ImGui.checkbox(BBTexts.get("beatblock.tool.include_air") + "##includeAir", new ImBoolean(includeAir))) {
			selMgr.setIncludeAir(!includeAir);
		}

		int selCount = selMgr.getSelectedBlocks().size();
		if (selCount > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				BBTexts.get("beatblock.common.selected_blocks", selCount));
		}

		renderSelectionPresets();
	}

	private void renderSelectionPresets() {
		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.tool.selection_presets"));
		ImGui.separator();

		var presets = presenter.listSelectionPresets();
		String preview = BBTexts.get("beatblock.tool.selection_preset.empty");
		if (selectedPresetIndex.get() >= 0 && selectedPresetIndex.get() < presets.size()) {
			preview = presets.get(selectedPresetIndex.get()).label();
		}
		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		if (ImGui.beginCombo("##selectionPresetCombo", preview)) {
			for (int i = 0; i < presets.size(); i++) {
				boolean selected = selectedPresetIndex.get() == i;
				if (ImGui.selectable(presets.get(i).label() + "##preset" + i, selected)) {
					selectedPresetIndex.set(i);
				}
				if (selected) {
					ImGui.setItemDefaultFocus();
				}
			}
			ImGui.endCombo();
		}

		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		ImGui.inputText(BBTexts.get("beatblock.tool.selection_preset.name") + "##presetName", selectionPresetNameBuffer);

		if (ImGui.button(BBTexts.get("beatblock.tool.selection_preset.save") + "##savePreset")) {
			var outcome = presenter.saveCurrentSelectionAsPreset(selectionPresetNameBuffer.get());
			selectionPresetMessage = outcome.message();
			if (outcome.success()) {
				selectedPresetIndex.set(Math.max(0, presenter.listSelectionPresets().size() - 1));
			}
		}
		ImGui.sameLine();
		boolean canLoad = selectedPresetIndex.get() >= 0 && selectedPresetIndex.get() < presets.size();
		if (!canLoad) {
			ImGui.beginDisabled();
		}
		if (ImGui.button(BBTexts.get("beatblock.tool.selection_preset.load") + "##loadPreset")) {
			var outcome = presenter.loadSelectionPreset(presets.get(selectedPresetIndex.get()).id());
			selectionPresetMessage = outcome.message();
		}
		if (!canLoad) {
			ImGui.endDisabled();
		}
		ImGui.sameLine();
		if (!canLoad) {
			ImGui.beginDisabled();
		}
		if (ImGui.button(BBTexts.get("beatblock.tool.selection_preset.delete") + "##deletePreset")) {
			String presetId = presets.get(selectedPresetIndex.get()).id();
			var outcome = presenter.deleteSelectionPreset(presetId);
			selectionPresetMessage = outcome.message();
			if (outcome.success()) {
				selectedPresetIndex.set(-1);
			}
		}
		if (!canLoad) {
			ImGui.endDisabled();
		}

		if (selectionPresetMessage != null && !selectionPresetMessage.isBlank()) {
			ImGui.textWrapped(selectionPresetMessage);
		}
	}

	private void renderStageObjectCreator() {
		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.tool.stage_object"));
		ImGui.separator();
		ImGui.textWrapped(BBTexts.get("beatblock.tool.stage_object.hint"));

		var selectionState = presenter.selectionToolViewState();
		int selCount = selectionState.selectionCount();
		if (selCount > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				BBTexts.get("beatblock.tool.selected_count", selCount));
		} else {
			ImGui.textColored(1f, 0.6f, 0.2f, 1f, BBTexts.get("beatblock.tool.select_blocks_first"));
		}

		// === 快速创建按钮（推荐） ===
		boolean canCreateFromSelection = selCount > 0;
		if (!canCreateFromSelection) ImGui.beginDisabled();

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);

		if (ImGui.button(BBTexts.get("beatblock.tool.quick_create") + "##quickCreate", -1f, 32f)) {
			quickCreateFromSelection();
		}

		ImGui.popStyleColor(3);

		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.tool.quick_create.tooltip"));
		}

		if (!canCreateFromSelection) ImGui.endDisabled();

		// === 精确创建（快照模式） ===
		ImGui.spacing();
		if (!canCreateFromSelection) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.tool.precise_create") + "##stageCreateFromSelection", -1f, 0f)) {
			var outcome = presenter.createFromSelectionSnapshot(buildQuickStageObjectRequest());
			applyStageObjectMessage(outcome.result());
		}
		if (!canCreateFromSelection) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.tool.precise_create.tooltip"));
		}

		// === 高级选项（折叠） ===
		ImGui.spacing();
		ImGui.setNextItemOpen(false, ImGuiCond.Once);
		if (ImGui.collapsingHeader(BBTexts.get("beatblock.tool.advanced_options") + "##stageAdvanced")) {
			ImGui.textWrapped(BBTexts.get("beatblock.tool.advanced.hint"));

			ImGui.spacing();
			ImGui.text(BBTexts.get("beatblock.tool.object_name"));
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("##stageObjName", stageObjectNameBuffer);

			ImGui.text(BBTexts.get("beatblock.tool.corner_points"));
			ToolPanelPresenter.CornerState corners = presenter.currentCorners();
			ImGui.textDisabled(BBTexts.get("beatblock.tool.corner_a", ToolPanelPresenter.formatPos(corners.posA())));
			ImGui.textDisabled(BBTexts.get("beatblock.tool.corner_b", ToolPanelPresenter.formatPos(corners.posB())));

			if (ImGui.button(BBTexts.get("beatblock.tool.fill_from_selection") + "##stageFromSel", -1f, 0f)) {
				applyStageObjectMessage(presenter.fillCornersFromSelection().result());
			}

			ImGui.setNextItemOpen(false, ImGuiCond.Once);
			if (ImGui.treeNode(BBTexts.get("beatblock.tool.crosshair_corners") + "##stageManualCorner")) {
				ImGui.textWrapped(BBTexts.get("beatblock.tool.crosshair_corners"));
				if (ImGui.button(BBTexts.get("beatblock.tool.crosshair_to_a") + "##stageObjSetA")) {
					applyStageObjectMessage(presenter.setCornerFromCrosshair(true).result());
				}
				ImGui.sameLine();
				if (ImGui.button(BBTexts.get("beatblock.tool.crosshair_to_b") + "##stageObjSetB")) {
					applyStageObjectMessage(presenter.setCornerFromCrosshair(false).result());
				}
				if (ImGui.button(BBTexts.get("beatblock.tool.clear_corners") + "##stageObjClearSelection")) {
					applyStageObjectMessage(presenter.clearCorners().result());
				}
				ImGui.treePop();
			}

			ImGui.spacing();
			ImGui.checkbox(BBTexts.get("beatblock.tool.include_air") + "##stageObjIncludeAir", stageObjectIncludeAir);

			ImGui.spacing();
			ImGui.text(BBTexts.get("beatblock.tool.sorting_strategy"));
			ImGui.setNextItemWidth(-1f);
			ImGui.combo("##stageGroupSorting", stageObjectSortingIndex, stageGroupSortingLabels());

			ImGui.text(BBTexts.get("beatblock.tool.stagger_delay"));
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("##stageGroupStagger", stageObjectStaggerBuffer);

			ImGui.spacing();
			boolean canCreate = corners.posA() != null && corners.posB() != null;
			if (!canCreate) ImGui.beginDisabled();
			if (ImGui.button(BBTexts.get("beatblock.tool.create_custom") + "##stageObjCreate", -1f, 0f)) {
				var outcome = presenter.createFromCuboid(buildStageObjectRequest());
				applyStageObjectMessage(outcome.result());
			}
			if (!canCreate) ImGui.endDisabled();
		}

		if (stageObjectMessage != null && !stageObjectMessage.isBlank()
				&& System.currentTimeMillis() - stageObjectMessageTimeMs < 5000L) {
			ImGui.textWrapped(stageObjectMessage);
		}

		renderStageObjectList();
	}

	private void renderStageObjectList() {
		var objects = presenter.listStageObjects();
		if (objects.isEmpty()) {
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.tool.no_stage_objects"));
			return;
		}

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.tool.registered_objects", objects.size()));
		String removeId = null;
		if (ImGui.beginChild("##StageObjectList", 0, Math.min(objects.size() * 22f + 8f, 160f), true)) {
			for (var obj : objects) {
				String label = BBTexts.get("beatblock.tool.stage_object_entry", obj.name(), obj.id(), obj.blockCount());
				ImGui.text(label);
				ImGui.sameLine();
				ImGui.textDisabled(BBTexts.get("beatblock.tool.source_type", obj.sourceType()));
				ImGui.sameLine();
				if (ImGui.smallButton(BBTexts.get("beatblock.common.delete") + "##stageObjDel_" + obj.id())) {
					removeId = obj.id();
				}
			}
		}
		ImGui.endChild();

		if (removeId != null) {
			applyStageObjectMessage(presenter.removeStageObject(removeId));
		}
	}

	private ToolPanelPresenter.StageObjectCreateRequest buildStageObjectRequest() {
		return new ToolPanelPresenter.StageObjectCreateRequest(
			stageObjectNameBuffer.get(),
			stageObjectIncludeAir.get(),
			ToolPanelPresenter.sortingStrategyAtIndex(stageObjectSortingIndex.get()),
			ToolPanelPresenter.parseStaggerSeconds(stageObjectStaggerBuffer.get())
		);
	}

	/**
	 * 快速创建：自动生成名称，使用默认参数
	 */
	private void quickCreateFromSelection() {
		// 自动生成名称 selection_1, selection_2, ...
		String autoName = generateAutoObjectName();

		// 使用默认参数
		ToolPanelPresenter.StageObjectCreateRequest request =
			new ToolPanelPresenter.StageObjectCreateRequest(
				autoName,
				false,  // 默认不包含空气
				com.beatblock.engine.GroupSortingStrategy.SEQUENTIAL,  // 默认顺序
				0.0     // 默认无延迟
			);

		var outcome = presenter.createFromSelectionSnapshot(request);
		applyStageObjectMessage(outcome.result());

		// 如果创建成功，显示提示
		if (outcome.result().ok()) {
			stageObjectMessage = BBTexts.get("beatblock.tool.created_hint", autoName);
			stageObjectMessageTimeMs = System.currentTimeMillis();
		}
	}

	/**
	 * 用于快速创建的简化请求（使用当前输入但允许为空时自动命名）
	 */
	private ToolPanelPresenter.StageObjectCreateRequest buildQuickStageObjectRequest() {
		String name = stageObjectNameBuffer.get();
		if (name == null || name.isBlank()) {
			name = generateAutoObjectName();
		}
		return new ToolPanelPresenter.StageObjectCreateRequest(
			name,
			stageObjectIncludeAir.get(),
			ToolPanelPresenter.sortingStrategyAtIndex(stageObjectSortingIndex.get()),
			ToolPanelPresenter.parseStaggerSeconds(stageObjectStaggerBuffer.get())
		);
	}

	/**
	 * 自动生成对象名称 selection_1, selection_2, ...
	 */
	private String generateAutoObjectName() {
		var existingObjects = presenter.listStageObjects();
		int counter = 1;
		while (true) {
			String candidate = "selection_" + counter;
			boolean exists = existingObjects.stream()
				.anyMatch(obj -> obj.id().equals(candidate));
			if (!exists) {
				return candidate;
			}
			counter++;
		}
	}

	private void applyStageObjectMessage(com.beatblock.ui.presenter.PresenterResult result) {
		if (result == null || result.messageOrEmpty().isBlank()) {
			return;
		}
		setStageObjectMessage(result.messageOrEmpty());
	}

	private void setStageObjectMessage(String msg) {
		stageObjectMessage = msg;
		stageObjectMessageTimeMs = System.currentTimeMillis();
	}
}
