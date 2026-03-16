package com.beatblock.audio.beatmap;

/** 一个音乐段落。 */
public record MusicSection(
	long         startMs,
	long         endMs,
	SectionLabel label,
	float        energyMean
) {
	public long durationMs() { return endMs - startMs; }
}

