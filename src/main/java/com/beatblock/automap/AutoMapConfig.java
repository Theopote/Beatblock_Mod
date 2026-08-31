package com.beatblock.automap;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Auto Map 配置：规则列表 + 能量映射参数 + 可选 per-feature 目标映射。
 * 能量可映射到：高度、速度、粒子数、旋转、缩放等。
 */
public final class AutoMapConfig {

	private final List<AutoMapRule> rules;
	private final float defaultHeightMultiplier;
	private final double minGapSeconds;
	private final Map<String, String> targetByNormalizedFeature;

	public AutoMapConfig(List<AutoMapRule> rules, float defaultHeightMultiplier, double minGapSeconds) {
		this(rules, defaultHeightMultiplier, minGapSeconds, Map.of());
	}

	public AutoMapConfig(
		List<AutoMapRule> rules,
		float defaultHeightMultiplier,
		double minGapSeconds,
		Map<String, String> targetByNormalizedFeature
	) {
		this.rules = new ArrayList<>(rules != null ? rules : List.of());
		this.defaultHeightMultiplier = defaultHeightMultiplier;
		this.minGapSeconds = Math.max(0, minGapSeconds);
		this.targetByNormalizedFeature = copyTargetMap(targetByNormalizedFeature);
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

	public Map<String, String> getTargetByNormalizedFeature() {
		return Collections.unmodifiableMap(targetByNormalizedFeature);
	}

	/**
	 * 解析候选事件的目标舞台对象 id（优先级：rule 专属 &gt; config per-feature &gt; fallback）。
	 */
	public String resolveTargetObjectId(
		AutoMapRule rule,
		String normalizedFeatureKey,
		String fallbackTargetId
	) {
		if (rule != null && rule.getTargetObjectId() != null) {
			return rule.getTargetObjectId();
		}
		if (normalizedFeatureKey != null) {
			String mapped = targetByNormalizedFeature.get(normalizedFeatureKey);
			if (mapped != null && !mapped.isBlank()) {
				return mapped;
			}
		}
		return fallbackTargetId != null ? fallbackTargetId : "";
	}

	public static Builder builder() {
		return new Builder();
	}

	/** 默认配置：低→bounce、中→slide、高→pulse，与设计一致。 */
	public static AutoMapConfig createDefault() {
		return builder()
			.minGapSeconds(0.08)
			.defaultHeightMultiplier(3f)
			.rule(new AutoMapRule("low", 0.15f, "bounce", 0.5, true, 4f, 0.12, null))
			.rule(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f, 0.08, null))
			.rule(new AutoMapRule("high", 0.15f, "pulse", 0.3, false, 1f, 0.04, null))
			.build();
	}

	private static Map<String, String> copyTargetMap(@Nullable Map<String, String> source) {
		if (source == null || source.isEmpty()) return Map.of();
		Map<String, String> copy = new HashMap<>();
		for (Map.Entry<String, String> e : source.entrySet()) {
			if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) continue;
			copy.put(AutoMapGenerator.normalizeFeatureKey(e.getKey()), e.getValue().trim());
		}
		return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
	}

	public static final class Builder {
		private final List<AutoMapRule> rules = new ArrayList<>();
		private float defaultHeightMultiplier = 3f;
		private double minGapSeconds = 0.08;
		private final Map<String, String> targetByNormalizedFeature = new HashMap<>();

		public Builder rule(AutoMapRule rule) {
			if (rule != null) rules.add(rule);
			return this;
		}

		public Builder defaultHeightMultiplier(float value) {
			this.defaultHeightMultiplier = value;
			return this;
		}

		public Builder minGapSeconds(double value) {
			this.minGapSeconds = value;
			return this;
		}

		public Builder targetForFeature(String normalizedFeatureKey, String targetObjectId) {
			if (normalizedFeatureKey == null || targetObjectId == null || targetObjectId.isBlank()) {
				return this;
			}
			targetByNormalizedFeature.put(
				AutoMapGenerator.normalizeFeatureKey(normalizedFeatureKey),
				targetObjectId.trim()
			);
			return this;
		}

		public AutoMapConfig build() {
			return new AutoMapConfig(rules, defaultHeightMultiplier, minGapSeconds, targetByNormalizedFeature);
		}
	}
}
