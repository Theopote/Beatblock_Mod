package com.beatblock.audio;

/**
 * 单次音频分析的可选参数。
 */
public record AnalysisOptions(boolean useDemucs) {

	public static AnalysisOptions withDemucs(boolean useDemucs) {
		return new AnalysisOptions(useDemucs);
	}
}
