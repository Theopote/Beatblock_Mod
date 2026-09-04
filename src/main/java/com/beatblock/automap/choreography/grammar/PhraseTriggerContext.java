package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyBudget;
import com.beatblock.automap.choreography.ChoreographyPlan;

import java.util.ArrayList;
import java.util.List;

/** {@link PhraseTriggerResolver} 输入：特征事件、Beat Grid 与有效时间窗。 */
public record PhraseTriggerContext(
	List<FeatureEventRef> featureEvents,
	List<Double> beatTimes,
	double startSeconds,
	double endSeconds
) {

	public PhraseTriggerContext {
		featureEvents = featureEvents != null ? List.copyOf(featureEvents) : List.of();
		beatTimes = beatTimes != null ? List.copyOf(beatTimes) : List.of();
	}

	/** 不限时间窗；用于测试或尚未绑定段落时。 */
	public PhraseTriggerContext(List<FeatureEventRef> featureEvents) {
		this(featureEvents, List.of(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	public PhraseTriggerContext(List<FeatureEventRef> featureEvents, List<Double> beatTimes) {
		this(featureEvents, beatTimes, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	public static PhraseTriggerContext empty() {
		return new PhraseTriggerContext(List.of(), List.of());
	}

	/** 将全局触发上下文收窄到指定段落的时间范围（保留 beat grid）。 */
	public static PhraseTriggerContext forSection(
		PhraseTriggerContext global,
		ChoreographyPlan.SectionPlan section
	) {
		PhraseTriggerContext resolved = global != null ? global : empty();
		if (section == null) return resolved;
		return new PhraseTriggerContext(
			resolved.featureEvents(),
			resolved.beatTimes(),
			section.startSeconds(),
			section.endSeconds()
		);
	}

	public boolean containsTime(double timeSeconds) {
		return timeSeconds >= startSeconds && timeSeconds < endSeconds;
	}

	/** 有效时间窗内的拍点；无 beatTimes 时按默认拍长合成网格。 */
	public List<Double> beatsInActiveRange() {
		List<Double> source = beatTimes;
		if (source.isEmpty()) {
			source = syntheticBeatGrid();
		}
		List<Double> out = new ArrayList<>();
		for (Double beat : source) {
			if (beat != null && containsTime(beat)) {
				out.add(beat);
			}
		}
		return out;
	}

	private List<Double> syntheticBeatGrid() {
		double start = Double.isFinite(startSeconds) ? startSeconds : 0.0;
		double end = Double.isFinite(endSeconds) ? endSeconds : start;
		if (!(end > start)) {
			double maxFeature = 0.0;
			for (FeatureEventRef event : featureEvents) {
				maxFeature = Math.max(maxFeature, event.timeSeconds());
			}
			end = Math.max(start + ChoreographyBudget.DEFAULT_BEAT_SECONDS, maxFeature + 1e-6);
		}
		List<Double> out = new ArrayList<>();
		double step = ChoreographyBudget.DEFAULT_BEAT_SECONDS;
		for (double t = start; t < end; t += step) {
			out.add(t);
		}
		return out;
	}
}
