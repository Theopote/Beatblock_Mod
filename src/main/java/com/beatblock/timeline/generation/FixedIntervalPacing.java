package com.beatblock.timeline.generation;

import java.util.ArrayList;
import java.util.List;

final class FixedIntervalPacing implements PacingStrategy {

	static final FixedIntervalPacing INSTANCE = new FixedIntervalPacing();

	private FixedIntervalPacing() {}

	@Override
	public List<Double> computeTimestamps(PacingRequest request) {
		if (request == null || request.slotCount() <= 0) return List.of();
		List<Double> out = new ArrayList<>(request.slotCount());
		double t = request.anchorTimeSeconds();
		if (request.startImmediately()) {
			out.add(t);
		}
		while (out.size() < request.slotCount()) {
			t += request.fixedIntervalSeconds();
			out.add(t);
		}
		return out;
	}
}
