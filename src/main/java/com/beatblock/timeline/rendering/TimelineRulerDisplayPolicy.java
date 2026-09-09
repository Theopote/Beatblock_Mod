package com.beatblock.timeline.rendering;

import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.TimelineMarker;

/** Visibility rules for the compact timeline ruler. */
public final class TimelineRulerDisplayPolicy {
	static final float MIN_BAND_LABEL_HEIGHT_PX = 13f;

	private TimelineRulerDisplayPolicy() {}

	public static boolean shouldDrawMarker(TimelineMarker marker, boolean hasChoreographyPlan) {
		if (marker == null) return false;
		return !(hasChoreographyPlan
			&& marker.getType() == MarkerType.SECTION
			&& marker.getOrigin() == MarkerOrigin.AUDIO_ANALYSIS);
	}

	public static boolean shouldDrawBandLabel(float bandHeightPx, float bandWidthPx) {
		return bandHeightPx >= MIN_BAND_LABEL_HEIGHT_PX && bandWidthPx >= 28f;
	}
}
