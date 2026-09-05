package com.beatblock.ui.panels;

import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.SelectionPropertiesPresenter;
import com.beatblock.ui.presenter.SelectionPropertiesViewState;
import com.beatblock.ui.presenter.ToolPanelPresenter;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 左侧工具面板：选区快速操作 → 自动化编排 → 舞台对象。
 * <p>
 * 高频选区参数在此；进阶摘要与上限见 {@link SelectionPropertiesPanel}。
 * Marker / RhythmDrop 已拆至独立面板。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
	private final ToolPanelPresenter presenter;
	private final SelectionPropertiesPresenter selectionPresenter;
	private final ImString stageObjectNameBuffer = new ImString(64);
	private final ImBoolean stageObjectIncludeAir = new ImBoolean(false);
	private final ImInt stageObjectSortingIndex = new ImInt(0);
	private final ImString stageObjectStaggerBuffer = new ImString(16);
	private final ImString selectionPresetNameBuffer = new ImString(48);
	private final ImInt selectedPresetIndex = new ImInt(-1);
	private final int[] primaryParamScratch = new int[1];
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
	private final Runnable onOpenSelectionInspector;

	public ToolPanel() {
		this(null);
	}

	public ToolPanel(Runnable onOpenSelectionInspector) {
		this(
			onOpenSelectionInspector,
			PresenterFactories.toolPanelPresenter(),
			PresenterFactories.selectionPropertiesPresenter()
		);
	}

	ToolPanel(
		Runnable onOpenSelectionInspector,
		ToolPanelPresenter presenter,
		SelectionPropertiesPresenter selectionPresenter
	) {
		this.onOpenSelectionInspector = onOpenSelectionInspector;
		this.presenter = presenter;
		this.selectionPresenter = selectionPresenter;
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
		SelectionPropertiesViewState sel = selectionPresenter.currentViewState();
		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		if (ImGui.beginCombo("##bselCombo", ToolPanelPresenter.selectionModeLabel(sel.mode()))) {
			for (SelectionMode mode : ToolPanelPresenter.selectionComboOrder()) {
				boolean selected = sel.mode() == mode;
				if (ImGui.selectable(ToolPanelPresenter.selectionModeLabel(mode), selected)) {
					if (sel.mode() != mode) {
						presenter.setSelectionMode(mode);
					}
				}
				if (selected) {
					ImGui.setItemDefaultFocus();
				}
			}
			ImGui.endCombo();
		}

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.tool.operations"));
		SelectionOperation[] operations = SelectionOperation.values();
		for (int i = 0; i < operations.length; i++) {
			SelectionOperation op = operations[i];
			if (i > 0) {
				ImGui.sameLine();
			}
			if (ImGui.radioButton(
				SelectionPropertiesPresenter.operationLabel(op) + "##toolOp" + op.name(),
				sel.operation() == op
			)) {
				selectionPresenter.setOperation(op);
			}
		}

		ImGui.spacing();
		renderPrimaryToolParam(sel);

		int selCount = sel.selectionCount();
		if (selCount > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				BBTexts.get("beatblock.common.selected_blocks", selCount));
		} else {
			ImGui.textDisabled(BBTexts.get("beatblock.common.selected_blocks", 0));
		}

		if (ImGui.button(BBTexts.get("beatblock.tool.advanced_selection") + "##openSelInspector", -1f, 0f)) {
			if (onOpenSelectionInspector != null) {
				onOpenSelectionInspector.run();
			}
		}

		renderSelectionPresets();
		ImGui.separator();
	}

	/**
	 * 仅展示当前工具最高频的 1 个参数；形状 / 完整状态 / 平面朝向等在 Inspector。
	 */
	private void renderPrimaryToolParam(SelectionPropertiesViewState sel) {
		switch (sel.mode()) {
			case BRUSH -> {
				primaryParamScratch[0] = sel.sphereBrushRadius();
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.size") + "##brushSize", primaryParamScratch, 1, 32)) {
					selectionPresenter.setSphereBrushRadius(primaryParamScratch[0]);
				}
			}
			case LINE -> {
				primaryParamScratch[0] = sel.lineThicknessRadius();
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.line_thickness") + "##lineThick", primaryParamScratch, 0, 32)) {
					selectionPresenter.setLineThicknessRadius(primaryParamScratch[0]);
				}
			}
			case CONNECTED, SELECTION_WAND -> {
				primaryParamScratch[0] = sel.maxMagicWandSpreadFromSeed();
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt(BBTexts.get("beatblock.tool.spread_radius") + "##wandSpread", primaryParamScratch, 1, 256)) {
					selectionPresenter.setMaxMagicWandSpreadFromSeed(primaryParamScratch[0]);
				}
			}
			default -> {
				// Box / Click / Lasso / Plane / Column: no primary quick param
			}
		}
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
