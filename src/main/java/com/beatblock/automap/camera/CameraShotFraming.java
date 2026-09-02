package com.beatblock.automap.camera;

/**
 * 导演取景意图：语义档位，几何由 {@link CameraFramingEngine} 根据 {@link StageBounds} 求解。
 */
public enum CameraShotFraming {
	WIDE,
	MEDIUM,
	CLOSE,
	OVERVIEW;

	/** 主体在垂直 FOV 中占据的大致比例（越大越近）。 */
	public double verticalFillRatio() {
		return switch (this) {
			case CLOSE -> 0.68;
			case MEDIUM -> 0.46;
			case WIDE -> 0.36;
			case OVERVIEW -> 0.28;
		};
	}

	/** 在 fill 基础上的额外留白（远景更大）。 */
	public double marginMultiplier() {
		return switch (this) {
			case CLOSE -> 1.0;
			case MEDIUM -> 1.05;
			case WIDE -> 1.15;
			case OVERVIEW -> 1.35;
		};
	}

	public double defaultPitchDeg() {
		return switch (this) {
			case CLOSE -> -8.0;
			case MEDIUM -> -10.0;
			case WIDE -> -12.0;
			case OVERVIEW -> -14.0;
		};
	}

	public double dollyReachFactor() {
		return switch (this) {
			case CLOSE -> 0.35;
			case MEDIUM -> 0.45;
			case WIDE -> 0.50;
			case OVERVIEW -> 0.55;
		};
	}

	public double minimumDistanceBlocks() {
		return switch (this) {
			case CLOSE -> 4.0;
			case MEDIUM -> 5.0;
			case WIDE -> 6.0;
			case OVERVIEW -> 8.0;
		};
	}

	/**
	 * @deprecated 仅作无 bounds 时的回退；优先使用 {@link CameraFramingEngine}。
	 */
	@Deprecated
	public double orbitRadiusBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 14.0;
			case MEDIUM -> 9.0;
			case CLOSE -> 6.0;
		};
	}

	/**
	 * @deprecated 仅作无 bounds 时的回退；优先使用 {@link CameraFramingEngine}。
	 */
	@Deprecated
	public double dollyReachBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 8.0;
			case MEDIUM -> 5.5;
			case CLOSE -> 3.5;
		};
	}

	/**
	 * @deprecated 仅作无 bounds 时的回退；优先使用 {@link CameraFramingEngine}。
	 */
	@Deprecated
	public double orbitHeightBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 4.0;
			case MEDIUM -> 3.0;
			case CLOSE -> 2.0;
		};
	}
}
