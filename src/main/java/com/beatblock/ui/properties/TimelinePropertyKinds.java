package com.beatblock.ui.properties;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import com.beatblock.ui.presenter.EventPropertiesRef;

/**
 * 时间线选中目标与属性适配器之间的类型判定。
 */
public final class TimelinePropertyKinds {

	private TimelinePropertyKinds() {
	}

	public static boolean isAnimationRef(EventPropertiesRef ref) {
		return ref != null && ref.event() != null && ref.event().getType() == EventType.ANIMATION;
	}

	public static boolean isGlobalRef(EventPropertiesRef ref) {
		return ref != null && ref.event() != null && ref.event().getType() == EventType.GLOBAL;
	}

	public static boolean isCameraRef(EventPropertiesRef ref) {
		if (ref == null) {
			return false;
		}
		if (ref.event() == null) {
			return isCameraTrack(ref.track());
		}
		EventType type = ref.event().getType();
		return type == EventType.CAMERA_SEGMENT || type == EventType.CAMERA_KEYFRAME;
	}

	public static boolean isAudioClipRef(EventPropertiesRef ref) {
		return ref != null && ref.event() == null && isAudioTrack(ref.track());
	}

	public static boolean isBuildLayerClipRef(EventPropertiesRef ref) {
		return ref != null
			&& ref.event() == null
			&& ref.clip() != null
			&& BuildLayerTrackSupport.isBuildLayerTrack(ref.track());
	}

	public static boolean isAudioTrack(Track track) {
		return track != null && Timeline.TRACK_ID_AUDIO.equals(track.getId());
	}

	public static boolean isCameraTrack(Track track) {
		return track != null && Timeline.TRACK_ID_CAMERA.equals(track.getId());
	}

	public static boolean isGlobalTrack(Track track) {
		return track != null && Timeline.TRACK_ID_GLOBAL.equals(track.getId());
	}
}
