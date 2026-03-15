package com.beatblock.audio.analysis;

import java.util.List;

/**
 * BPM 估计：基于检测到的节拍时间间隔做自相关或间隔直方图，取主峰对应周期。
 * 简化实现：用相邻 beat 间隔中位数推算 BPM。
 */
public final class BPMDetector {

	/**
	 * 从已检测的节拍列表估计 BPM。
	 *
	 * @param beats 按时间排序的节拍
	 * @return 估计的 BPM，若无足够节拍则返回 120
	 */
	public static float estimateBPM(List<DetectedBeat> beats) {
		if (beats == null || beats.size() < 3) return 120f;
		List<Double> intervals = new java.util.ArrayList<>();
		for (int i = 1; i < beats.size(); i++) {
			double dt = beats.get(i).getTimeSeconds() - beats.get(i - 1).getTimeSeconds();
			if (dt > 0.2 && dt < 2.0) intervals.add(dt); // 过滤异常间隔
		}
		if (intervals.isEmpty()) return 120f;
		intervals.sort(Double::compareTo);
		double medianInterval = intervals.get(intervals.size() / 2);
		if (medianInterval <= 0) return 120f;
		float bpm = (float) (60.0 / medianInterval);
		if (bpm < 60) bpm *= 2; // 可能是半拍
		if (bpm > 200) bpm /= 2;
		return Math.max(60f, Math.min(200f, bpm));
	}
}
