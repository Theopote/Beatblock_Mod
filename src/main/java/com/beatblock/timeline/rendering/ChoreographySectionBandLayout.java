package com.beatblock.timeline.rendering;

/**
 * 标尺区编舞段落 / 乐句色带布局常量。
 */
public final class ChoreographySectionBandLayout {

	/** 段落色带占标尺高度比例（底部）。 */
	public static final float SECTION_BAND_HEIGHT_FRAC = 0.20f;
	/** 乐句色带占标尺高度比例（段落色带上方）。 */
	public static final float PHRASE_BAND_HEIGHT_FRAC = 0.12f;
	/** @deprecated 使用 {@link #SECTION_BAND_HEIGHT_FRAC} + {@link #PHRASE_BAND_HEIGHT_FRAC} */
	@Deprecated
	public static final float BAND_HEIGHT_FRAC = SECTION_BAND_HEIGHT_FRAC + PHRASE_BAND_HEIGHT_FRAC;
	public static final float BOUNDARY_HIT_PX = 6f;

	private ChoreographySectionBandLayout() {}

	public static float choreoBandTop(TimelineLayout layout) {
		float rTop = layout.rulerTop;
		float rBot = layout.rulerTop + layout.rulerHeight;
		return rBot - (rBot - rTop) * (SECTION_BAND_HEIGHT_FRAC + PHRASE_BAND_HEIGHT_FRAC);
	}

	public static float phraseBandTop(TimelineLayout layout) {
		return choreoBandTop(layout);
	}

	public static float phraseBandBottom(TimelineLayout layout) {
		return sectionBandTop(layout);
	}

	public static float sectionBandTop(TimelineLayout layout) {
		float rTop = layout.rulerTop;
		float rBot = layout.rulerTop + layout.rulerHeight;
		return rBot - (rBot - rTop) * SECTION_BAND_HEIGHT_FRAC;
	}

	public static float bandTop(TimelineLayout layout) {
		return sectionBandTop(layout);
	}

	public static boolean isInSectionBand(float mouseY, TimelineLayout layout) {
		float top = sectionBandTop(layout);
		float rBot = layout.rulerTop + layout.rulerHeight;
		return mouseY >= top && mouseY <= rBot;
	}

	public static boolean isInPhraseBand(float mouseY, TimelineLayout layout) {
		return mouseY >= phraseBandTop(layout) && mouseY < phraseBandBottom(layout);
	}

	public static boolean isInBand(float mouseY, TimelineLayout layout) {
		return isInSectionBand(mouseY, layout);
	}
}
