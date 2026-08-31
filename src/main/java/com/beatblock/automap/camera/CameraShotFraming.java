package com.beatblock.automap.camera;

/**
 * 取景范围：影响默认轨道半径与推拉距离。
 */
public enum CameraShotFraming {
	WIDE,
	MEDIUM,
	CLOSE,
	OVERVIEW;

	public double orbitRadiusBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 14.0;
			case MEDIUM -> 9.0;
			case CLOSE -> 6.0;
		};
	}

	public double dollyReachBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 8.0;
			case MEDIUM -> 5.5;
			case CLOSE -> 3.5;
		};
	}

	public double orbitHeightBlocks() {
		return switch (this) {
			case WIDE, OVERVIEW -> 4.0;
			case MEDIUM -> 3.0;
			case CLOSE -> 2.0;
		};
	}
}
