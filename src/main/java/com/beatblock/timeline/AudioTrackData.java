package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 音频轨道专用数据：波形 + 开放式命名特征轨道（kick / snare / hihat 等）。
 */
public class AudioTrackData {

	private WaveformData waveform;
	/** key → FeatureTrack，插入顺序即渲染顺序（LinkedHashMap）。 */
	private final Map<String, FeatureTrack> featureTracks = new LinkedHashMap<>();

	/** Demucs 茎波形：key = "drums"/"bass"/"vocals"/"other" → WaveformData */
	private final Map<String, WaveformData> stemWaveforms = new LinkedHashMap<>();

	public WaveformData getWaveform() { return waveform; }
	public void setWaveform(WaveformData waveform) { this.waveform = waveform; }

	public void addFeatureEvent(String key, FeatureEvent event) {
		if (key == null || key.isBlank() || event == null) return;
		featureTracks.computeIfAbsent(key, k -> new FeatureTrack(k, k)).addEvent(event);
	}

	public void addFeatureEvent(String key, String label, FeatureEvent event) {
		if (key == null || key.isBlank() || event == null) return;
		featureTracks.computeIfAbsent(key, k -> new FeatureTrack(k, label)).addEvent(event);
	}

	public FeatureTrack getFeatureTrack(String key) {
		return featureTracks.get(key);
	}

	public Map<String, FeatureTrack> getFeatureTracks() {
		return Collections.unmodifiableMap(featureTracks);
	}

	public Set<String> getFeatureTrackKeys() {
		return Collections.unmodifiableSet(featureTracks.keySet());
	}

	public void clearFeatureTracks() {
		featureTracks.values().forEach(FeatureTrack::clear);
		featureTracks.clear();
	}

	public void clearAll() {
		clearFeatureTracks();
		stemWaveforms.clear();
		waveform = null;
	}

	public boolean hasFeatureTracks() {
		return !featureTracks.isEmpty();
	}

	public void setStemWaveform(String stemKey, WaveformData data) {
		if (stemKey != null && data != null) stemWaveforms.put(stemKey, data);
	}

	public WaveformData getStemWaveform(String stemKey) {
		return stemWaveforms.get(stemKey);
	}

	public Set<String> getStemWaveformKeys() {
		return Collections.unmodifiableSet(stemWaveforms.keySet());
	}

	public boolean hasStemWaveforms() {
		return !stemWaveforms.isEmpty();
	}

	public void clearStemWaveforms() {
		stemWaveforms.clear();
	}
}
