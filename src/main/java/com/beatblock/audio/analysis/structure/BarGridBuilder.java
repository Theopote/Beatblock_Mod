package com.beatblock.audio.analysis.structure;

import com.beatblock.audio.analysis.BeatGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Beat / Bar 网格构建。
 */
public final class BarGridBuilder {

	private BarGridBuilder() {}

	public record BarSpan(double startSeconds, double endSeconds, int barIndex) {}

	public static List<BarSpan> build(BeatGrid grid, double durationSeconds) {
		List<BarSpan> bars = new ArrayList<>();
		if (grid == null || grid.getBpm() <= 0 || durationSeconds <= 0) {
			bars.add(new BarSpan(0, durationSeconds, 0));
			return bars;
		}
		double beatDur = grid.getBeatDuration();
		double barDur = beatDur * 4.0;
		double firstBar = snapToGrid(0, grid.getFirstBeatTime(), barDur);
		int index = 0;
		for (double start = firstBar; start < durationSeconds - 1e-3; start += barDur) {
			double end = Math.min(durationSeconds, start + barDur);
			if (end - start < beatDur * 0.5) break;
			bars.add(new BarSpan(start, end, index++));
		}
		if (bars.isEmpty()) {
			bars.add(new BarSpan(0, durationSeconds, 0));
		}
		return bars;
	}

	public static double barDuration(BeatGrid grid) {
		if (grid == null || grid.getBpm() <= 0) return 2.0;
		return grid.getBeatDuration() * 4.0;
	}

	public static double snapToBar(double timeSeconds, BeatGrid grid) {
		if (grid == null || grid.getBpm() <= 0) return timeSeconds;
		double barDur = barDuration(grid);
		double origin = grid.getFirstBeatTime();
		return snapToGrid(timeSeconds, origin, barDur);
	}

	private static double snapToGrid(double timeSeconds, double origin, double step) {
		if (step <= 0) return timeSeconds;
		double offset = timeSeconds - origin;
		return origin + Math.round(offset / step) * step;
	}
}
