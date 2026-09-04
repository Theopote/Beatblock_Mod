package com.beatblock.automap.choreography.grammar;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 从 {@link Timeline} 特征轨与可选 Beat Grid 构建 {@link PhraseTriggerContext}。 */
public final class PhraseTriggerContextFactory {

	private PhraseTriggerContextFactory() {}

	public static PhraseTriggerContext fromTimeline(Timeline timeline) {
		return fromTimeline(timeline, List.of());
	}

	public static PhraseTriggerContext fromTimeline(Timeline timeline, @Nullable List<Double> beatTimes) {
		if (timeline == null) return PhraseTriggerContext.empty();
		List<FeatureEventRef> events = new ArrayList<>();
		for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
			if (entry.getValue() == null) continue;
			String trackKey = entry.getKey();
			for (FeatureEvent event : entry.getValue().getEvents()) {
				if (event == null) continue;
				events.add(new FeatureEventRef(event.getTimeSeconds(), trackKey, event.getEnergy()));
			}
		}
		return new PhraseTriggerContext(events, beatTimes != null ? beatTimes : List.of());
	}
}
