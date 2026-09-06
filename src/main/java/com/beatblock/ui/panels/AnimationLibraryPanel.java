package com.beatblock.ui.panels;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.PresetChannelPreview;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.preferences.AnimationLibraryFavorites;
import com.beatblock.ui.presenter.AnimationLibraryPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 动画库面板：浏览、搜索、收藏并把 {@link BlockInfluencePreset} 应用到已选事件或拖入时间线。
 */
public class AnimationLibraryPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int SEARCH_CAPACITY = 128;
	public static final String ANIMATION_PRESET_PAYLOAD_TYPE = "BB_ANIMATION_PRESET_ID";

	private final AnimationLibraryPanelPresenter presenter;
	private final ImString searchBuffer = new ImString(SEARCH_CAPACITY);

	public AnimationLibraryPanel() {
		this(PresenterFactories.animationLibraryPanelPresenter());
	}

	public AnimationLibraryPanel(AnimationLibraryPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var state = presenter.viewState();
			Map<String, BlockInfluencePreset> presets = BlockInfluencePresets.getAll();
			ImGui.text(BBTexts.get("beatblock.animation_library.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.animation_library.hint", presets.size()));

			renderSearchBar();

			if (!state.editorReady()) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
			} else {
				renderPresetList(state);
			}

			if (!state.statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(state.statusMessage());
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.animationLibraryWindow());
		}
	}

	private void renderSearchBar() {
		ImGui.setNextItemWidth(-1f);
		ImGui.inputTextWithHint("##AnimationLibrarySearch",
			BBTexts.get("beatblock.animation_library.search"), searchBuffer);
	}

	private void renderPresetList(AnimationLibraryPanelPresenter.ViewState state) {
		List<BlockInfluencePreset> filtered = filterPresets(BlockInfluencePresets.getAll().values(), searchBuffer.get());
		if (filtered.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.animation_library.no_matches"));
			return;
		}

		List<BlockInfluencePreset> favorites = favoritePresets(filtered);
		if (!favorites.isEmpty()) {
			ImGui.spacing();
			ImGui.textColored(0.95f, 0.85f, 0.35f, 1f, BBTexts.get("beatblock.animation_library.favorites"));
			if (ImGui.beginChild("##AnimationLibraryFavorites", 0, Math.min(favorites.size() * 28f + 8f, 120f), true)) {
				for (BlockInfluencePreset preset : favorites) {
					renderPresetEntry(preset, state);
				}
			}
			ImGui.endChild();
			ImGui.separator();
		}

		Map<InfluenceDimension, List<BlockInfluencePreset>> groups = groupByPrimaryDimension(filtered);
		for (Map.Entry<InfluenceDimension, List<BlockInfluencePreset>> entry : groups.entrySet()) {
			ImGui.spacing();
			String dimLabel = BBTexts.get(
				"beatblock.animation_library.dimension." + entry.getKey().name().toLowerCase(Locale.ROOT));
			ImGui.textColored(0.4f, 0.8f, 1f, 1f, dimLabel + " (" + entry.getValue().size() + ")");
			if (ImGui.beginChild("##AnimationLibraryGroup_" + entry.getKey().name(), 0,
				Math.min(entry.getValue().size() * 28f + 8f, 160f), true)) {
				for (BlockInfluencePreset preset : entry.getValue()) {
					renderPresetEntry(preset, state);
				}
			}
			ImGui.endChild();
		}
	}

	private static List<BlockInfluencePreset> filterPresets(Collection<BlockInfluencePreset> presets, String query) {
		String trimmed = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
		List<BlockInfluencePreset> out = new ArrayList<>();
		for (BlockInfluencePreset preset : presets) {
			if (preset == null) {
				continue;
			}
			if (trimmed.isEmpty()
				|| preset.getId().toLowerCase(Locale.ROOT).contains(trimmed)
				|| preset.getDisplayName().toLowerCase(Locale.ROOT).contains(trimmed)) {
				out.add(preset);
			}
		}
		out.sort(Comparator.comparing(BlockInfluencePreset::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static List<BlockInfluencePreset> favoritePresets(Collection<BlockInfluencePreset> presets) {
		List<BlockInfluencePreset> out = new ArrayList<>();
		for (String id : AnimationLibraryFavorites.all()) {
			BlockInfluencePreset preset = BlockInfluencePresets.get(id);
			if (preset != null && presets.contains(preset)) {
				out.add(preset);
			}
		}
		return out;
	}

	private static Map<InfluenceDimension, List<BlockInfluencePreset>> groupByPrimaryDimension(
		List<BlockInfluencePreset> presets
	) {
		Map<InfluenceDimension, List<BlockInfluencePreset>> groups = new LinkedHashMap<>();
		for (InfluenceDimension dim : InfluenceDimension.values()) {
			groups.put(dim, new ArrayList<>());
		}
		for (BlockInfluencePreset preset : presets) {
			InfluenceDimension dim = primaryDimension(preset);
			groups.get(dim).add(preset);
		}
		groups.values().removeIf(List::isEmpty);
		return groups;
	}

	private static InfluenceDimension primaryDimension(BlockInfluencePreset preset) {
		if (preset == null || preset.getChannels().isEmpty()) {
			return InfluenceDimension.EXISTENCE;
		}
		for (var channel : preset.getChannels()) {
			if (channel != null && channel.enabled()) {
				return channel.dimension();
			}
		}
		return InfluenceDimension.EXISTENCE;
	}

	private void renderPresetEntry(BlockInfluencePreset preset, AnimationLibraryPanelPresenter.ViewState state) {
		if (preset == null) {
			return;
		}
		String presetId = preset.getId();
		boolean favorite = AnimationLibraryFavorites.isFavorite(presetId);
		String star = favorite ? "★" : "☆";
		if (ImGui.smallButton(star + "##fav_" + presetId)) {
			AnimationLibraryFavorites.toggle(presetId);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(favorite
				? BBTexts.get("beatblock.animation_library.unfavorite")
				: BBTexts.get("beatblock.animation_library.favorite"));
		}

		ImGui.sameLine();
		if (!state.canApplyToSelection()) {
			ImGui.beginDisabled();
		}
		if (ImGui.smallButton(BBTexts.get("beatblock.animation_library.apply") + "##apply_" + presetId)) {
			var outcome = presenter.applyPresetToSelection(presetId);
			notify(outcome);
		}
		if (!state.canApplyToSelection()) {
			ImGui.endDisabled();
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(state.canApplyToSelection()
				? BBTexts.get("beatblock.animation_library.apply.tooltip")
				: BBTexts.get("beatblock.animation_library.apply.disabled"));
		}

		ImGui.sameLine();
		String label = String.format(Locale.ROOT, "%s · %s (%.2fs)##presetLib_%s",
			presetId,
			preset.getDisplayName(),
			preset.getDefaultDurationSeconds(),
			presetId);
		PresetChannelPreview.renderCollapsibleChannelsOnly(label, preset);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.animation_library.drag_hint"));
		}
		if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
			ImGui.setDragDropPayload(ANIMATION_PRESET_PAYLOAD_TYPE, presetId);
			ImGui.text(preset.getDisplayName());
			ImGui.text(String.format(Locale.ROOT, "%.2fs", preset.getDefaultDurationSeconds()));
			ImGui.textDisabled(BBTexts.get("beatblock.animation_library.drag_hint"));
			ImGui.endDragDropSource();
		}
	}

	private static void notify(AnimationLibraryPanelPresenter.ApplyOutcome outcome) {
		if (outcome.message() == null || outcome.message().isBlank()) {
			return;
		}
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(outcome.message());
		} else {
			ToastNotificationSystem.showError(outcome.message());
		}
	}
}
