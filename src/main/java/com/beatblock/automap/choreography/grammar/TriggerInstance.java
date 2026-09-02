package com.beatblock.automap.choreography.grammar;

/** 一次已解析的触发时刻（编译期）。 */
public record TriggerInstance(
	double timeSeconds,
	float featureEnergy,
	int sequenceIndex
) {
	public TriggerInstance {
		featureEnergy = Math.max(0f, Math.min(1f, featureEnergy));
		sequenceIndex = Math.max(0, sequenceIndex);
	}
}
