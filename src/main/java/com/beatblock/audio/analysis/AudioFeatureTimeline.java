package com.beatblock.audio.analysis;

import java.util.Collections;
import java.util.List;

/**
 * 分析结果时间线：节拍、能量帧、频段、波形帧、BPM、节拍网格。
 * Timeline UI 与 Smart Auto Map 可直接读取。
 */
public final class AudioFeatureTimeline {

	private final double durationSeconds;
	private final List<DetectedBeat> beats;
	private final List<EnergyFrame> energyFrames;
	private final List<FrequencyBands> bands;
	private final WaveformExtractor.WaveformFrame[] waveformFrames;
	private final float bpm;
	private final BeatGrid beatGrid;

	public AudioFeatureTimeline(double durationSeconds,
	                            List<DetectedBeat> beats,
	                            List<EnergyFrame> energyFrames,
	                            List<FrequencyBands> bands,
	                            WaveformExtractor.WaveformFrame[] waveformFrames,
	                            float bpm,
	                            BeatGrid beatGrid) {
		this.durationSeconds = Math.max(0, durationSeconds);
		this.beats = beats != null ? List.copyOf(beats) : List.of();
		this.energyFrames = energyFrames != null ? List.copyOf(energyFrames) : List.of();
		this.bands = bands != null ? List.copyOf(bands) : List.of();
		this.waveformFrames = waveformFrames != null ? waveformFrames.clone() : new WaveformExtractor.WaveformFrame[0];
		this.bpm = Math.max(0f, bpm);
		this.beatGrid = beatGrid;
	}

	public double getDurationSeconds() { return durationSeconds; }
	public List<DetectedBeat> getBeats() { return beats; }
	public List<EnergyFrame> getEnergyFrames() { return energyFrames; }
	public List<FrequencyBands> getBands() { return bands; }
	public WaveformExtractor.WaveformFrame[] getWaveformFrames() { return waveformFrames.clone(); }
	public float getBpm() { return bpm; }
	public BeatGrid getBeatGrid() { return beatGrid; }
}
