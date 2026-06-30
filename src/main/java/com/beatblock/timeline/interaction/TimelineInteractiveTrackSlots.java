package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.timeline.rendering.TrackRegistry;

import java.util.ArrayList;
import java.util.List;

/** 交互轨道槽位与锁定状态查询。 */
public final class TimelineInteractiveTrackSlots {

	private TimelineInteractiveTrackSlots() {}

	public record InteractiveTrackSlot(String trackId, int rowIndex) {}

	public static List<InteractiveTrackSlot> build(Timeline timeline) {
		List<InteractiveTrackSlot> slots = new ArrayList<>();
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_AUDIO, TimelineTrackMeta.ROW_AUDIO_GROUP));
		if (timeline != null) {
			List<TrackDefinition> defs = TrackRegistry.buildBlockAnimationControlTracks(timeline);
			for (int i = 0; i < defs.size() && i < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; i++) {
				slots.add(new InteractiveTrackSlot(defs.get(i).getKey(), TimelineTrackMeta.ROW_ANIM_FEATURES_START + i));
			}
		}
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_ANIMATION_BLOCK, TimelineTrackMeta.ROW_ANIM_BLOCK));
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_CAMERA, TimelineTrackMeta.ROW_CAMERA));
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_ANIMATION_AUTO, TimelineTrackMeta.ROW_ANIM_AUTO));
		if (timeline != null) {
			List<TrackDefinition> buildDefs = TrackRegistry.buildBuildLayerTracks(timeline);
			for (int i = 0; i < buildDefs.size() && i < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; i++) {
				slots.add(new InteractiveTrackSlot(
					buildDefs.get(i).getKey(),
					TimelineTrackMeta.ROW_BUILD_LAYER_START + i));
			}
		}
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_GLOBAL, TimelineTrackMeta.ROW_GLOBAL_EVENT));
		return slots;
	}

	public static int logicalRowForTrackId(Timeline timeline, String trackId) {
		if (trackId == null || trackId.isBlank()) return -1;
		for (InteractiveTrackSlot slot : build(timeline)) {
			if (trackId.equals(slot.trackId())) {
				return slot.rowIndex();
			}
		}
		return -1;
	}

	public static boolean isTrackLocked(
		Timeline timeline,
		TimelineTrackListState trackListState,
		String trackId
	) {
		if (trackListState == null || trackId == null || trackId.isBlank()) return false;
		int logicalRow = logicalRowForTrackId(timeline, trackId);
		if (logicalRow < 0) return false;
		return trackListState.isLocked(logicalRow);
	}
}
