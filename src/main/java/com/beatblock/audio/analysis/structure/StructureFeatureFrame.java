package com.beatblock.audio.analysis.structure;

/**
 * 结构分析用特征帧：能量、频段、onset 密度与谱变化代理。
 */
public record StructureFeatureFrame(
	double timeSeconds,
	float energy,
	float lowBand,
	float midBand,
	float highBand,
	float onsetDensity,
	float spectralFlux
) {

	float[] toVector() {
		return new float[] { energy, lowBand, midBand, highBand, onsetDensity, spectralFlux };
	}
}
