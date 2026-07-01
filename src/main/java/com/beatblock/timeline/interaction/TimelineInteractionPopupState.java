package com.beatblock.timeline.interaction;

import imgui.type.ImBoolean;
import imgui.type.ImString;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.MARKER_NAME_BUFFER_SIZE;

/** ImGui 弹窗与右键上下文所需的持久状态。 */
public final class TimelineInteractionPopupState {

	public String contextTrackId;
	public String contextClipId;
	public String contextEventId;
	public double contextTimeSeconds;
	public String contextMarkerId;

	public final ImString markerNameBuffer = new ImString(MARKER_NAME_BUFFER_SIZE);
	public final ImBoolean contextCameraShowPath = new ImBoolean(true);
}
