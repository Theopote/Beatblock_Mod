package com.beatblock.ui;

import com.beatblock.BeatBlock;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.client.export.VideoExportCoordinator;
import com.beatblock.creator.CreationPreset;
import com.beatblock.timeline.interaction.PostGenerationFocusHelper;
import com.beatblock.timeline.rendering.SectionEditPopupCoordinator;
import com.beatblock.client.render.BeatBlockLassoOverlay;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.panels.*;
import com.beatblock.ui.preferences.BeatBlockShortcutHandler;
import com.beatblock.ui.preferences.UiPreferences;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelineActionDispatcher;
import com.beatblock.ui.presenter.TimelineActionId;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * BeatBlock 主 UI 管理器：菜单栏 + Dockspace + 各面板。
 * 布局：顶部菜单栏；底部时间线；左侧工具；右侧统一属性面板；中间不放置面板（即 Minecraft 场景）；动画库可开关。
 */
public class BeatBlockUIManager {

	private static final String DOCKSPACE_WINDOW_NAME = "BeatBlockDockSpace";
	private static final String DOCKSPACE_ID = "BeatBlockDockSpace";
	// 与 ChronoBlocks 一致：NoBackground + 完全透明，否则整屏一块黑半透明遮挡
	private static final int DOCKSPACE_FLAGS =
		ImGuiWindowFlags.NoTitleBar
			| ImGuiWindowFlags.NoCollapse
			| ImGuiWindowFlags.NoResize
			| ImGuiWindowFlags.NoMove
			| ImGuiWindowFlags.NoBringToFrontOnFocus
			| ImGuiWindowFlags.NoNavFocus
			| ImGuiWindowFlags.NoBackground;

	private final MenuBarPanel menuBarPanel;
	private final AudioAnalysisPanel audioAnalysisPanel;
	private final ToolPanel toolPanel;
	private final MarkerPanel markerPanel;
	private final TimelinePropertiesPanel timelinePropertiesPanel;
	private final TimelinePanel timelinePanel;
	private final AnimationLibraryPanel animationLibraryPanel;
	private final SelectionPropertiesPanel selectionPropertiesPanel;
	private final LayerPanel layerPanel;
	private final StageExplorerPanel stageExplorerPanel;
	private final RhythmDropPanel rhythmDropPanel;
	private final CreatorHomePanel creatorHomePanel;
	private final QuickStartWizardPanel quickStartWizardPanel;
	private final EnvironmentSetupPanel environmentSetupPanel;
	private final UndoHistoryPanel undoHistoryPanel;
	private final EventLibraryPanel eventLibraryPanel;
	private final CameraCreatorPanel cameraCreatorPanel;
	private final VfxCreatorPanel vfxCreatorPanel;
	private final PerformanceMonitorPanel performanceMonitorPanel;
	private final PreferencesPanel preferencesPanel;
	private final VideoExportDialog videoExportDialog;
	private final CreationPresetSelectDialog creationPresetSelectDialog;
	private final TimelineActionDispatcher timelineActions;

	private final BeatBlockPanelVisibility panelVisibility = new BeatBlockPanelVisibility();
	private boolean firstLayout = true;

	public BeatBlockUIManager(Runnable onCloseRequest) {
		this.timelineActions = PresenterFactories.timelineActionDispatcher();
		this.toolPanel = new ToolPanel(
			() -> panelVisibility.selectionProperties.set(true),
			this::openSectionEdit,
			() -> panelVisibility.stageExplorer.set(true)
		);
		this.markerPanel = new MarkerPanel();
		this.audioAnalysisPanel = new AudioAnalysisPanel(() -> panelVisibility.timeline.set(true));
		this.menuBarPanel = new MenuBarPanel(onCloseRequest, panelVisibility,
			() -> toolPanel.setShowAutoMapSettings(true),
			this::generateRhythmDropFromMenu,
			this::resetLayoutState, this::saveCurrentLayout, this::loadSavedLayout,
			this::openQuickStartWizard, this::openVideoExportDialog,
			this::openEnvironmentSetup, this::openCreatorHome, this::openCreationPresetSelectDialog);
		this.creatorHomePanel = new CreatorHomePanel(new CreatorHomePanel.Actions(
			this::openQuickStartWizard,
                menuBarPanel::requestOpenProject,
                menuBarPanel::requestNewProject
		));
		this.timelinePropertiesPanel = new TimelinePropertiesPanel();
		TimelinePanelVisibility.bind(panelVisibility);
		this.timelinePanel = new TimelinePanel();
 		this.animationLibraryPanel = new AnimationLibraryPanel(PresenterFactories.animationLibraryPanelPresenter());
		this.selectionPropertiesPanel = new SelectionPropertiesPanel();
		this.layerPanel = new LayerPanel();
		this.stageExplorerPanel = new StageExplorerPanel(
			PresenterFactories.stageExplorerPresenter(),
			layerPanel
		);
		this.rhythmDropPanel = new RhythmDropPanel();
		this.quickStartWizardPanel = new QuickStartWizardPanel(new QuickStartWizardPanel.DoneActions(
			this::playPreviewFromWizard,
			this::editTimelineFromWizard,
			this::editChoreographyFromWizard,
			this::saveProjectFromWizard,
			this::exportVideoFromWizard,
			this::openStageExplorerFromWizard,
			this::focusQuickStartStageObjectInExplorer
		));
		this.environmentSetupPanel = new EnvironmentSetupPanel();
		this.undoHistoryPanel = new UndoHistoryPanel();
		this.eventLibraryPanel = new EventLibraryPanel();
		this.cameraCreatorPanel = new CameraCreatorPanel();
		this.vfxCreatorPanel = new VfxCreatorPanel();
		this.performanceMonitorPanel = new PerformanceMonitorPanel();
		this.preferencesPanel = new PreferencesPanel();
		this.videoExportDialog = new VideoExportDialog();
		this.creationPresetSelectDialog = new CreationPresetSelectDialog();
	}

