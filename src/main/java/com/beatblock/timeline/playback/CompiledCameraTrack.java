package com.beatblock.timeline.playback;

import com.beatblock.timeline.EventType;

import java.util.List;
import java.util.Map;

/** Immutable, pre-sorted camera data consumed during formal playback. */
public record CompiledCameraTrack(List<CameraClip> clips) {

	public CompiledCameraTrack {
		clips = List.copyOf(clips);
	}

	public boolean isEmpty() {
		return clips.isEmpty();
	}

	public record CameraClip(double startTimeSeconds, double endTimeSeconds, List<CameraEvent> events) {
		public CameraClip {
			events = List.copyOf(events);
		}
	}

	public record CameraEvent(String id, double timeSeconds, EventType type, Map<String, Object> parameters) {
		public CameraEvent {
			id = id != null ? id : "";
			type = type != null ? type : EventType.ANIMATION;
			parameters = Map.copyOf(parameters);
		}
	}
}
