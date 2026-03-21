package com.beatblock.timeline.util;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/**
 * 时间吸附：靠近 Beat / Grid / Event 时对齐。
 */
public final class SnapSystem {

	public static final double SNAP_THRESHOLD_SECONDS = 0.08;

	/**
	 * 若 time 靠近某个吸附点则返回吸附后时间，否则返回原值。
	 *
	 * @param snapToGrid      是否吸附到时间网格
	 * @param gridStepSeconds 网格步长（秒）
	 * @param snapToBeat      是否吸附到节拍
	 * @param bpm             曲目 BPM，&lt;= 0 时节拍吸附自动跳过
	 * @param magnetSnap      是否吸附到附近其他事件
	 * @param excludeEventId  拖动中的事件 ID，磁吸时跳过自身（可为 null）
	 */
	public static double snap(double timeSeconds, Timeline timeline,
			boolean snapToGrid, double gridStepSeconds,
			boolean snapToBeat, double bpm,
			boolean magnetSnap, String excludeEventId) {
		if (timeline == null) return timeSeconds;
		double best = timeSeconds;
		double bestDist = SNAP_THRESHOLD_SECONDS;
		if (snapToGrid && gridStepSeconds > 0) {
			double grid = Math.round(timeSeconds / gridStepSeconds) * gridStepSeconds;
			double d = Math.abs(timeSeconds - grid);
			if (d < bestDist) {
				bestDist = d;
				best = grid;
			}
		}
		if (snapToBeat && bpm > 0) {
			double beatDuration = 60.0 / bpm;
			double beat = Math.round(timeSeconds / beatDuration) * beatDuration;
			double d = Math.abs(timeSeconds - beat);
			if (d < bestDist) {
				bestDist = d;
				best = beat;
			}
		}
		if (magnetSnap) {
			for (Track track : timeline.getTracks()) {
				for (Clip clip : track.getClips()) {
					for (TimelineEvent e : clip.getEvents()) {
						if (excludeEventId != null && excludeEventId.equals(e.getId())) continue;
						double d = Math.abs(timeSeconds - e.getTimeSeconds());
						if (d < bestDist) {
							bestDist = d;
							best = e.getTimeSeconds();
						}
					}
				}
			}
		}
		return best;
	}

	/**
	 * 重载：不带磁吸（向后兼容）。
	 */
	public static double snap(double timeSeconds, Timeline timeline,
			boolean snapToGrid, double gridStepSeconds,
			boolean snapToBeat, double bpm) {
		return snap(timeSeconds, timeline, snapToGrid, gridStepSeconds, snapToBeat, bpm, false, null);
	}
}
