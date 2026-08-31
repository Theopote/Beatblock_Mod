package com.beatblock.audio.analysis.structure;

import java.util.List;

/**
 * 特征自相似矩阵与重复度估计。
 */
public final class SelfSimilarityMatrix {

	private SelfSimilarityMatrix() {}

	public static double[][] compute(List<StructureFeatureFrame> frames) {
		int n = frames != null ? frames.size() : 0;
		double[][] matrix = new double[n][n];
		for (int i = 0; i < n; i++) {
			matrix[i][i] = 1.0;
			for (int j = i + 1; j < n; j++) {
				double sim = cosineSimilarity(frames.get(i).toVector(), frames.get(j).toVector());
				matrix[i][j] = sim;
				matrix[j][i] = sim;
			}
		}
		return matrix;
	}

	/**
	 * 段落与更早段落的最高相似度（用于 Verse/Chorus 重复检测）。
	 */
	public static double maxPriorSimilarity(double[][] matrix, int startIndex, int endIndex) {
		if (matrix == null || matrix.length == 0) return 0;
		endIndex = Math.min(endIndex, matrix.length);
		startIndex = Math.max(0, startIndex);
		if (endIndex - startIndex < 2) return 0;

		double best = 0;
		int len = endIndex - startIndex;
		for (int priorStart = 0; priorStart + len <= startIndex; priorStart++) {
			double sum = 0;
			int count = 0;
			for (int offset = 0; offset < len; offset++) {
				int i = startIndex + offset;
				int j = priorStart + offset;
				if (i < matrix.length && j < matrix.length) {
					sum += matrix[i][j];
					count++;
				}
			}
			if (count > 0) {
				best = Math.max(best, sum / count);
			}
		}
		return best;
	}

	/**
	 * Checkerboard 卷积代理：在 lag 处衡量结构变化强度。
	 */
	public static double[] checkerboardNovelty(double[][] matrix, int kernelRadius) {
		int n = matrix.length;
		double[] curve = new double[n];
		if (n == 0) return curve;
		int radius = Math.max(2, kernelRadius);
		for (int i = radius; i < n - radius; i++) {
			double same = 0;
			double cross = 0;
			int sameCount = 0;
			int crossCount = 0;
			for (int di = -radius; di <= radius; di++) {
				for (int dj = -radius; dj <= radius; dj++) {
					int row = i + di;
					int col = i + dj;
					if (row < 0 || col < 0 || row >= n || col >= n) continue;
					if ((di <= 0 && dj <= 0) || (di > 0 && dj > 0)) {
						same += matrix[row][col];
						sameCount++;
					} else {
						cross += matrix[row][col];
						crossCount++;
					}
				}
			}
			double sameAvg = sameCount > 0 ? same / sameCount : 0;
			double crossAvg = crossCount > 0 ? cross / crossCount : 0;
			curve[i] = Math.max(0, sameAvg - crossAvg);
		}
		return curve;
	}

	private static double cosineSimilarity(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		if (normA < 1e-9 || normB < 1e-9) return 0;
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}
}
