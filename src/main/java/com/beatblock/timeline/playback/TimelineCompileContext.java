package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Read-only inputs and derived counts shared by validation rules. */
public record TimelineCompileContext(
	Timeline document,
	@Nullable BlockAnimationEngine engine,
	@Nullable BuildLayerManager layerManager,
	List<TimelineAnimationEvent> stageEvents,
	int cameraKeyframeCount,
	int buildLayerCount,
	int markerCount
) {
	public TimelineCompileContext {
		Objects.requireNonNull(document, "document");
		stageEvents = List.copyOf(stageEvents != null ? stageEvents : List.of());
	}

	public static TimelineCompileContext of(
		Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		List<TimelineAnimationEvent> events = document.getStageEvents();
		return new TimelineCompileContext(
			document,
			engine,
			layerManager,
			events != null ? events : List.of(),
			countCameraKeyframes(document),
			layerManager != null ? layerManager.getAll().size() : 0,
			document.getMarkers() != null ? document.getMarkers().size() : 0
		);
	}

	public int animationEventCount() {
		return stageEvents.size();
	}

	private static int countCameraKeyframes(Timeline document) {
		Track camera = document.getTrack(Timeline.TRACK_ID_CAMERA);
		if (camera == null) return 0;
		int count = 0;
		for (var clip : camera.getClips()) {
			if (clip == null) continue;
			for (var event : clip.getEvents()) {
				if (event != null && event.getType() == EventType.CAMERA_KEYFRAME) count++;
			}
		}
		return count;
	}
}