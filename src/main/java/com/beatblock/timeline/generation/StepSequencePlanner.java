package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 STEP 派发在<strong>调度/烘焙时</strong>展开为带绝对时间戳的单块步骤（无运行时状态机）。
 */
public final class StepSequencePlanner {

	public record PlannedStep(BlockPos block, double startTimeSeconds) {}

	private StepSequencePlanner() {}

	public static List<PlannedStep> plan(
		List<BlockPos> orderedBlocks,
		TimelineAnimationEvent sourceEvent,
		double[] referenceBeatTimesSeconds,
		double timelineBpm
	) {
		if (orderedBlocks == null || orderedBlocks.isEmpty() || sourceEvent == null) {
			return List.of();
		}
		Map<String, Object> params = sourceEvent.getParameters();
		boolean immediate = isImmediateStart(params);
		double[] beats = referenceBeatTimesSeconds != null ? referenceBeatTimesSeconds : new double[0];
		PacingMode pacingMode = PacingMode.fromParams(params, beats.length > 0);
		double anchor = resolveAnchorTime(sourceEvent.getTimeSeconds(), immediate, beats);

		if (pacingMode == PacingMode.DISTANCE) {
			return planByDistance(orderedBlocks, params, anchor);
		}

		int blocksPerBeat = Math.max(1, readInt(params.get("blocksPerBeat"), 1));
		int slotCount = (int) Math.ceil(orderedBlocks.size() / (double) blocksPerBeat);
		PacingStrategy strategy = pacingMode == PacingMode.FIXED_INTERVAL
			? PacingStrategy.fixedInterval()
			: PacingStrategy.beatGrid();
		List<Double> slotTimes = strategy.computeTimestamps(new PacingRequest(
			slotCount,
			anchor,
			true,
			beats,
			timelineBpm > 0 ? timelineBpm : 120.0,
			0.0
		));

		List<PlannedStep> planned = new ArrayList<>(orderedBlocks.size());
		for (int i = 0; i < orderedBlocks.size(); i++) {
			int slot = Math.min(i / blocksPerBeat, Math.max(0, slotTimes.size() - 1));
			double start = slotTimes.get(slot);
			planned.add(new PlannedStep(orderedBlocks.get(i), start));
		}
		return planned;
	}

	private static List<PlannedStep> planByDistance(
		List<BlockPos> orderedBlocks,
		Map<String, Object> params,
		double anchorTimeSeconds
	) {
		double secondsPerBlock = readDouble(params.get("distancePaceSecondsPerBlock"), DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT);
		double minGap = readDouble(params.get("distancePaceMinGapSeconds"), DistancePacing.DEFAULT_MIN_GAP_SECONDS);
		List<Double> blockTimes = DistancePacing.computeBlockTimestamps(
			orderedBlocks, anchorTimeSeconds, true, secondsPerBlock, minGap);
		List<PlannedStep> planned = new ArrayList<>(orderedBlocks.size());
		for (int i = 0; i < orderedBlocks.size(); i++) {
			planned.add(new PlannedStep(orderedBlocks.get(i), blockTimes.get(i)));
		}
		return planned;
	}

	static double resolveAnchorTime(double eventTimeSeconds, boolean immediate, double[] referenceBeatTimesSeconds) {
		if (immediate || referenceBeatTimesSeconds == null || referenceBeatTimesSeconds.length == 0) {
			return Math.max(0.0, eventTimeSeconds);
		}
		int idx = BeatGridPacing.firstBeatIndexAtOrAfter(eventTimeSeconds, referenceBeatTimesSeconds);
		if (idx < referenceBeatTimesSeconds.length) {
			return referenceBeatTimesSeconds[idx];
		}
		return Math.max(0.0, eventTimeSeconds);
	}

	private static boolean isImmediateStart(Map<String, Object> params) {
		if (params == null) return false;
		Object raw = params.get("stepStartMode");
		if (raw == null) return false;
		return "IMMEDIATE".equalsIgnoreCase(String.valueOf(raw).trim());
	}

	private static int readInt(@Nullable Object raw, int fallback) {
		if (raw instanceof Number n) return Math.max(1, n.intValue());
		if (raw == null) return fallback;
		try {
			return Math.max(1, (int) Math.round(Double.parseDouble(String.valueOf(raw).trim())));
		} catch (Exception ex) {
			return fallback;
		}
	}

	private static double readDouble(@Nullable Object raw, double fallback) {
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}
}
