package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;
import org.jspecify.annotations.Nullable;

/**
 * Quick Start / Auto Map 生成后，将时间线选区聚焦到舞台对象的首个动画事件。
 */
public final class PostGenerationFocusHelper {

	private PostGenerationFocusHelper() {}

	public static void focusStageObjectOrFirstAnimation(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable String stageObjectId
	) {
		if (timeline == null || editor == null) {
			return;
		}
		TimelineEventRef target = findEarliestAnimationForTarget(timeline, stageObjectId);
		if (target == null) {
			target = findEarliestAnimation(timeline);
		}
		if (target == null) {
			return;
		}

		SelectionState selection = editor.getSelectionState();
		selection.clearAll();
		selection.selectClip(target.clip().getId());
		selection.selectEvent(target.event().getId());
		editor.getPlaybackSession().seek(target.event().getTimeSeconds());
	}

	public static @Nullable TimelineEventRef findEarliestAnimationForTarget(
		Timeline timeline,
		@Nullable String stageObjectId
	) {
		if (timeline == null || stageObjectId == null || stageObjectId.isBlank()) {
			return null;
		}
		String wanted = stageObjectId.trim();
		TimelineEventRef best = null;
		double bestTime = Double.POSITIVE_INFINITY;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) {
				continue;
			}
			for (Clip clip : track.getClips()) {
				if (clip == null) {
					continue;
				}
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) {
						continue;
					}
					if (!wanted.equals(targetObjectId(event))) {
						continue;
					}
					double time = event.getTimeSeconds();
					if (time < bestTime) {
						bestTime = time;
						best = new TimelineEventRef(track, clip, event);
					}
				}
			}
		}
		return best;
	}

	public static @Nullable TimelineEventRef findEarliestAnimation(Timeline timeline) {
		if (timeline == null) {
			return null;
		}
		TimelineEventRef best = null;
		double bestTime = Double.POSITIVE_INFINITY;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) {
				continue;
			}
			for (Clip clip : track.getClips()) {
				if (clip == null) {
					continue;
				}
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) {
						continue;
					}
					double time = event.getTimeSeconds();
					if (time < bestTime) {
						bestTime = time;
						best = new TimelineEventRef(track, clip, event);
					}
				}
			}
		}
		return best;
	}

	private static String targetObjectId(TimelineEvent event) {
		Object raw = event.getParameters().get("targetObject");
		return raw != null ? String.valueOf(raw).trim() : "";
	}
}
