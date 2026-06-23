package com.beatblock.timeline.util;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间吸附：靠近 Beat / Grid / Event / Marker / 特征点 / 片段边界时对齐。
 */
public final class SnapSystem {

	public static final double SNAP_THRESHOLD_SECONDS = 0.08;
	private static final double GUIDE_TIME_EPSILON = 1e-6;

	private SnapSystem() {}

	public record SnapResult(double timeSeconds, double[] guideTimes) {
		private static final double[] EMPTY = new double[0];

		public static SnapResult unchanged(double timeSeconds) {
			return new SnapResult(timeSeconds, EMPTY);
		}
	}

	/**
	 * 若 time 靠近某个吸附点则返回吸附后时间与对齐参考线时间，否则返回原值。
	 */
	public static SnapResult snapWithGuides(double timeSeconds, Timeline timeline,
			boolean snapToGrid, double gridStepSeconds,
			boolean snapToBeat, double bpm,
			boolean magnetSnap, String excludeEventId) {
		if (timeline == null) return SnapResult.unchanged(timeSeconds);

		MutableSnapState state = new MutableSnapState(timeSeconds, SNAP_THRESHOLD_SECONDS);
		if (snapToGrid && gridStepSeconds > 0) {
			double grid = Math.round(timeSeconds / gridStepSeconds) * gridStepSeconds;
			state.consider(timeSeconds, grid);
		}
		if (snapToBeat && bpm > 0) {
			double beatDuration = 60.0 / bpm;
			double beat = Math.round(timeSeconds / beatDuration) * beatDuration;
			state.consider(timeSeconds, beat);
		}
		if (magnetSnap) {
			for (Track track : timeline.getTracks()) {
				for (Clip clip : track.getClips()) {
					if (clip == null) continue;
					state.consider(timeSeconds, clip.getStartTimeSeconds());
					state.consider(timeSeconds, clip.getEndTimeSeconds());
					for (TimelineEvent e : clip.getEvents()) {
						if (excludeEventId != null && excludeEventId.equals(e.getId())) continue;
						state.consider(timeSeconds, e.getTimeSeconds());
					}
				}
			}
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker != null) {
					state.consider(timeSeconds, marker.getTimeSeconds());
				}
			}
			for (FeatureTrack ft : timeline.getFeatureTracks().values()) {
				if (ft == null) continue;
				for (FeatureEvent fe : ft.getEvents()) {
					state.consider(timeSeconds, fe.getTimeSeconds());
				}
			}
		}

		if (state.bestDist >= SNAP_THRESHOLD_SECONDS) {
			return SnapResult.unchanged(timeSeconds);
		}
		double[] guides = state.guideTimes.stream()
			.mapToDouble(Double::doubleValue)
			.distinct()
			.sorted()
			.toArray();
		return new SnapResult(state.best, guides);
	}

	/**
	 * 若 time 靠近某个吸附点则返回吸附后时间，否则返回原值。
	 */
	public static double snap(double timeSeconds, Timeline timeline,
			boolean snapToGrid, double gridStepSeconds,
			boolean snapToBeat, double bpm,
			boolean magnetSnap, String excludeEventId) {
		return snapWithGuides(timeSeconds, timeline, snapToGrid, gridStepSeconds,
			snapToBeat, bpm, magnetSnap, excludeEventId).timeSeconds();
	}

	public static double snap(double timeSeconds, Timeline timeline,
			boolean snapToGrid, double gridStepSeconds,
			boolean snapToBeat, double bpm) {
		return snap(timeSeconds, timeline, snapToGrid, gridStepSeconds, snapToBeat, bpm, false, null);
	}

	private static final class MutableSnapState {
		double best;
		double bestDist;
		final List<Double> guideTimes = new ArrayList<>();

		MutableSnapState(double initialTime, double initialDist) {
			best = initialTime;
			bestDist = initialDist;
		}

		void consider(double timeSeconds, double target) {
			double d = Math.abs(timeSeconds - target);
			if (d < bestDist) {
				bestDist = d;
				best = target;
				guideTimes.clear();
				guideTimes.add(target);
			} else if (d < SNAP_THRESHOLD_SECONDS && Math.abs(target - best) < GUIDE_TIME_EPSILON) {
				if (!guideTimes.contains(target)) {
					guideTimes.add(target);
				}
			}
		}
	}
}
