package com.beatblock.audio.analysis.structure;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@link AudioFeatureTimeline} 提取 beat/bar 对齐的结构特征序列。
 */
public final class StructureFeatureExtractor {

	public static final double DEFAULT_HOP_SECONDS = 0.25;

	private StructureFeatureExtractor() {}

	public static List<StructureFeatureFrame> extract(AudioFeatureTimeline timeline) {
		if (timeline == null || timeline.getDurationSeconds() <= 0) return List.of();
		if (timeline.getEnergyFrames().isEmpty() && timeline.getBands().isEmpty()) {
			return List.of();
		}

		double hop = resolveHopSeconds(timeline);
		double duration = timeline.getDurationSeconds();
		List<Double> sampleTimes = buildSampleTimes(duration, hop, timeline.getBeatGrid());

		float maxEnergy = 1f;
		for (EnergyFrame frame : timeline.getEnergyFrames()) {
			maxEnergy = Math.max(maxEnergy, frame.getEnergy());
		}
		if (maxEnergy < 1e-6f) maxEnergy = 1f;

		List<StructureFeatureFrame> out = new ArrayList<>(sampleTimes.size());
		StructureFeatureFrame prev = null;
		for (double time : sampleTimes) {
			float energy = sampleEnergy(timeline.getEnergyFrames(), time) / maxEnergy;
			float[] bands = sampleBands(timeline.getBands(), time);
			float onset = sampleOnsetDensity(timeline.getBeats(), time, hop * 2);
			float flux = prev == null ? 0f : bandFlux(prev, bands);
			StructureFeatureFrame frame = new StructureFeatureFrame(
				time, energy, bands[0], bands[1], bands[2], onset, flux);
			out.add(frame);
			prev = frame;
		}
		return out;
	}

	private static double resolveHopSeconds(AudioFeatureTimeline timeline) {
		BeatGrid grid = timeline.getBeatGrid();
		if (grid != null && grid.getBpm() > 0) {
			return Math.max(0.125, grid.getBeatDuration() / 4.0);
		}
		return DEFAULT_HOP_SECONDS;
	}

	private static List<Double> buildSampleTimes(double duration, double hop, BeatGrid grid) {
		List<Double> times = new ArrayList<>();
		if (grid != null && grid.getBpm() > 0) {
			double beatDur = grid.getBeatDuration();
			double step = beatDur / 4.0;
			for (double t = 0; t <= duration + 1e-6; t += step) {
				times.add(t);
			}
			if (times.isEmpty()) times.add(0.0);
			return times;
		}
		for (double t = 0; t <= duration + 1e-6; t += hop) {
			times.add(t);
		}
		if (times.isEmpty()) times.add(0.0);
		return times;
	}

	private static float sampleEnergy(List<EnergyFrame> frames, double time) {
		if (frames.isEmpty()) return 0f;
		EnergyFrame nearest = frames.get(0);
		double bestDist = Math.abs(nearest.getTimeSeconds() - time);
		for (EnergyFrame frame : frames) {
			double dist = Math.abs(frame.getTimeSeconds() - time);
			if (dist < bestDist) {
				bestDist = dist;
				nearest = frame;
			}
		}
		return nearest.getEnergy();
	}

	private static float[] sampleBands(List<FrequencyBands> bands, double time) {
		if (bands.isEmpty()) return new float[] { 0f, 0f, 0f };
		FrequencyBands nearest = bands.get(0);
		double bestDist = Math.abs(nearest.getTimeSeconds() - time);
		for (FrequencyBands band : bands) {
			double dist = Math.abs(band.getTimeSeconds() - time);
			if (dist < bestDist) {
				bestDist = dist;
				nearest = band;
			}
		}
		float sum = nearest.getLow() + nearest.getMid() + nearest.getHigh();
		if (sum < 1e-6f) return new float[] { 0f, 0f, 0f };
		return new float[] {
			nearest.getLow() / sum,
			nearest.getMid() / sum,
			nearest.getHigh() / sum
		};
	}

	private static float sampleOnsetDensity(List<DetectedBeat> beats, double time, double windowSeconds) {
		if (beats.isEmpty() || windowSeconds <= 0) return 0f;
		int count = 0;
		for (DetectedBeat beat : beats) {
			if (Math.abs(beat.getTimeSeconds() - time) <= windowSeconds) count++;
		}
		return (float) (count / Math.max(1.0, windowSeconds * 4.0));
	}

	private static float bandFlux(StructureFeatureFrame prev, float[] bands) {
		float delta = Math.abs(bands[0] - prev.lowBand())
			+ Math.abs(bands[1] - prev.midBand())
			+ Math.abs(bands[2] - prev.highBand());
		return Math.min(1f, delta);
	}
}
