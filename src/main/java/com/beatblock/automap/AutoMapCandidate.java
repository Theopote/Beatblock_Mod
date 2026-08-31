package com.beatblock.automap;

/**
 * Smart Auto Map 合并排序前的候选动画事件（来自单条特征轨上的单个 {@link com.beatblock.timeline.FeatureEvent}）。
 */
public record AutoMapCandidate(
	double timeSeconds,
	String trackKey,
	String normalizedFeatureKey,
	float energy,
	AutoMapRule rule
) {}
