package com.beatblock.automap.camera;

/** 摄像机验收规则 id（与 {@link com.beatblock.timeline.playback.TimelineValidator} 对齐）。 */
public final class CameraValidationRules {

	public static final String MISSING_CAMERA_SUBJECT = "missing_camera_subject";
	public static final String MISSING_CAMERA_LOOK_AT = "missing_camera_look_at";
	public static final String MISSING_CAMERA_BUILD_LAYER = "missing_camera_build_layer";
	public static final String INVALID_CAMERA_FRAMING = "invalid_camera_framing";
	public static final String UNSUPPORTED_CAMERA_TRANSITION = "unsupported_camera_transition";

	private CameraValidationRules() {}
}
