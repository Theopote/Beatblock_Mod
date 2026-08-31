package com.beatblock.audio.analysis.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfSimilarityMatrixTest {

	@Test
	void detectsRepeatedSegmentsInMatrix() {
		List<StructureFeatureFrame> frames = List.of(
			frame(0, 0.2f, 0.8f, 0.1f, 0.1f),
			frame(1, 0.2f, 0.8f, 0.1f, 0.1f),
			frame(2, 0.8f, 0.1f, 0.1f, 0.8f),
			frame(3, 0.8f, 0.1f, 0.1f, 0.8f),
			frame(4, 0.2f, 0.8f, 0.1f, 0.1f),
			frame(5, 0.2f, 0.8f, 0.1f, 0.1f)
		);
		double[][] matrix = SelfSimilarityMatrix.compute(frames);

		double repetition = SelfSimilarityMatrix.maxPriorSimilarity(matrix, 4, 6);
		assertTrue(repetition > 0.8);
	}

	private static StructureFeatureFrame frame(double time, float energy, float low, float mid, float high) {
		return new StructureFeatureFrame(time, energy, low, mid, high, 0.5f, 0.1f);
	}
}
