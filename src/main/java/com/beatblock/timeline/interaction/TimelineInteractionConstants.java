package com.beatblock.timeline.interaction;

/** {@link TimelineInteraction} 共享常量。 */
public final class TimelineInteractionConstants {

	public static final float DRAG_THRESHOLD_PX = 4f;
	public static final float PLAYHEAD_HIT_PX = 6f;
	public static final float LOOP_HANDLE_HIT_PX = 6f;
	public static final float DIVIDER_HIT_PX = 5f;
	public static final float CAMERA_EDGE_HIT_PX = 6f;
	public static final double CAMERA_MIN_CLIP_DURATION = 0.05;

	public static final String POPUP_EVENT_CONTEXT = "##TimelineEventContextPopup";
	public static final String POPUP_EVENT_PROPERTIES = "##TimelineEventPropertiesPopup";
	public static final String POPUP_MARKER_CONTEXT = "##TimelineMarkerContextPopup";
	public static final String POPUP_DELETE_CONFIRM = "##TimelineDeleteConfirmPopup";

	public static final int TIME_INPUT_BUFFER_SIZE = 64;
	public static final int PARAM_INPUT_BUFFER_SIZE = 256;
	public static final int MARKER_NAME_BUFFER_SIZE = 128;

	private TimelineInteractionConstants() {}
}
