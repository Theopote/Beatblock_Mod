package com.beatblock.ui.properties;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
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

	public static boolean isCameraTrack(Track track) {
		return track != null && Timeline.TRACK_ID_CAMERA.equals(track.getId());
	}
}
