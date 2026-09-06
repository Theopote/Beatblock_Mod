package com.beatblock.ui.presenter;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.AnimationLibrary;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.animation.AnimationLibraryItem;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.preferences.AnimationLibraryFavorites;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Animation Library panel logic over {@link AnimationLibraryItem} / {@link AnimationDefinition}.
 */
public final class AnimationLibraryPanelPresenter {

	public record ViewState(
		boolean editorReady,
		boolean canApplyToSelection,
		int selectedAnimationEventCount,
		String statusMessage
	) {}

	public record ApplyOutcome(boolean success, String message) {}

	private final EventPropertiesPresenter eventPropertiesPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<AnimationLibrary> animationLibrary;

	private String statusMessage = "";

	public AnimationLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor
	) {
		this(eventPropertiesPresenter, timeline, timelineEditor, AnimationLibraryPanelPresenter::defaultLibrary);
	}

	public AnimationLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<AnimationLibrary> animationLibrary
	) {
		this.eventPropertiesPresenter = eventPropertiesPresenter;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.animationLibrary = animationLibrary != null ? animationLibrary : AnimationLibraryPanelPresenter::defaultLibrary;
	}

	public ViewState viewState() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return new ViewState(false, false, 0, statusMessage);
		}
		int count = eventPropertiesPresenter.countSelectedAnimationEvents(tl, editor.getSelectionState());
		return new ViewState(true, count > 0, count, statusMessage);
	}

	/** Catalog size for the panel hint line. */
	public int catalogSize() {
		AnimationLibrary library = libraryOrEmpty();
		return library.getAll().size();
	}

	/**
	 * Filtered, display-name-sorted catalog items for the current search query.
	 */
	public List<AnimationLibraryItem> filteredItems(@Nullable String query) {
		String trimmed = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
		List<AnimationLibraryItem> out = new ArrayList<>();
		for (AnimationDefinition definition : libraryOrEmpty().getAll().values()) {
			if (definition == null) continue;
			if (trimmed.isEmpty()
				|| definition.getId().toLowerCase(Locale.ROOT).contains(trimmed)
				|| definition.getName().toLowerCase(Locale.ROOT).contains(trimmed)) {
				out.add(new AnimationLibraryItem(definition));
			}
		}
		out.sort(Comparator.comparing(AnimationLibraryItem::displayName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	/** Favorite items that also appear in {@code items}, preserving favorites order. */
	public List<AnimationLibraryItem> favoriteItems(Collection<AnimationLibraryItem> items) {
		if (items == null || items.isEmpty()) return List.of();
		Map<String, AnimationLibraryItem> byId = new LinkedHashMap<>();
		for (AnimationLibraryItem item : items) {
			if (item != null) byId.put(item.id(), item);
		}
		List<AnimationLibraryItem> out = new ArrayList<>();
		for (String id : AnimationLibraryFavorites.all()) {
			AnimationLibraryItem item = byId.get(id);
			if (item != null) out.add(item);
		}
		return out;
	}

	public Map<InfluenceDimension, List<AnimationLibraryItem>> groupByPrimaryDimension(
		List<AnimationLibraryItem> items
	) {
		Map<InfluenceDimension, List<AnimationLibraryItem>> groups = new LinkedHashMap<>();
		for (InfluenceDimension dim : InfluenceDimension.values()) {
			groups.put(dim, new ArrayList<>());
		}
		if (items != null) {
			for (AnimationLibraryItem item : items) {
				if (item == null) continue;
				groups.get(item.primaryDimension()).add(item);
			}
		}
		groups.values().removeIf(List::isEmpty);
		return groups;
	}

	public ApplyOutcome applyPresetToSelection(String presetId) {
		AnimationDefinition definition = libraryOrEmpty().get(presetId);
		if (definition == null) {
			return fail(BBTexts.get("beatblock.animation_library.preset_missing"));
		}
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		SelectionState selectionState = editor.getSelectionState();
		CommandManager commandManager = editor.getCommandManager();
		EventPropertiesPresenter.BatchEditOutcome outcome = eventPropertiesPresenter.applyBatchAnimationEdit(
			tl,
			selectionState,
			commandManager,
			EventPropertiesPresenter.BatchAnimationEditRequest.replaceAnimation(
				definition.getId(),
				definition.getDurationSeconds()
			)
		);
		if (outcome.success()) {
			statusMessage = BBTexts.get(
				"beatblock.animation_library.applied",
				definition.getName(),
				outcome.updatedCount()
			);
			return new ApplyOutcome(true, statusMessage);
		}
		String error = outcome.errorMessage();
		return fail(error != null && !error.isBlank() ? error : BBTexts.get("beatblock.animation_library.apply_failed"));
	}

	private ApplyOutcome fail(String message) {
		statusMessage = message;
		return new ApplyOutcome(false, message);
	}

	private AnimationLibrary libraryOrEmpty() {
		AnimationLibrary library = animationLibrary.get();
		return library != null ? library : new AnimationLibrary();
	}

	private static AnimationLibrary defaultLibrary() {
		return new AnimationLibrary();
	}
}
