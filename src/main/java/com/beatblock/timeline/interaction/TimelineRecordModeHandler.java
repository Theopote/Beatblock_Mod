package com.beatblock.timeline.interaction;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.util.SnapSystem;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** 实时录制：播放中按空格在当前播放头写入动画事件。 */
public final class TimelineRecordModeHandler {

	private static final double MERGE_EPSILON_SECONDS = 0.04;
	private static final double DEFAULT_DURATION_SECONDS = 0.46;
	private static final float DEFAULT_ENERGY = 0.7f;
	private static final String DEFAULT_ANIMATION_TYPE = "bounce";

	private TimelineRecordModeHandler() {}

	public record RecordOutcome(boolean success, String message) {}

	public static RecordOutcome recordAtPlayhead(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable TimelineToolbarState toolbarState,
		@Nullable StageObjectSystem stageObjectSystem,
		boolean playbackActive
	) {
		if (timeline == null || editor == null) {
			return new RecordOutcome(false, "timeline-unavailable");
		}
		if (!playbackActive && !editor.getClock().isPlaying()) {
			return new RecordOutcome(false, "not-playing");
		}
		if (stageObjectSystem == null || stageObjectSystem.getAll().isEmpty()) {
			return new RecordOutcome(false, "no-stage-object");
		}

		double timeSeconds = editor.getClock().getCurrentTimeSeconds();
		if (toolbarState != null) {
			double bpm = timeline.getBpm();
			var snap = SnapSystem.snapWithGuides(
				timeSeconds,
				timeline,
				toolbarState.isSnapToGrid(),
				0.25,
				toolbarState.isSnapToBeat(),
				bpm,
				false,
				null
			);
			timeSeconds = snap.timeSeconds();
		}

		for (TimelineAnimationEvent existing : timeline.getBlockAnimationEvents()) {
			if (Math.abs(existing.getTimeSeconds() - timeSeconds) < MERGE_EPSILON_SECONDS) {
				return new RecordOutcome(false, "duplicate-time");
			}
		}

		String targetObjectId = resolveTargetObjectId(stageObjectSystem);
		if (targetObjectId == null) {
			return new RecordOutcome(false, "no-stage-object");
		}

		Map<String, Object> params = new HashMap<>();
		params.put("generatedBy", "record-mode");

		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"",
			timeSeconds,
			DEFAULT_DURATION_SECONDS,
			DEFAULT_ANIMATION_TYPE,
			targetObjectId,
			DEFAULT_ENERGY,
			params
		);
		boolean written = TimelineDraftWriter.writeEvent(
			timeline,
			com.beatblock.timeline.Timeline.TRACK_ID_ANIMATION_BLOCK,
			event,
			TimelineEventOrigin.MANUAL
		);
		if (!written) {
			return new RecordOutcome(false, "write-failed");
		}
		timeline.sortAll();
		editor.syncClockDuration();
		return new RecordOutcome(true, "ok");
	}

	private static @Nullable String resolveTargetObjectId(StageObjectSystem system) {
		StageObject first = null;
		for (StageObject obj : system.getAll()) {
			if (obj != null) {
				first = obj;
				break;
			}
		}
		return first != null ? first.getId() : null;
	}
}
