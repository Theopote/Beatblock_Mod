package com.beatblock.ui.presenter;

import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick Start 生成事务用的 Timeline 可编辑层快照：覆盖向导会写入的轨道与编舞 metadata。
 */
public final class QuickStartTimelineSnapshot {

	private static final String[] TRACKED_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	private record ClipSnapshot(
		String clipId,
		double startTimeSeconds,
		double endTimeSeconds,
		List<TimelineEvent> events
	) {}

	private final Map<String, List<ClipSnapshot>> clipsByTrack;
	private final @Nullable Object choreographyPlan;
	private final @Nullable Object autoMapConfig;

	private QuickStartTimelineSnapshot(
		Map<String, List<ClipSnapshot>> clipsByTrack,
		@Nullable Object choreographyPlan,
		@Nullable Object autoMapConfig
	) {
		this.clipsByTrack = clipsByTrack;
		this.choreographyPlan = choreographyPlan;
		this.autoMapConfig = autoMapConfig;
	}

	public static QuickStartTimelineSnapshot capture(@Nullable Timeline timeline) {
		Map<String, List<ClipSnapshot>> clipsByTrack = new LinkedHashMap<>();
		if (timeline == null) {
			return new QuickStartTimelineSnapshot(clipsByTrack, null, null);
		}
		for (String trackId : TRACKED_TRACK_IDS) {
			Track track = timeline.getTrack(trackId);
			List<ClipSnapshot> clips = new ArrayList<>();
			if (track != null) {
				for (Clip clip : track.getClips()) {
					List<TimelineEvent> events = new ArrayList<>();
					for (TimelineEvent event : clip.getEvents()) {
						events.add(copyEvent(event));
					}
					clips.add(new ClipSnapshot(
						clip.getId(),
						clip.getStartTimeSeconds(),
						clip.getEndTimeSeconds(),
						events
					));
				}
			}
			clipsByTrack.put(trackId, clips);
		}
		return new QuickStartTimelineSnapshot(
			clipsByTrack,
			timeline.getMetadata(ChoreographyPlanStore.KEY_PLAN),
			timeline.getMetadata(ChoreographyPlanStore.KEY_CONFIG)
		);
	}

	public void restore(@Nullable Timeline timeline) {
		restoreTracks(timeline, TRACKED_TRACK_IDS, true);
	}

	/**
	 * 仅恢复指定轨道；{@code restoreMetadata} 为 true 时一并恢复编舞 plan/config。
	 */
	public void restoreTracks(
		@Nullable Timeline timeline,
		String[] trackIds,
		boolean restoreMetadata
	) {
		if (timeline == null || trackIds == null) {
			return;
		}
		for (String trackId : trackIds) {
			if (trackId == null || trackId.isBlank()) {
				continue;
			}
			Track track = timeline.getTrack(trackId);
			if (track == null) {
				continue;
			}
			for (Clip existing : new ArrayList<>(track.getClips())) {
				track.removeClip(existing.getId());
			}
			List<ClipSnapshot> snapshots = clipsByTrack.getOrDefault(trackId, List.of());
			for (ClipSnapshot snapshot : snapshots) {
				Clip clip = new Clip(snapshot.clipId(), snapshot.startTimeSeconds(), snapshot.endTimeSeconds());
				for (TimelineEvent event : snapshot.events()) {
					clip.addEvent(copyEvent(event));
				}
				track.addClip(clip);
			}
			timeline.markAnimationEventsDirty(trackId);
		}
		if (restoreMetadata) {
			timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, choreographyPlan);
			timeline.setMetadata(ChoreographyPlanStore.KEY_CONFIG, autoMapConfig);
		}
	}

	private static TimelineEvent copyEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new HashMap<>(source.getParameters())
		);
	}
}
