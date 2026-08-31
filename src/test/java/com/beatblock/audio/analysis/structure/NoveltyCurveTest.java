package com.beatblock.audio.analysis.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoveltyCurveTest {

	@Test
	void peaksAtFeatureChange() {
		List<StructureFeatureFrame> frames = List.of(
			frame(0, 0.2f),
			frame(1, 0.2f),
			frame(2, 0.9f),
			frame(3, 0.9f),
			frame(4, 0.2f)
		);
		double[] novelty = NoveltyCurve.compute(frames);
		double[] smoothed = NoveltyCurve.smooth(novelty, frames, 0.5);

		assertTrue(smoothed[2] > smoothed[1]);
		assertTrue(smoothed[4] > smoothed[3]);
	}

	private static StructureFeatureFrame frame(double time, float energy) {
		return new StructureFeatureFrame(time, energy, 0.3f, 0.3f, 0.3f, 0.2f, 0.1f);
	}
}
