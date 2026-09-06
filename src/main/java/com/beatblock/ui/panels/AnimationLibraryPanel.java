package com.beatblock.ui.panels;

import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.ui.animation.AnimationLibraryItem;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 动画库面板：浏览、搜索、收藏并把 {@link AnimationLibraryItem} 应用到已选事件或拖入时间线。
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
			ImGui.text(BBTexts.get("beatblock.animation_library.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.animation_library.hint", presenter.catalogSize()));

			renderSearchBar();

			if (!state.editorReady()) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
			} else {
				renderItemList(state);
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

	private void renderItemList(AnimationLibraryPanelPresenter.ViewState state) {
		List<AnimationLibraryItem> filtered = presenter.filteredItems(searchBuffer.get());
		if (filtered.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.animation_library.no_matches"));
			return;
		}

		List<AnimationLibraryItem> favorites = presenter.favoriteItems(filtered);
		if (!favorites.isEmpty()) {
			ImGui.spacing();
			ImGui.textColored(0.95f, 0.85f, 0.35f, 1f, BBTexts.get("beatblock.animation_library.favorites"));
			if (ImGui.beginChild("##AnimationLibraryFavorites", 0, Math.min(favorites.size() * 28f + 8f, 120f), true)) {
				for (AnimationLibraryItem item : favorites) {
					renderItemEntry(item, state);
				}
			}
			ImGui.endChild();
			ImGui.separator();
		}

		Map<InfluenceDimension, List<AnimationLibraryItem>> groups = presenter.groupByPrimaryDimension(filtered);
		for (Map.Entry<InfluenceDimension, List<AnimationLibraryItem>> entry : groups.entrySet()) {
			ImGui.spacing();
			String dimLabel = BBTexts.get(
				"beatblock.animation_library.dimension." + entry.getKey().name().toLowerCase(Locale.ROOT));
			ImGui.textColored(0.4f, 0.8f, 1f, 1f, dimLabel + " (" + entry.getValue().size() + ")");
			if (ImGui.beginChild("##AnimationLibraryGroup_" + entry.getKey().name(), 0,
				Math.min(entry.getValue().size() * 28f + 8f, 160f), true)) {
				for (AnimationLibraryItem item : entry.getValue()) {
					renderItemEntry(item, state);
				}
			}
			ImGui.endChild();
		}
	}

	private void renderItemEntry(AnimationLibraryItem item, AnimationLibraryPanelPresenter.ViewState state) {
		if (item == null) {
			return;
		}
		String itemId = item.id();
		boolean favorite = AnimationLibraryFavorites.isFavorite(itemId);
		String star = favorite ? "★" : "☆";
		if (ImGui.smallButton(star + "##fav_" + itemId)) {
			AnimationLibraryFavorites.toggle(itemId);
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
		if (ImGui.smallButton(BBTexts.get("beatblock.animation_library.apply") + "##apply_" + itemId)) {
			var outcome = presenter.applyPresetToSelection(itemId);
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
			itemId,
			item.displayName(),
			item.defaultDurationSeconds(),
			itemId);
		PresetChannelPreview.renderCollapsibleChannelsOnly(label, item);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.animation_library.drag_hint"));
		}
		if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
			ImGui.setDragDropPayload(ANIMATION_PRESET_PAYLOAD_TYPE, itemId);
			ImGui.text(item.displayName());
			ImGui.text(String.format(Locale.ROOT, "%.2fs", item.defaultDurationSeconds()));
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
