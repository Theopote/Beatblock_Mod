package com.beatblock.automap.camera;

/**
 * Lightweight camera angle intent for architecture / showcase framing.
 * Applied when compiling {@link CameraShot} → Timeline (azimuth + pitch bias).
 */
public enum CameraShotAngle {
	FRONT,
	FRONT_THREE_QUARTER,
	SIDE,
	REAR_THREE_QUARTER,
	TOP,
	LOW,
	HIGH;

	/** Horizontal orbit offset from the legacy south (+Z) default, degrees. */
	public double azimuthDeg() {
		return switch (this) {
			case FRONT, TOP, LOW, HIGH -> 0.0;
			case FRONT_THREE_QUARTER -> 45.0;
			case SIDE -> 90.0;
			case REAR_THREE_QUARTER -> 135.0;
		};
	}

	/** Pitch used for the shot (overrides framing pitch when not {@link #inheritFramingPitch()}). */
	public double resolvePitchDeg(double framingPitchDeg) {
		return switch (this) {
			case FRONT, FRONT_THREE_QUARTER, SIDE, REAR_THREE_QUARTER -> framingPitchDeg;
			case TOP -> -55.0;
			case LOW -> 12.0;
			case HIGH -> -28.0;
		};
	}

	public boolean inheritFramingPitch() {
		return switch (this) {
			case FRONT, FRONT_THREE_QUARTER, SIDE, REAR_THREE_QUARTER -> true;
			case TOP, LOW, HIGH -> false;
		};
	}

	/** Extra eye-height scale (TOP sits higher). */
	public double heightScale() {
		return this == TOP ? 1.85 : 1.0;
	}

	public double distanceScale() {
		return this == TOP ? 0.55 : 1.0;
	}
}