	public void openQuickStartWizard() {
		quickStartWizardPanel.open();
	}

	public void openCreatorHome() {
		creatorHomePanel.open();
	}

	public void openCreationPresetSelectDialog() {
		creationPresetSelectDialog.openForTimelineApply();
	}

	private void playPreviewFromWizard() {
		panelVisibility.timeline.set(true);
		var context = BeatBlock.getContext();
		var editor = context.timelineEditor();
		if (editor != null) {
			PresenterFactories.timelineTransportPresenter(context).play(editor);
		}
	}

	private void editTimelineFromWizard() {
		panelVisibility.timeline.set(true);
		panelVisibility.timelineProperties.set(true);
		panelVisibility.tool.set(true);
		focusAfterWizardGeneration();
	}

	private void editChoreographyFromWizard() {
		CreationPreset preset = quickStartWizardPanel.creationPreset();
		panelVisibility.timeline.set(true);
		panelVisibility.timelineProperties.set(true);
		panelVisibility.tool.set(true);
		focusAfterWizardGeneration();

		switch (preset.doneRefinementTarget()) {
			case BINDING_EDITOR -> menuBarPanel.requestBindingEditor();
			case RHYTHM_DROP -> panelVisibility.rhythmDrop.set(true);
			case SECTION_EDIT -> {
				var timeline = BeatBlock.getContext().timeline();
				if (timeline != null && ChoreographyPlanStore.hasPlan(timeline)) {
					SectionEditPopupCoordinator.requestOpen(0);
					ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.wizard.done.refinement.section_toast"));
				} else {
					ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.wizard.done.refinement.timeline_toast"));
				}
			}
			case TIMELINE -> ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.wizard.done.refinement.timeline_toast"));
		}
	}

	public void openSectionEdit() {
		SectionEditPopupCoordinator.requestOpen();
	}

	private void focusAfterWizardGeneration() {
		focusQuickStartStageObjectInExplorer();
		var context = BeatBlock.getContext();
		PostGenerationFocusHelper.focusStageObjectOrFirstAnimation(
			context.timeline(),
			context.timelineEditor(),
			quickStartWizardPanel.lastGenerateStageObjectId()
		);
	}

	private void openStageExplorerFromWizard() {
		panelVisibility.stageExplorer.set(true);
		focusQuickStartStageObjectInExplorer();
		ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.wizard.done.open_stage_explorer.toast"));
	}

	private void focusQuickStartStageObjectInExplorer() {
		String stageObjectId = quickStartWizardPanel.lastGenerateStageObjectId();
		if (stageObjectId == null || stageObjectId.isBlank()) {
			return;
		}
		PresenterFactories.stageExplorerPresenter().focusAnimationTarget(stageObjectId);
	}

	private void saveProjectFromWizard() {
		menuBarPanel.requestSaveProject();
	}

	private void exportVideoFromWizard() {
		openVideoExportDialog();
	}

	public void openEnvironmentSetup() {
		environmentSetupPanel.open();
	}

	public void openVideoExportDialog() {
		videoExportDialog.open();
	}

	private void generateRhythmDropFromMenu() {
		var result = timelineActions.execute(TimelineActionId.GENERATE_RHYTHM_DROP);
		BeatBlockSelectionManager.get().setMessage(result.message());
	}

