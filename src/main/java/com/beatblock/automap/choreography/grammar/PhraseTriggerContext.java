package com.beatblock.automap.choreography.grammar;

import java.util.List;

/** {@link PhraseTriggerResolver} 输入：计划内可触发的特征事件列表。 */
public record PhraseTriggerContext(List<FeatureEventRef> featureEvents) {

	public PhraseTriggerContext {
		featureEvents = featureEvents != null ? List.copyOf(featureEvents) : List.of();
	}

	public static PhraseTriggerContext empty() {
		return new PhraseTriggerContext(List.of());
	}
}
