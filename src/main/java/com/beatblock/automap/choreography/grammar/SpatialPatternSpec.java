package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import org.jspecify.annotations.Nullable;

/**
 * 多目标空间编排：映射到 {@link SpatialMotifId} + 轴。
 * <p>
 * {@link SpatialLayoutKind#LEFT_TO_RIGHT} 等价于 {@link SpatialMotifId#CASCADE} + {@link MotifAxis#X}。
 */
public record SpatialPatternSpec(
	SpatialMotifId pattern,
	MotifAxis axis,
	@Nullable SpatialLayoutKind layoutKind
) {
	public SpatialPatternSpec {
		pattern = pattern != null ? pattern : SpatialMotifId.CASCADE;
		axis = axis != null ? axis : MotifAxis.X;
	}

	public static SpatialPatternSpec leftToRight() {
		return new SpatialPatternSpec(SpatialMotifId.CASCADE, MotifAxis.X, SpatialLayoutKind.LEFT_TO_RIGHT);
	}

	public static SpatialPatternSpec of(SpatialMotifId pattern, MotifAxis axis) {
		return new SpatialPatternSpec(pattern, axis, null);
	}

	public SpatialMotifId resolvedPattern() {
		if (layoutKind == SpatialLayoutKind.LEFT_TO_RIGHT) {
			return SpatialMotifId.CASCADE;
		}
		if (layoutKind == SpatialLayoutKind.RIGHT_TO_LEFT) {
			return SpatialMotifId.CASCADE;
		}
		return pattern;
	}

	public MotifAxis resolvedAxis() {
		if (layoutKind == SpatialLayoutKind.LEFT_TO_RIGHT || layoutKind == SpatialLayoutKind.RIGHT_TO_LEFT) {
			return MotifAxis.X;
		}
		return axis;
	}

	public enum SpatialLayoutKind {
		LEFT_TO_RIGHT,
		RIGHT_TO_LEFT
	}
}
