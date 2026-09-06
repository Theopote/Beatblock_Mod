package com.beatblock.ui.panels;

import com.beatblock.ui.BeatBlockPanelVisibility;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.timeline.rendering.TimelineBindingEditorPopup;
import com.beatblock.timeline.rendering.TimelineSectionEditPopup;
import com.beatblock.timeline.rendering.SectionEditPopupCoordinator;
import com.beatblock.ui.presenter.MenuBarPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelineActionDispatcher;
import com.beatblock.ui.presenter.TimelineActionId;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.preferences.BeatBlockShortcutId;
import com.beatblock.ui.preferences.UiPreferences;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 顶部通栏菜单栏：文件、编辑、视图、演出、帮助。
 */
public class MenuBarPanel {

	private static final int IMPORT_PATH_CAPACITY = 512;

	private final MenuBarPresenter presenter;
	private final Runnable onCloseRequest;
	private final BeatBlockPanelVisibility panels;
	private final Runnable onOpenSmartAutoMap;
	private final Runnable onGenerateRhythmDrop;
	private final Runnable onResetLayout;
	private final Runnable onSaveLayout;
	private final Runnable onLoadLayout;
	private final Runnable onOpenQuickStartWizard;
	private final Runnable onOpenEnvironmentSetup;
	private final Runnable onOpenVideoExport;
	private final TimelineActionDispatcher timelineActions;
	private final TimelineToolbarFeedbackPresenter showFeedback;
	private final TimelineBindingEditorPopup bindingEditorPopup;
	private final TimelineSectionEditPopup sectionEditPopup;
	private boolean showImportDialog;
	private boolean showOpenProjectDialog;
	private boolean showSaveProjectDialog;
	private boolean showAboutDialog;
	private boolean requestBindingEditorPopup;
	private final ImString importPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString openProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString saveProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private String projectDialogMessage = "";
	private String importDialogMessage = "";

