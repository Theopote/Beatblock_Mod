package com.beatblock.audio.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 节拍网格：BPM + 细分拍时间点（1/4、1/8、1/16），用于时间线对齐与自动编排。
 */
public final class BeatGrid {

	private final float bpm;
	private final double firstBeatTime;
	private final List<Double> beatTimes;

	public BeatGrid(float bpm, double durationSeconds, double firstBeatTime) {
		this.bpm = Math.max(1f, bpm);
		this.firstBeatTime = firstBeatTime;
		this.beatTimes = new ArrayList<>();
		double beatDuration = 60.0 / this.bpm;
		for (double t = firstBeatTime; t <= durationSeconds + 0.001; t += beatDuration) {
			beatTimes.add(t);
		}
	}

	public BeatGrid(float bpm, double durationSeconds) {
		this(bpm, durationSeconds, 0);
	}

	public float getBpm() { return bpm; }
	public double getFirstBeatTime() { return firstBeatTime; }
	public List<Double> getBeatTimes() { return List.copyOf(beatTimes); }

	/** 1/4 拍间隔（秒） */
	public double getBeatDuration() { return 60.0 / bpm; }
	/** 1/8 拍间隔 */
	public double getEighthDuration() { return getBeatDuration() / 2; }
	/** 1/16 拍间隔 */
	public double getSixteenthDuration() { return getBeatDuration() / 4; }
}
