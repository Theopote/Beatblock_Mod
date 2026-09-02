package com.beatblock.automap.choreography.grammar;

/** 解析触发器时使用的特征事件快照。 */
public record FeatureEventRef(
	double timeSeconds,
	String featureKey,
	float energy
) {
	public FeatureEventRef {
		featureKey = featureKey != null ? featureKey : "";
		energy = Math.max(0f, Math.min(1f, energy));
	}
}
