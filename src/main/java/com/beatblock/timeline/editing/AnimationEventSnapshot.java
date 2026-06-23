package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 动画事件 + 片段时间范围的不可变快照，用于 Undo/Redo。
 */
public record AnimationEventSnapshot(
	double timeSeconds,
	Map<String, Object> parameters,
	double clipStartSeconds,
	double clipEndSeconds
) {

	public AnimationEventSnapshot {
		parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
	}

	public static AnimationEventSnapshot capture(TimelineEvent event, Clip clip) {
		return new AnimationEventSnapshot(
			event.getTimeSeconds(),
			new HashMap<>(event.getParameters()),
			clip.getStartTimeSeconds(),
			clip.getEndTimeSeconds()
		);
	}

	public void applyTo(TimelineEvent event, Clip clip) {
		event.setTimeSeconds(timeSeconds);
		for (String key : new ArrayList<>(event.getParameters().keySet())) {
			event.removeParameter(key);
		}
		for (Map.Entry<String, Object> entry : parameters.entrySet()) {
			event.setParameter(entry.getKey(), entry.getValue());
		}
		clip.setStartTimeSeconds(clipStartSeconds);
		clip.setEndTimeSeconds(clipEndSeconds);
	}
}
