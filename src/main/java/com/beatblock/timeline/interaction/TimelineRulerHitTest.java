package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.editor.TimelineViewState;

import java.util.List;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DIVIDER_HIT_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.LOOP_HANDLE_HIT_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.PLAYHEAD_HIT_PX;

/** 标尺/播放头/分割线命中测试。 */
public final class TimelineRulerHitTest {

	private TimelineRulerHitTest() {}

	public static boolean isMouseOverLoopInHandle(
		float mx,
		float my,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState
	) {
		if (layout == null || viewState == null || toolbarState == null) return false;
		float x = layout.rulerLeft + viewState.timeToScreen(toolbarState.getLoopInSeconds());
		return layout.rulerContains(mx, my) && Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX;
	}

	public static boolean isMouseOverLoopOutHandle(
		float mx,
		float my,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState
	) {
		if (layout == null || viewState == null || toolbarState == null || !toolbarState.hasLoopRange()) return false;
		float x = layout.rulerLeft + viewState.timeToScreen(toolbarState.getLoopOutSeconds());
		return layout.rulerContains(mx, my) && Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX;
	}

	public static int findMarkerIndexAtMouse(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		if (timeline == null || viewState == null || layout == null || !layout.rulerContains(mx, my)) return -1;
		List<TimelineMarker> markers = timeline.getMarkers();
		for (int i = 0; i < markers.size(); i++) {
			TimelineMarker marker = markers.get(i);
			if (marker == null) continue;
			float x = layout.rulerLeft + viewState.timeToScreen(marker.getTimeSeconds());
			if (Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX) return i;
		}
		return -1;
	}

	public static void addMarkerAtTime(Timeline timeline, double timeSeconds) {
		if (timeline == null) return;
		int markerIndex = timeline.getMarkers().size() + 1;
		timeline.addMarker(new TimelineMarker(timeSeconds, "Marker " + markerIndex));
	}

	public static boolean isMouseOverPlayhead(
		float mouseX,
		float mouseY,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineClock clock
	) {
		if (clock == null) return false;
		float playheadX = layout.contentLeft + viewState.timeToScreen(clock.getCurrentTimeSeconds());
		if (mouseX < playheadX - PLAYHEAD_HIT_PX || mouseX > playheadX + PLAYHEAD_HIT_PX) return false;
		return mouseY >= layout.contentTop && mouseY < layout.contentTop + layout.contentHeight;
	}

	public static boolean isMouseOverDivider(float mouseX, float mouseY, TimelineLayout layout) {
		float divX = layout.trackHeaderLeft + layout.trackHeaderWidth;
		if (mouseX < divX - DIVIDER_HIT_PX || mouseX > divX + DIVIDER_HIT_PX) return false;
		return mouseY >= layout.contentTop && mouseY < layout.contentTop + layout.contentHeight;
	}

	public static boolean isMouseOverRulerDivider(float mouseX, float mouseY, TimelineLayout parentLayout) {
		float divX = parentLayout.trackHeaderLeft + parentLayout.trackHeaderWidth;
		if (mouseX < divX - DIVIDER_HIT_PX || mouseX > divX + DIVIDER_HIT_PX) return false;
		return mouseY >= parentLayout.rulerTop && mouseY < parentLayout.rulerTop + parentLayout.rulerHeight;
	}
}
