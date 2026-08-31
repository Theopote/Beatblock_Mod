package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only inputs and derived counts shared by validation rules. */
public record TimelineCompileContext(
	Timeline document,
	@Nullable BlockAnimationEngine engine,
	@Nullable BuildLayerManager layerManager,
	List<TimelineAnimationEvent> stageEvents,
	List<TimelineSourceLocation> stageEventLocations,
	int cameraKeyframeCount,
	int buildLayerCount,
	int markerCount
) {
	public TimelineCompileContext {
		Objects.requireNonNull(document, "document");
		stageEvents = List.copyOf(stageEvents != null ? stageEvents : List.of());
		stageEventLocations = List.copyOf(stageEventLocations != null ? stageEventLocations : List.of());
		if (stageEventLocations.size() != stageEvents.size()) {
			throw new IllegalArgumentException("stageEventLocations must align with stageEvents");
		}
	}

	public TimelineCompileContext(Timeline document, @Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager, List<TimelineAnimationEvent> stageEvents,
		int cameraKeyframeCount, int buildLayerCount, int markerCount) {
		this(document, engine, layerManager, stageEvents, fallbackLocations(stageEvents),
			cameraKeyframeCount, buildLayerCount, markerCount);
	}

	public static TimelineCompileContext of(Timeline document, @Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager) {
		List<TimelineAnimationEvent> safeEvents = document.getStageEvents();
		return new TimelineCompileContext(document, engine, layerManager, safeEvents,
			locateStageEvents(document, safeEvents), countCameraKeyframes(document),
			layerManager != null ? layerManager.getAll().size() : 0,
			document.getMarkers().size());
	}

	public int animationEventCount() { return stageEvents.size(); }

	private static List<TimelineSourceLocation> locateStageEvents(
		Timeline document, List<TimelineAnimationEvent> events) {
		record Candidate(String trackId, String clipId, String eventId, double time, int order) {}
		List<Candidate> candidates = new ArrayList<>();
		int order = 0;
		for (Track track : document.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) continue;
			for (var clip : track.getClips()) {
				if (clip == null) continue;
				for (var event : clip.getEvents()) {
					if (event != null && event.getType() == EventType.ANIMATION) {
						candidates.add(new Candidate(track.getId(), clip.getId(), event.getId(),
							event.getTimeSeconds(), order++));
					}
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::time).thenComparingInt(Candidate::order));
		List<TimelineSourceLocation> result = new ArrayList<>(events.size());
		for (int i = 0; i < events.size(); i++) {
			TimelineAnimationEvent event = events.get(i);
			Candidate candidate = i < candidates.size() ? candidates.get(i) : null;
			result.add(new TimelineSourceLocation(candidate != null ? candidate.trackId() : "",
				candidate != null ? candidate.clipId() : "", event != null ? event.getEventId() : "", i));
		}
		return result;
	}

	private static List<TimelineSourceLocation> fallbackLocations(List<TimelineAnimationEvent> events) {
		List<TimelineSourceLocation> result = new ArrayList<>();
		if (events != null) for (int i = 0; i < events.size(); i++) {
			var event = events.get(i);
			result.add(new TimelineSourceLocation("", "", event != null ? event.getEventId() : "", i));
		}
		return result;
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