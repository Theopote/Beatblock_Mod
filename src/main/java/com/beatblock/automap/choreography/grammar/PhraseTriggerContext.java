package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyPlan;

import java.util.List;

/** {@link PhraseTriggerResolver} 输入：计划内可触发的特征事件列表及有效时间窗。 */
public record PhraseTriggerContext(
	List<FeatureEventRef> featureEvents,
	double startSeconds,
	double endSeconds
) {

	public PhraseTriggerContext {
		featureEvents = featureEvents != null ? List.copyOf(featureEvents) : List.of();
	}

	/** 不限时间窗；用于测试或尚未绑定段落时。 */
	public PhraseTriggerContext(List<FeatureEventRef> featureEvents) {
		this(featureEvents, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	public static PhraseTriggerContext empty() {
		return new PhraseTriggerContext(List.of());
	}

	/** 将全局特征事件上下文收窄到指定段落的时间范围。 */
	public static PhraseTriggerContext forSection(
		PhraseTriggerContext global,
		ChoreographyPlan.SectionPlan section
	) {
		PhraseTriggerContext resolved = global != null ? global : empty();
		if (section == null) return resolved;
		return new PhraseTriggerContext(
			resolved.featureEvents(),
			section.startSeconds(),
			section.endSeconds()
		);
	}

	public boolean containsTime(double timeSeconds) {
		return timeSeconds >= startSeconds && timeSeconds < endSeconds;
	}
}