	private void saveCurrentLayout() {
		try {
			ImGuiIO io = ImGui.getIO();
			if (io != null) {
				String path = io.getIniFilename();
				if (path != null && !path.isBlank()) {
					ImGui.saveIniSettingsToDisk(path);
					ToastNotificationSystem.showSuccess(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.layout_saved"));
					return;
				}
			}
			ToastNotificationSystem.showError(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.layout_path_unavailable"));
		} catch (Throwable error) {
			ToastNotificationSystem.showError(com.beatblock.ui.i18n.BBTexts.get(
				"beatblock.message.layout_save_failed", error.getMessage()));
		}
	}

	private void loadSavedLayout() {
		try {
			ImGuiIO io = ImGui.getIO();
			if (io != null) {
				String path = io.getIniFilename();
				if (path != null && !path.isBlank()) {
					ImGui.loadIniSettingsFromDisk(path);
					ToastNotificationSystem.showSuccess(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.layout_loaded"));
					return;
				}
			}
			ToastNotificationSystem.showError(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.layout_path_unavailable"));
		} catch (Throwable error) {
			ToastNotificationSystem.showError(com.beatblock.ui.i18n.BBTexts.get(
				"beatblock.message.layout_load_failed", error.getMessage()));
		}
	}

	public void render() {
		BeatBlockShortcutHandler.processGlobalShortcuts(timelineActions, new BeatBlockShortcutHandler.MenuActions() {
			@Override public void openImportMusic() { menuBarPanel.requestImportMusic(); }
			@Override public void saveProject() { menuBarPanel.requestSaveProject(); }
			@Override public void openProject() { menuBarPanel.requestOpenProject(); }
			@Override public void generateRhythmDrop() { generateRhythmDropFromMenu(); }
		});

		if (VideoExportCoordinator.getInstance().shouldHideEditorChrome()) {
			com.beatblock.client.render.GlobalVisualEffectOverlay.render();
			videoExportDialog.render();
			return;
		}

		// 1. 菜单栏（独立于 Dockspace）
		menuBarPanel.render();

		// 2. Dockspace 窗口（与 ChronoBlocks 一致：完全透明 + NoBackground，不遮挡场景）
		imgui.ImGuiViewport viewport = ImGui.getMainViewport();
		ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
		ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
		ImGui.setNextWindowViewport(viewport.getID());

		ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0f, 0f, 0f, 0f);

		int dockspaceId = -1;
		if (ImGui.begin(DOCKSPACE_WINDOW_NAME, DOCKSPACE_FLAGS)) {
			dockspaceId = ImGui.getID(DOCKSPACE_ID);
			ImGui.dockSpace(dockspaceId, 0, 0, imgui.internal.flag.ImGuiDockNodeFlags.PassthruCentralNode);

			if (firstLayout && dockspaceId != -1) {
				BeatBlockDockSpaceLayoutBuilder.buildDefaultLayout(dockspaceId);
				firstLayout = false;
			}
		}
		ImGui.end();

		ImGui.popStyleColor(3);
		ImGui.popStyleVar(3);

		if (dockspaceId == -1) return;

		// 3. 各停靠面板：主题色
		UiPreferences.pushPanelThemeColors();
		audioAnalysisPanel.render(panelVisibility.audioAnalysis);
		toolPanel.render(panelVisibility.tool);
		markerPanel.render(panelVisibility.marker);
		timelinePropertiesPanel.render(panelVisibility.timelineProperties);
		timelinePanel.render(panelVisibility.timeline);
		animationLibraryPanel.render(panelVisibility.animationLibrary);
		selectionPropertiesPanel.render(panelVisibility.selectionProperties);
		stageExplorerPanel.render(panelVisibility.stageExplorer);
		layerPanel.render(panelVisibility.layer);
		rhythmDropPanel.render(panelVisibility.rhythmDrop);
		undoHistoryPanel.render(panelVisibility.undoHistory);
		eventLibraryPanel.render(panelVisibility.eventLibrary);
		cameraCreatorPanel.render(panelVisibility.cameraCreator);
		vfxCreatorPanel.render(panelVisibility.vfxCreator);
		performanceMonitorPanel.render(panelVisibility.performanceMonitor);
		preferencesPanel.render(panelVisibility.preferences);
		UiPreferences.popPanelThemeColors();

		environmentSetupPanel.render();
		boolean environmentSetupOpen = environmentSetupPanel.isOpen();
		creatorHomePanel.onUiOpened(environmentSetupOpen);
		creatorHomePanel.render();
		quickStartWizardPanel.render();
		creationPresetSelectDialog.render();
		videoExportDialog.render();
		com.beatblock.client.render.GlobalVisualEffectOverlay.render();
		BeatBlockLassoOverlay.render();
		// Multi-target animation placement (layer multi-select) — must run after panels open popups
		com.beatblock.timeline.generation.AnimationMultiTargetDropPrompt.render();
		// Pre-play Performance check (Timeline Compiler 2.0 Phase A)
		com.beatblock.timeline.playback.PerformanceCheckDialog.render();
		ToastNotificationSystem.render();
	}

	public void resetLayoutState() {
		BeatBlockDockSpaceLayoutBuilder.resetLayoutState();
		firstLayout = true;
		ToastNotificationSystem.showSuccess(com.beatblock.ui.i18n.BBTexts.get("beatblock.message.layout_reset"));
	}
}
