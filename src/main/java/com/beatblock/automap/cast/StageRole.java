package com.beatblock.automap.cast;

/**
 * 舞台角色：导演共享的主角/配角语义，而非频段槽位。
 */
public enum StageRole {
	HERO,
	LEAD,
	SUPPORT,
	ACCENT,
	BACKGROUND;

	public boolean isFocusable() {
		return this == HERO || this == LEAD || this == SUPPORT || this == ACCENT;
	}

	public double defaultProminence() {
		return switch (this) {
			case HERO -> 1.0;
			case LEAD -> 0.85;
			case SUPPORT -> 0.70;
			case ACCENT -> 0.45;
			case BACKGROUND -> 0.25;
		};
	}
}
