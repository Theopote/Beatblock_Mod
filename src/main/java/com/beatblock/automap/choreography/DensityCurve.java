package com.beatblock.automap.choreography;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 全曲密度曲线（0–1），用于 BUILD/DROP 等段落调制事件密度。
 */
public final class DensityCurve {

	private final List<Point> points;

	public record Point(double timeSeconds, double density) {
		public Point {
			density = Math.max(0.0, Math.min(1.0, density));
		}
	}

	private DensityCurve(List<Point> points) {
		this.points = List.copyOf(points);
	}

	public static DensityCurve uniform(double density) {
		double d = Math.max(0.0, Math.min(1.0, density));
		return new DensityCurve(List.of(new Point(0.0, d)));
	}

	public static DensityCurve ofPoints(List<Point> points) {
		if (points == null || points.isEmpty()) return uniform(1.0);
		List<Point> sorted = new ArrayList<>(points);
		sorted.sort(Comparator.comparingDouble(Point::timeSeconds));
		return new DensityCurve(sorted);
	}

	public List<Point> points() {
		return points;
	}

	/** 线性插值采样指定时间的密度。 */
	public double sampleAt(double timeSeconds) {
		if (points.isEmpty()) return 1.0;
		if (timeSeconds <= points.getFirst().timeSeconds()) {
			return points.getFirst().density();
		}
		for (int i = 1; i < points.size(); i++) {
			Point prev = points.get(i - 1);
			Point next = points.get(i);
			if (timeSeconds <= next.timeSeconds()) {
				double span = next.timeSeconds() - prev.timeSeconds();
				if (span <= 1e-9) return next.density();
				double t = (timeSeconds - prev.timeSeconds()) / span;
				return prev.density() + (next.density() - prev.density()) * t;
			}
		}
		return points.getLast().density();
	}
}
