package com.beatblock.audio.analysis;

import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.FrequencyBand;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 将 Python {@link Beatmap} 适配为 {@link AudioFeatureTimeline}，供 Smart AutoMap 等 Java 消费者使用。
 * <p>
 * 这不是完整 FFT 分析的替代品，而是让 Beatmap 路径在不跑二次 Java dense analyze 时也能生成可用特征。
 */
public final class BeatmapFeatureTimelineAdapter {

	private BeatmapFeatureTimelineAdapter() {}

	public static @Nullable AudioFeatureTimeline fromBeatmap(@Nullable Beatmap beatmap) {
		if (beatmap == null || beatmap.meta == null) {
			return null;
		}
		double duration = Math.max(0.0, beatmap.meta.durationMs() / 1000.0);
		if (duration <= 0.0 && (beatmap.beats == null || beatmap.beats.isEmpty())) {
			return null;
		}

		List<BeatEvent> events = beatmap.beats != null ? beatmap.beats : List.of();
		TreeMap<Long, DetectedBeat> uniqueBeats = new TreeMap<>();
		Map<Long, float[]> bandAccum = new LinkedHashMap<>();
		Map<Long, Float> energyAccum = new LinkedHashMap<>();

		for (BeatEvent event : events) {
			if (event == null) {
				continue;
			}
			long ms = Math.max(0L, event.timeMs());
			float energy = Math.max(0f, Math.min(1f, event.energy()));
			DetectedBeat existing = uniqueBeats.get(ms);
			if (existing == null || energy > existing.getStrength()) {
				uniqueBeats.put(ms, new DetectedBeat(ms / 1000.0, energy));
			}
			float[] bands = bandAccum.computeIfAbsent(ms, ignored -> new float[3]);
			switch (event.band()) {
				case LOW -> bands[0] = Math.max(bands[0], energy);
				case MID -> bands[1] = Math.max(bands[1], energy);
				case HIGH -> bands[2] = Math.max(bands[2], energy);
			}
			energyAccum.merge(ms, energy, Math::max);
		}

		List<DetectedBeat> beats = new ArrayList<>(uniqueBeats.values());
		List<FrequencyBands> bands = new ArrayList<>(bandAccum.size());
		List<EnergyFrame> energyFrames = new ArrayList<>(energyAccum.size());
		for (Map.Entry<Long, float[]> entry : bandAccum.entrySet()) {
			double time = entry.getKey() / 1000.0;
			float[] values = entry.getValue();
			bands.add(new FrequencyBands(time, values[0], values[1], values[2]));
			energyFrames.add(new EnergyFrame(time, energyAccum.getOrDefault(entry.getKey(), 0f)));
		}

		if (duration <= 0.0 && !beats.isEmpty()) {
			duration = beats.getLast().getTimeSeconds() + 0.5;
		}

		float bpm = (float) Math.max(0.0, beatmap.meta.bpm());
		BeatGrid grid = bpm > 0f ? new BeatGrid(bpm, duration) : null;
		return new AudioFeatureTimeline(
			duration,
			beats,
			energyFrames,
			bands,
			new WaveformExtractor.WaveformFrame[0],
			bpm,
			grid
		);
	}
}
