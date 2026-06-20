package com.beatblock.automap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Smart Auto Map 配置：规则列表 + 能量映射参数。
 * 能量可映射到：高度、速度、粒子数、旋转、缩放等。
 */
public final class AutoMapConfig {

	private final List<AutoMapRule> rules;
	private final float defaultHeightMultiplier;
	private final double minGapSeconds;

	public AutoMapConfig(List<AutoMapRule> rules, float defaultHeightMultiplier, double minGapSeconds) {
		this.rules = new ArrayList<>(rules != null ? rules : List.of());
		this.defaultHeightMultiplier = defaultHeightMultiplier;
		this.minGapSeconds = Math.max(0, minGapSeconds);
	}

	public List<AutoMapRule> getRules() {
		return Collections.unmodifiableList(rules);
	}

	public float getDefaultHeightMultiplier() {
		return defaultHeightMultiplier;
	}

	public double getMinGapSeconds() {
		return minGapSeconds;
	}

	/** 默认配置：低→bounce、中→slide、高→pulse，与设计一致。 */
	public static AutoMapConfig createDefault() {
		List<AutoMapRule> rules = new ArrayList<>();
		rules.add(new AutoMapRule("low", 0.15f, "bounce", 0.5, true, 4f));
		rules.add(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f));
		rules.add(new AutoMapRule("high", 0.15f, "pulse", 0.3, false, 1f));
		return new AutoMapConfig(rules, 3f, 0.08);
	}
}
