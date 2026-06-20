package com.beatblock.timeline.generation;

import java.util.ArrayList;
import java.util.List;

final class BeatGridPacing implements PacingStrategy {

	static final BeatGridPacing INSTANCE = new BeatGridPacing();

	private BeatGridPacing() {}

	@Override
	public List<Double> computeTimestamps(PacingRequest request) {
		if (request == null || request.slotCount() <= 0) return List.of();
		List<Double> out = new ArrayList<>(request.slotCount());
		double cursor = request.anchorTimeSeconds();
		if (request.startImmediately()) {
			out.add(cursor);
		}
		int beatIndex = firstBeatIndexAtOrAfter(cursor, request.referenceBeatTimesSeconds());
		while (out.size() < request.slotCount()) {
			if (beatIndex < request.referenceBeatTimesSeconds().length) {
				double beat = request.referenceBeatTimesSeconds()[beatIndex++];
				if (out.isEmpty() || beat > out.getLast() + 1e-6) {
					out.add(beat);
					cursor = beat;
					continue;
				}
			}
			cursor += request.fixedIntervalSeconds();
			out.add(cursor);
		}
		return out;
	}

	static int firstBeatIndexAtOrAfter(double timeSeconds, double[] beatTimes) {
		if (beatTimes == null || beatTimes.length == 0) return 0;
		int lo = 0;
		int hi = beatTimes.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (beatTimes[mid] < timeSeconds - 1e-6) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}
}
