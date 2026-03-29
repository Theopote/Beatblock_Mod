package com.beatblock.timeline.camera;

import com.beatblock.timeline.Timeline;

/**
 * 摄像机片段是否显示路径（关键帧与折线），存于 {@link Timeline} metadata。
 */
public final class CameraPathMetadata {

	private static final String PREFIX = "cameraPathVisible_";

	private CameraPathMetadata() {}

	public static boolean isPathVisible(Timeline timeline, String clipId) {
		if (timeline == null || clipId == null || clipId.isBlank()) return true;
		Object v = timeline.getMetadata(PREFIX + clipId);
		if (v == null) return true;
		String s = String.valueOf(v).trim().toLowerCase();
		return !"0".equals(s) && !"false".equals(s) && !"no".equals(s);
	}

	public static void setPathVisible(Timeline timeline, String clipId, boolean visible) {
		if (timeline == null || clipId == null || clipId.isBlank()) return;
		timeline.setMetadata(PREFIX + clipId, visible ? "1" : "0");
	}
}
