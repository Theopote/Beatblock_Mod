package com.beatblock.timeline.rendering;

/**
 * 标尺区编舞段落色带布局常量。
 */
public final class ChoreographySectionBandLayout {

	public static final float BAND_HEIGHT_FRAC = 0.28f;
	public static final float BOUNDARY_HIT_PX = 6f;

	private ChoreographySectionBandLayout() {}

	public static float bandTop(TimelineLayout layout) {
		float rTop = layout.rulerTop;
		float rBot = layout.rulerTop + layout.rulerHeight;
		return rBot - (rBot - rTop) * BAND_HEIGHT_FRAC;
	}

	public static boolean isInBand(float mouseY, TimelineLayout layout) {
		float bandTop = bandTop(layout);
		float rBot = layout.rulerTop + layout.rulerHeight;
		return mouseY >= bandTop && mouseY <= rBot;
	}
}
