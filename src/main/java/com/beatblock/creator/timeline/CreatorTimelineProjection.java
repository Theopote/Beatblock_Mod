package com.beatblock.creator.timeline;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.preferences.UiPreferences;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将技术轨道行投影为创作者作品视图：MUSIC / PERFORMANCE / BUILD / CAMERA / VFX。
 * <p>
 * 不修改 {@link Timeline} 底层 track id 或 .osc 持久化结构。
 */
public final class CreatorTimelineProjection {

	private CreatorTimelineProjection() {}

	public static boolean isEnabled() {
		return UiPreferences.creatorTimelineView();
	}

	public static @Nullable CreatorTrackGroup groupForRow(int rowIndex) {
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) {
			return CreatorTrackGroup.MUSIC;
		}
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			return CreatorTrackGroup.MUSIC;
		}
		if (rowIndex == TimelineTrackMeta.ROW_ANIMATION_GROUP
			|| TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			return CreatorTrackGroup.MUSIC_ANALYSIS;
		}
		if (rowIndex == TimelineTrackMeta.ROW_ACTION_GROUP
			|| rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK
			|| rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			return CreatorTrackGroup.PERFORMANCE;
		}
		if (TimelineTrackMeta.isBuildLayerSubRow(rowIndex)) {
			return CreatorTrackGroup.BUILD;
		}
		if (rowIndex == TimelineTrackMeta.ROW_CAMERA) {
			return CreatorTrackGroup.CAMERA;
		}
		if (rowIndex == TimelineTrackMeta.ROW_GLOBAL_EVENT) {
			return CreatorTrackGroup.VFX;
		}
		return null;
	}

	/** Creator 视图中完全隐藏的技术行（事件仍可通过合并行显示/交互）。 */
	public static boolean suppressesRow(int rowIndex) {
		return isEnabled() && rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO;
	}

	public static boolean mergesAutoOntoPerformanceRow(int rowIndex) {
		return isEnabled() && rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK;
	}

	public static int performanceInteractionRowForTrackId(String trackId) {
		if (!isEnabled()) {
			return TimelineTrackMeta.ROW_ANIM_AUTO;
		}
		if (Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)) {
			return TimelineTrackMeta.ROW_ANIM_BLOCK;
		}
		return -1;
	}

	public static String groupLabelKey(CreatorTrackGroup group) {
		return switch (group) {
			case MUSIC -> "beatblock.creator.track.music";
			case MUSIC_ANALYSIS -> "beatblock.creator.track.music_analysis";
			case PERFORMANCE -> "beatblock.creator.track.performance";
			case BUILD -> "beatblock.creator.track.build";
			case CAMERA -> "beatblock.creator.track.camera";
			case VFX -> "beatblock.creator.track.vfx";
		};
	}

	public static String groupLabel(CreatorTrackGroup group) {
		return BBTexts.get(groupLabelKey(group));
	}

	public static String creatorDisplayName(
		int rowIndex,
		@Nullable TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks,
		List<TrackDefinition> buildLayerTracks
	) {
		if (!isEnabled()) {
			return null;
		}
		CreatorTrackGroup group = groupForRow(rowIndex);
		if (group == null) {
			return null;
		}
		if (trackListState != null) {
			String custom = trackListState.getCustomNameOrNull(rowIndex);
			if (custom != null) {
				return custom;
			}
		}
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) {
			return groupLabel(CreatorTrackGroup.MUSIC);
		}
		if (rowIndex == TimelineTrackMeta.ROW_ANIMATION_GROUP) {
			return groupLabel(CreatorTrackGroup.MUSIC_ANALYSIS);
		}
		if (rowIndex == TimelineTrackMeta.ROW_ACTION_GROUP) {
			return groupLabel(CreatorTrackGroup.PERFORMANCE);
		}
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK) {
			return BBTexts.get("beatblock.creator.track.performance_lane");
		}
		if (rowIndex == TimelineTrackMeta.ROW_CAMERA) {
			return groupLabel(CreatorTrackGroup.CAMERA);
		}
		if (rowIndex == TimelineTrackMeta.ROW_GLOBAL_EVENT) {
			return groupLabel(CreatorTrackGroup.VFX);
		}
		if (TimelineTrackMeta.isBuildLayerSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.buildLayerSubRowSlot(rowIndex);
			if (slot == 0) {
				return groupLabel(CreatorTrackGroup.BUILD);
			}
			if (slot >= 0 && slot < buildLayerTracks.size()) {
				return buildLayerTracks.get(slot).getDisplayName();
			}
		}
		return null;
	}

	public static String creatorTypeLabel(int rowIndex) {
		if (!isEnabled()) {
			return null;
		}
		CreatorTrackGroup group = groupForRow(rowIndex);
		if (group == null) {
			return null;
		}
		return switch (group) {
			case MUSIC, MUSIC_ANALYSIS -> BBTexts.get("beatblock.creator.track.type.music");
			case PERFORMANCE -> BBTexts.get("beatblock.creator.track.type.performance");
			case BUILD -> BBTexts.get("beatblock.creator.track.type.build");
			case CAMERA -> BBTexts.get("beatblock.creator.track.type.camera");
			case VFX -> BBTexts.get("beatblock.creator.track.type.vfx");
		};
	}

	public static List<Integer> buildRowOrder(
		List<TrackDefinition> audioDefs,
		List<TrackDefinition> controlDefs,
		List<TrackDefinition> buildLayerDefs
	) {
		if (!isEnabled()) {
			return List.of();
		}
		List<Integer> ordered = new ArrayList<>(TimelineLayout.CONTENT_ROW_COUNT);
		Set<Integer> addedRows = new HashSet<>();

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_AUDIO_GROUP);
		appendPrimaryMusicRows(ordered, addedRows, audioDefs);

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIMATION_GROUP);
		appendAnalysisRows(ordered, addedRows, audioDefs, controlDefs);

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ACTION_GROUP);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_BLOCK);

		for (int slot = 0; slot < buildLayerDefs.size() && slot < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; slot++) {
			addRow(ordered, addedRows, TimelineTrackMeta.ROW_BUILD_LAYER_START + slot);
		}
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_CAMERA);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_GLOBAL_EVENT);

		for (int row = 0; row < TimelineLayout.CONTENT_ROW_COUNT; row++) {
			addRow(ordered, addedRows, row);
		}
		return ordered;
	}

	public static Map<Integer, Integer> buildRowParents(
		List<TrackDefinition> audioDefs,
		List<TrackDefinition> controlDefs
	) {
		if (!isEnabled()) {
			return Map.of();
		}
		Map<Integer, Integer> parents = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			TrackDefinition td = audioDefs.get(slot);
			parents.put(row, isPrimaryMusicTrack(td)
				? TimelineTrackMeta.ROW_AUDIO_GROUP
				: TimelineTrackMeta.ROW_ANIMATION_GROUP);
		}
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			parents.put(TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot, TimelineTrackMeta.ROW_ANIMATION_GROUP);
		}
		parents.put(TimelineTrackMeta.ROW_ACTION_GROUP, TimelineTrackMeta.NO_PARENT);
		parents.put(TimelineTrackMeta.ROW_ANIM_BLOCK, TimelineTrackMeta.ROW_ACTION_GROUP);
		for (int slot = 0; slot < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; slot++) {
			parents.put(TimelineTrackMeta.ROW_BUILD_LAYER_START + slot, TimelineTrackMeta.NO_PARENT);
		}
		parents.put(TimelineTrackMeta.ROW_CAMERA, TimelineTrackMeta.NO_PARENT);
		parents.put(TimelineTrackMeta.ROW_GLOBAL_EVENT, TimelineTrackMeta.NO_PARENT);
		return parents;
	}

	public static void applyCreatorDefaults(TimelineTrackListState state) {
		if (!isEnabled() || state == null) {
			return;
		}
		if (state.copyCollapsedGroupRows().isEmpty()) {
			state.setGroupCollapsed(TimelineTrackMeta.ROW_ANIMATION_GROUP, true);
		}
	}

	private static void appendPrimaryMusicRows(
		List<Integer> ordered,
		Set<Integer> addedRows,
		List<TrackDefinition> audioDefs
	) {
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			if (isPrimaryMusicTrack(td)) {
				addRow(ordered, addedRows, TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
			}
		}
	}

	private static void appendAnalysisRows(
		List<Integer> ordered,
		Set<Integer> addedRows,
		List<TrackDefinition> audioDefs,
		List<TrackDefinition> controlDefs
	) {
		Map<String, Integer> audioFeatureRows = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
				audioFeatureRows.put(td.getKey(), TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
			} else if (!isPrimaryMusicTrack(td)) {
				addRow(ordered, addedRows, TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
			}
		}
		Map<String, Integer> controlFeatureRows = new HashMap<>();
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(controlDefs.get(slot).getKey());
			if (featureKey != null && !featureKey.isBlank()) {
				controlFeatureRows.put(featureKey, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
			}
		}
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) {
				continue;
			}
			Integer audioRow = audioFeatureRows.get(td.getKey());
			Integer controlRow = controlFeatureRows.get(td.getKey());
			if (audioRow != null) {
				addRow(ordered, addedRows, audioRow);
			}
			if (controlRow != null) {
				addRow(ordered, addedRows, controlRow);
			}
		}
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}
	}

	private static boolean isPrimaryMusicTrack(TrackDefinition td) {
		if (td == null) {
			return false;
		}
		return "waveform".equals(td.getKey());
	}

	private static void addRow(List<Integer> ordered, Set<Integer> addedRows, int row) {
		if (row < 0 || row >= TimelineLayout.CONTENT_ROW_COUNT) {
			return;
		}
		if (suppressesRow(row)) {
			return;
		}
		if (addedRows.add(row)) {
			ordered.add(row);
		}
	}
}
