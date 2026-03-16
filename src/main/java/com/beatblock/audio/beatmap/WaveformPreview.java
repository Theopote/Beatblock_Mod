package com.beatblock.audio.beatmap;

/** 波形预览数据（用于 UI 绘制）。 */
public record WaveformPreview(
	int     samplesPerSecond,
	float[] data             // 归一化振幅 0~1
) {}

