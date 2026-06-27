package com.beatblock.ui.panels;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.Locale;

/**
 * 动画库面板：浏览全部 BlockInfluence 预设及其通道组合。
 */
public class AnimationLibraryPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var presets = BlockInfluencePresets.getAll();
			ImGui.text(BBTexts.get("beatblock.animation_library.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.animation_library.hint", presets.size()));

			ImGui.spacing();
			if (ImGui.beginChild("##AnimationLibraryList", 0, 0, false)) {
				for (BlockInfluencePreset preset : presets.values()) {
					renderPresetEntry(preset);
				}
			}
			ImGui.endChild();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow());
		}
	}

	private static void renderPresetEntry(BlockInfluencePreset preset) {
		if (preset == null) {
			return;
		}
		String label = String.format(Locale.ROOT, "%s · %s (%.2fs)##presetLib_%s",
			preset.getId(),
			preset.getDisplayName(),
			preset.getDefaultDurationSeconds(),
			preset.getId());
		PresetChannelPreview.renderCollapsibleChannelsOnly(label, preset);
	}
}
