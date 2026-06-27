package com.beatblock.ui.panels;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.preferences.AnimationLibraryFavorites;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
			Map<String, BlockInfluencePreset> presets = BlockInfluencePresets.getAll();
			ImGui.text(BBTexts.get("beatblock.animation_library.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.animation_library.hint", presets.size()));

			List<BlockInfluencePreset> favorites = favoritePresets(presets.values());
			if (!favorites.isEmpty()) {
				ImGui.spacing();
				ImGui.textColored(0.95f, 0.85f, 0.35f, 1f, BBTexts.get("beatblock.animation_library.favorites"));
				if (ImGui.beginChild("##AnimationLibraryFavorites", 0, Math.min(favorites.size() * 28f + 8f, 120f), true)) {
					for (BlockInfluencePreset preset : favorites) {
						renderPresetEntry(preset);
					}
				}
				ImGui.endChild();
				ImGui.separator();
			}

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

	private static List<BlockInfluencePreset> favoritePresets(Collection<BlockInfluencePreset> presets) {
		List<BlockInfluencePreset> out = new ArrayList<>();
		for (String id : AnimationLibraryFavorites.all()) {
			BlockInfluencePreset preset = BlockInfluencePresets.get(id);
			if (preset != null) {
				out.add(preset);
			}
		}
		return out;
	}

	private static void renderPresetEntry(BlockInfluencePreset preset) {
		if (preset == null) {
			return;
		}
		boolean favorite = AnimationLibraryFavorites.isFavorite(preset.getId());
		String star = favorite ? "★" : "☆";
		if (ImGui.smallButton(star + "##fav_" + preset.getId())) {
			AnimationLibraryFavorites.toggle(preset.getId());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(favorite
				? BBTexts.get("beatblock.animation_library.unfavorite")
				: BBTexts.get("beatblock.animation_library.favorite"));
		}
		ImGui.sameLine();
		String label = String.format(Locale.ROOT, "%s · %s (%.2fs)##presetLib_%s",
			preset.getId(),
			preset.getDisplayName(),
			preset.getDefaultDurationSeconds(),
			preset.getId());
		PresetChannelPreview.renderCollapsibleChannelsOnly(label, preset);
	}
}