	public MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onGenerateRhythmDrop, Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout,
			Runnable onOpenQuickStartWizard, Runnable onOpenVideoExport, Runnable onOpenEnvironmentSetup) {
		this(onCloseRequest, panels, onOpenSmartAutoMap, onGenerateRhythmDrop, onResetLayout, onSaveLayout, onLoadLayout,
			onOpenQuickStartWizard, onOpenVideoExport, onOpenEnvironmentSetup, PresenterFactories.menuBarPresenter());
	}

	MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onGenerateRhythmDrop, Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout,
			Runnable onOpenQuickStartWizard, Runnable onOpenVideoExport, Runnable onOpenEnvironmentSetup,
			MenuBarPresenter presenter) {
		this.presenter = presenter;
		this.onCloseRequest = onCloseRequest;
		this.panels = panels != null ? panels : new BeatBlockPanelVisibility();
		this.onOpenSmartAutoMap = onOpenSmartAutoMap != null ? onOpenSmartAutoMap : () -> {};
		this.onGenerateRhythmDrop = onGenerateRhythmDrop != null ? onGenerateRhythmDrop : () -> {};
		this.onResetLayout = onResetLayout != null ? onResetLayout : () -> {};
		this.onSaveLayout = onSaveLayout != null ? onSaveLayout : () -> {};
		this.onLoadLayout = onLoadLayout != null ? onLoadLayout : () -> {};
		this.onOpenQuickStartWizard = onOpenQuickStartWizard != null ? onOpenQuickStartWizard : () -> {};
		this.onOpenEnvironmentSetup = onOpenEnvironmentSetup != null ? onOpenEnvironmentSetup : () -> {};
		this.onOpenVideoExport = onOpenVideoExport != null ? onOpenVideoExport : () -> {};
		this.timelineActions = PresenterFactories.timelineActionDispatcher();
		this.showFeedback = PresenterFactories.timelineToolbarFeedbackPresenter();
		this.bindingEditorPopup = new TimelineBindingEditorPopup(
			PresenterFactories.timelineBindingEditorPresenter(), showFeedback);
		this.sectionEditPopup = new TimelineSectionEditPopup(
			PresenterFactories.timelineSectionEditPresenter(), showFeedback);
	}

	public void render() {
		if (!ImGui.beginMainMenuBar()) return;
		try {
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.file"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.open_project"), shortcut(BeatBlockShortcutId.OPEN_PROJECT))) {
					requestOpenProject();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.save_project"), shortcut(BeatBlockShortcutId.SAVE_PROJECT))) {
					requestSaveProject();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.save_project_as"))) {
					requestSaveProjectAs();
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.import_music"), shortcut(BeatBlockShortcutId.IMPORT_MUSIC))) {
					requestImportMusic();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.export_video"))) {
					onOpenVideoExport.run();
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.close_beatblock"), "Esc")) {
					if (onCloseRequest != null) onCloseRequest.run();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.edit"))) {
				var undoRedo = presenter.undoRedoState();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.undo"), shortcut(BeatBlockShortcutId.UNDO), false, undoRedo.canUndo())) {
					presenter.undo();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.redo"), shortcut(BeatBlockShortcutId.REDO), false, undoRedo.canRedo())) {
					presenter.redo();
				}
				ImGui.separator();
				var editState = presenter.editViewState();
				if (ImGui.menuItem(BBTexts.get("beatblock.common.cut"), shortcut(BeatBlockShortcutId.CUT), false, editState.hasSelection())) {
					presenter.cutTimelineSelection();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.common.copy"), shortcut(BeatBlockShortcutId.COPY), false, editState.hasSelection())) {
					presenter.copyTimelineSelection();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.paste_at_playhead"), shortcut(BeatBlockShortcutId.PASTE), false, editState.hasClipboard())) {
					presenter.pasteTimelineAtPlayhead();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.common.duplicate"), shortcut(BeatBlockShortcutId.DUPLICATE), false, editState.canDuplicate())) {
					presenter.duplicateTimelineSelection();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.common.split"), shortcut(BeatBlockShortcutId.SPLIT), false, editState.canSplitAtPlayhead())) {
					presenter.splitTimelineAtPlayhead();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.common.delete"), shortcut(BeatBlockShortcutId.DELETE), false, editState.canDelete())) {
					presenter.deleteTimelineSelection();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.view"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.close_all_panels"))) {
					panels.closeAll();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.open_all_panels"))) {
					panels.openAll();
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.reset_layout"))) {
					onResetLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.reset_layout"));
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.save_layout"))) {
					onSaveLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.save_layout"));
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.load_layout"))) {
					onLoadLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.load_layout"));
				}
				ImGui.separator();
				if (ImGui.beginMenu(BBTexts.get("beatblock.menu.panels"))) {
					panelToggleItem(BBTexts.get("beatblock.panel.audio_analysis"), panels.audioAnalysis);
					panelToggleItem(BBTexts.get("beatblock.panel.tool"), panels.tool);
					panelToggleItem(BBTexts.get("beatblock.panel.marker_debug"), panels.marker);
					panelToggleItem(BBTexts.get("beatblock.panel.timeline_properties"), panels.timelineProperties);
					panelToggleItem(BBTexts.get("beatblock.panel.timeline"), panels.timeline);
					panelToggleItem(BBTexts.get("beatblock.panel.animation_library"), panels.animationLibrary);
					panelToggleItem(BBTexts.get("beatblock.panel.selection_properties"), panels.selectionProperties);
					panelToggleItem(BBTexts.get("beatblock.panel.layer"), panels.layer);
					panelToggleItem(BBTexts.get("beatblock.panel.rhythm_drop"), panels.rhythmDrop);
					panelToggleItem(BBTexts.get("beatblock.panel.undo_history"), panels.undoHistory);
					panelToggleItem(BBTexts.get("beatblock.panel.event_library"), panels.eventLibrary);
					panelToggleItem(BBTexts.get("beatblock.panel.camera_creator"), panels.cameraCreator);
					panelToggleItem(BBTexts.get("beatblock.panel.performance_monitor"), panels.performanceMonitor);
					panelToggleItem(BBTexts.get("beatblock.panel.preferences"), panels.preferences);
					ImGui.endMenu();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.show"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.smart_auto_map"), BBTexts.get("beatblock.menu.smart_auto_map_shortcut"))) {
					onOpenSmartAutoMap.run();
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.tooltip.smart_auto_map"));
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.generate_rhythm_drop"), shortcut(BeatBlockShortcutId.GENERATE_RHYTHM_DROP))) {
					onGenerateRhythmDrop.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.generate_rhythm_drop"));
				}
				ImGui.separator();
				if (ImGui.beginMenu(BBTexts.get("beatblock.menu.mapping_and_generation"))) {
					if (ImGui.menuItem(BBTexts.get("beatblock.menu.generate_from_bindings"))) {
						showOutcome(timelineActions.execute(TimelineActionId.RUN_BINDING_MAP));
					}
					if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.binding_map.tooltip"));
					if (ImGui.menuItem(BBTexts.get("beatblock.timeline.auto_map"))) {
						showOutcome(timelineActions.execute(TimelineActionId.RUN_AUTO_MAP));
					}
					if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.auto_map.tooltip"));
					if (ImGui.menuItem(BBTexts.get("beatblock.menu.bake_step_events"))) {
						showOutcome(timelineActions.execute(TimelineActionId.BAKE_STEP));
					}
					if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.bake_step.tooltip"));
					ImGui.endMenu();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.bindings"))) {
					requestBindingEditorPopup = true;
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.binding_editor.tooltip"));
				if (ImGui.menuItem(BBTexts.get("beatblock.section_edit.menu"))) {
					SectionEditPopupCoordinator.requestOpen();
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.section_edit.menu.tooltip"));
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.help"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.environment_setup"))) {
					onOpenEnvironmentSetup.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.environment_setup"));
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.quick_start_wizard"))) {
					onOpenQuickStartWizard.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.quick_start_wizard"));
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.about"))) {
					showAboutDialog = true;
				}
				ImGui.endMenu();
			}
		} finally {
			ImGui.endMainMenuBar();
		}
		renderImportDialog();
		renderOpenProjectDialog();
		renderSaveProjectDialog();
		renderAboutDialog();
		if (requestBindingEditorPopup) {
			ImGui.openPopup(TimelineBindingEditorPopup.POPUP_ID);
			requestBindingEditorPopup = false;
		}
		if (SectionEditPopupCoordinator.consumeOpenRequest()) {
			sectionEditPopup.prepareForOpen(SectionEditPopupCoordinator.consumeSectionIndex());
			ImGui.openPopup(TimelineSectionEditPopup.POPUP_ID);
		}
		bindingEditorPopup.renderIfOpen();
		sectionEditPopup.renderIfOpen();
	}

	private static void showOutcome(TimelineActionDispatcher.ActionResult outcome) {
		if (outcome == null || outcome.message() == null || outcome.message().isBlank()) return;
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(outcome.message());
		} else {
			ToastNotificationSystem.showError(outcome.message());
		}
	}

	private static String shortcut(BeatBlockShortcutId id) {
		return UiPreferences.shortcut(id);
	}

	public void requestImportMusic() {
		showImportDialog = true;
		importDialogMessage = "";
		importPath.set("");
	}

	public void requestOpenProject() {
		showOpenProjectDialog = true;
		projectDialogMessage = "";
		openProjectPath.set("");
	}

	public void requestSaveProject() {
		String path = presenter.defaultSaveProjectPath();
		if (path == null || path.isBlank()) {
			requestSaveProjectAs();
			return;
		}
		showPresenterResult(presenter.saveProject(path));
	}

	private void requestSaveProjectAs() {
		showSaveProjectDialog = true;
		projectDialogMessage = "";
		saveProjectPath.set(presenter.defaultSaveProjectPath());
	}

	private static void showPresenterResult(com.beatblock.ui.presenter.PresenterResult result) {
		if (result == null || result.messageOrEmpty().isBlank()) return;
		if (result.ok()) ToastNotificationSystem.showSuccess(result.messageOrEmpty());
		else ToastNotificationSystem.showError(result.messageOrEmpty());
	}

	private static void panelToggleItem(String label, ImBoolean open) {
		boolean v = open.get();
		if (ImGui.menuItem(label, null, v)) {
			open.set(!v);
		}
	}

	private void renderImportDialog() {
		if (!showImportDialog) return;
		ImGui.setNextWindowSize(400, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.import_music"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.wav_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##path", importPath);
			if (ImGui.button(BBTexts.get("beatblock.common.import"))) {
				var result = presenter.importAudio(importPath.get());
				importDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showImportDialog = false;
					showPresenterResult(result);
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel"))) {
				showImportDialog = false;
			}
			if (!importDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(importDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderOpenProjectDialog() {
		if (!showOpenProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.open_project"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.project_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##openOscPath", openProjectPath);
			if (ImGui.button(BBTexts.get("beatblock.common.open"))) {
				var result = presenter.openProject(openProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showOpenProjectDialog = false;
					showPresenterResult(result);
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##openOsc")) {
				showOpenProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderSaveProjectDialog() {
		if (!showSaveProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.save_project"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.save_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##saveOscPath", saveProjectPath);
			if (ImGui.button(BBTexts.get("beatblock.common.save"))) {
				var result = presenter.saveProject(saveProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showSaveProjectDialog = false;
					showPresenterResult(result);
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##saveOsc")) {
				showSaveProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderAboutDialog() {
		if (!showAboutDialog) return;
		ImGui.setNextWindowSize(360, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.about"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.common.brand"));
			ImGui.text(BBTexts.get("beatblock.about.tagline"));
			ImGui.spacing();
			ImGui.textWrapped(BBTexts.get("beatblock.about.description"));
			ImGui.spacing();
			ImGui.text(BBTexts.get("beatblock.about.powered_by"));
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.common.ok"))) {
				showAboutDialog = false;
			}
		}
		ImGui.end();
	}
}
