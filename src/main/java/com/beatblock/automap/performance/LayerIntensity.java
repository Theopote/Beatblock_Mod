package com.beatblock.automap.performance;

/**
 * 编舞层强度：控制 Accent / Phrase / Hero / Build / VFX 保留比例与语义。
 * {@link #PRIMARY} 表示该层是作品主轴（如 Build Reveal），不是普通 Phrase 动画。
 */
public enum LayerIntensity {
	OFF,
	VERY_LOW,
	LOW,
	MEDIUM,
	HIGH,
	PRIMARY;

	public boolean isOff() {
		return this == OFF;
	}

	public boolean isPrimary() {
		return this == PRIMARY;
	}

	public boolean isEnabled() {
		return this != OFF;
	}

	/**
	 * 事件保留比例（按能量排序取头部）。PRIMARY 对动画 Phrase 视为极低，主轴走专用车道。
	 */
	public double keepRatio() {
		return switch (this) {
			case OFF -> 0.0;
			case VERY_LOW -> 0.15;
			case LOW -> 0.35;
			case MEDIUM -> 0.65;
			case HIGH -> 1.0;
			case PRIMARY -> 0.10;
		};
	}

	/** 映射到现有 {@link com.beatblock.automap.choreography.ChoreographyLayerProfile} 兼容档。 */
	public boolean includeAsAccentLane() {
		return isEnabled();
	}

	public boolean includeAsPhraseLane() {
		return this == MEDIUM || this == HIGH || this == PRIMARY;
	}

	public boolean includeAsHeroLane() {
		return this == HIGH || this == PRIMARY || this == MEDIUM;
	}
}
