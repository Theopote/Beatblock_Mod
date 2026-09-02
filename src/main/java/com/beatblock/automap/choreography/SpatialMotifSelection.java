package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

/**
 * 按音乐段落类型选择默认空间 motif（smart-automap 生成路径）。
 */
public final class SpatialMotifSelection {

	private SpatialMotifSelection() {}

	public static SpatialMotifId forSection(SectionType sectionType) {
		if (sectionType == null) return SpatialMotifId.CASCADE;
		return switch (sectionType) {
			case INTRO, OUTRO -> SpatialMotifId.GATHER;
			case BUILD -> SpatialMotifId.CASCADE;
			case PRE_CHORUS -> SpatialMotifId.SWEEP;
			case DROP -> SpatialMotifId.EXPLODE;
			case CHORUS -> SpatialMotifId.WAVE;
			case VERSE -> SpatialMotifId.ALTERNATE;
			case BRIDGE -> SpatialMotifId.CHASE;
			case BREAK -> SpatialMotifId.RIPPLE;
		};
	}

	public static String defaultPrimitive(SectionType sectionType) {
		if (sectionType == null) return "pulse";
		return switch (sectionType) {
			case DROP -> "jump";
			case BUILD, PRE_CHORUS -> "rise";
			case INTRO, OUTRO -> "pulse";
			case BREAK -> "pulse";
			case BRIDGE -> "slide";
			default -> "pulse";
		};
	}

	public static MotifAxis defaultAxis(SectionType sectionType) {
		if (sectionType == null) return MotifAxis.X;
		return switch (sectionType) {
			case DROP, INTRO, OUTRO, BREAK -> MotifAxis.RADIAL;
			case CHORUS, BRIDGE -> MotifAxis.Z;
			default -> MotifAxis.X;
		};
	}

	public static double defaultPropagationDelay(SectionType sectionType) {
		if (sectionType == null) return 0.06;
		return switch (sectionType) {
			case DROP -> 0.04;
			case BUILD, PRE_CHORUS -> 0.05;
			case CHORUS -> 0.07;
			case BREAK, BRIDGE -> 0.055;
			default -> 0.06;
		};
	}
}
