package com.beatblock.ui.panels;

import com.beatblock.ui.BeatBlockPanelVisibility;
import com.beatblock.ui.presenter.MenuBarPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
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
	private final Runnable onResetLayout;
	private final Runnable onSaveLayout;
	private final Runnable onLoadLayout;
	private boolean showImportDialog;
	private boolean showOpenProjectDialog;
	private boolean showSaveProjectDialog;
	private boolean showAboutDialog;
	private final ImString importPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString openProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString saveProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private String projectDialogMessage = "";

	public MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout) {
		this(onCloseRequest, panels, onOpenSmartAutoMap, onResetLayout, onSaveLayout, onLoadLayout,
			PresenterFactories.menuBarPresenter());
	}

	MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout, MenuBarPresenter presenter) {
		this.presenter = presenter;
		this.onCloseRequest = onCloseRequest;
		this.panels = panels != null ? panels : new BeatBlockPanelVisibility();
		this.onOpenSmartAutoMap = onOpenSmartAutoMap != null ? onOpenSmartAutoMap : () -> {};
		this.onResetLayout = onResetLayout != null ? onResetLayout : () -> {};
		this.onSaveLayout = onSaveLayout != null ? onSaveLayout : () -> {};
		this.onLoadLayout = onLoadLayout != null ? onLoadLayout : () -> {};
	}

	public void render() {
		if (!ImGui.beginMainMenuBar()) return;
		try {
			// 文件
			if (ImGui.beginMenu("文件")) {
				if (ImGui.menuItem("打开工程(.osc)", "Ctrl+Shift+O")) {
					showOpenProjectDialog = true;
					projectDialogMessage = "";
					openProjectPath.set("");
				}
				if (ImGui.menuItem("保存工程(.osc)", "Ctrl+S")) {
					showSaveProjectDialog = true;
					projectDialogMessage = "";
					saveProjectPath.set(presenter.defaultSaveProjectPath());
				}
				ImGui.separator();
				if (ImGui.menuItem("导入音乐", "Ctrl+O")) {
					showImportDialog = true;
					importPath.set("");
				}
				ImGui.separator();
				if (ImGui.menuItem("关闭 BeatBlock", "Esc")) {
					if (onCloseRequest != null) onCloseRequest.run();
				}
				ImGui.endMenu();
			}
			// 编辑
			if (ImGui.beginMenu("编辑")) {
				var undoRedo = presenter.undoRedoState();
				if (ImGui.menuItem("撤销", "Ctrl+Z", false, undoRedo.canUndo())) {
					presenter.undo();
				}
				if (ImGui.menuItem("重做", "Ctrl+Y", false, undoRedo.canRedo())) {
					presenter.redo();
				}
				ImGui.endMenu();
			}
			// 视图
			if (ImGui.beginMenu("视图")) {
				if (ImGui.menuItem("关闭所有面板")) {
					panels.closeAll();
				}
				if (ImGui.menuItem("打开所有面板")) {
					panels.openAll();
				}
				ImGui.separator();
				if (ImGui.menuItem("重置布局")) {
					onResetLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("恢复默认 Dock 分区（侧栏/底栏比例）。各面板显示开关保持当前状态。");
				}
				if (ImGui.menuItem("保存当前布局")) {
					onSaveLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("将窗口停靠与尺寸写入配置文件（游戏目录下 config/beatblock/imgui.ini）。");
				}
				if (ImGui.menuItem("载入已保存布局")) {
					onLoadLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("从 imgui.ini 重新载入窗口与停靠（需事先「保存当前布局」或存在该文件）。");
				}
				ImGui.separator();
				if (ImGui.beginMenu("面板")) {
					panelToggleItem("音频解析", panels.audioAnalysis);
					panelToggleItem("工具", panels.tool);
					panelToggleItem("标记与调试", panels.marker);
					panelToggleItem("事件属性", panels.eventProperties);
					panelToggleItem("时间线", panels.timeline);
					panelToggleItem("动画库", panels.animationLibrary);
					panelToggleItem("选择属性", panels.selectionProperties);
					panelToggleItem("建造图层", panels.layer);
					ImGui.endMenu();
				}
				ImGui.endMenu();
			}
			// 演出
			if (ImGui.beginMenu("演出")) {
				if (ImGui.menuItem("Smart Auto Map...", "自动编排")) {
                    onOpenSmartAutoMap.run();
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip("根据音乐自动生成方块动画、镜头与粒子");
				ImGui.endMenu();
			}
			// 帮助
			if (ImGui.beginMenu("帮助")) {
				if (ImGui.menuItem("关于 BeatBlock")) {
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
		if (ImGui.begin("导入音乐", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("WAV 文件路径（本地绝对路径）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##path", importPath);
			if (ImGui.button("导入")) {
				var result = presenter.importAudio(importPath.get());
				if (result.ok()) {
					showImportDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消")) {
				showImportDialog = false;
			}
		}
		ImGui.end();
	}

	private void renderOpenProjectDialog() {
		if (!showOpenProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin("打开工程 (.osc)", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("工程文件路径（.osc）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##openOscPath", openProjectPath);
			if (ImGui.button("打开")) {
				var result = presenter.openProject(openProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showOpenProjectDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消##openOsc")) {
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
		if (ImGui.begin("保存工程 (.osc)", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("保存路径（.osc）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##saveOscPath", saveProjectPath);
			if (ImGui.button("保存")) {
				var result = presenter.saveProject(saveProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showSaveProjectDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消##saveOsc")) {
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
		if (ImGui.begin("关于 BeatBlock", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("BeatBlock");
			ImGui.text("音乐驱动的 Minecraft 方块动画引擎");
			ImGui.spacing();
			ImGui.textWrapped("导入音乐、分析节拍与频段，在时间线上编排方块动画、镜头与粒子，打造随音乐起舞的视觉演出。");
			ImGui.spacing();
			ImGui.text("基于 Fabric 与 ImGui。");
			ImGui.spacing();
			if (ImGui.button("确定")) {
				showAboutDialog = false;
			}
		}
		ImGui.end();
	}
}
