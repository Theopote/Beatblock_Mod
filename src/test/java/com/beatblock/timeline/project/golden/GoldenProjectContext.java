package com.beatblock.timeline.project.golden;

import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;

/** 可序列化为官方 Golden Project 的内存工程快照。 */
public record GoldenProjectContext(
	Timeline timeline,
	BuildLayerManager layers,
	double[] probeTimesSeconds
) {
	public GoldenProjectContext {
		if (probeTimesSeconds == null || probeTimesSeconds.length == 0) {
			probeTimesSeconds = defaultProbeTimes(timeline);
		}
	}

	public static GoldenProjectContext of(Timeline timeline, BuildLayerManager layers) {
		return new GoldenProjectContext(timeline, layers, defaultProbeTimes(timeline));
	}

	private static double[] defaultProbeTimes(Timeline timeline) {
		double duration = timeline != null ? Math.max(0.0, timeline.getDurationSeconds()) : 0.0;
		if (duration <= 0.0) {
			return new double[] {0.0, 15.0, 30.0};
		}
		return new double[] {
			0.0,
			duration * 0.25,
			duration * 0.5,
			duration * 0.75,
			duration
		};
	}
}
