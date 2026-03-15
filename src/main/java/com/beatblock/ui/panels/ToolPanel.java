package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 左侧工具面板。提供 Smart Auto Map 等工具。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	/** 上次 Smart Auto Map 生成数量，用于界面反馈 */
	private int lastAutoMapCount = -1;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("工具");
		ImGui.separator();

		if (ImGui.button("Smart Auto Map")) {
			if (BeatBlock.timeline != null) {
				AutoMapConfig config = AutoMapConfig.createDefault();
				lastAutoMapCount = AutoMapGenerator.generate(BeatBlock.timeline, config, true);
			} else {
				lastAutoMapCount = -1;
			}
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("根据当前频段事件自动生成动画事件（先导入音乐）");
		}
		if (lastAutoMapCount >= 0) {
			ImGui.sameLine();
			ImGui.textDisabled(String.format("已生成 %d 个", lastAutoMapCount));
		}

		ImGui.spacing();
		ImGui.textWrapped("选择、画笔、橡皮等工具将在此列出。");
		ImGui.end();
	}
}
