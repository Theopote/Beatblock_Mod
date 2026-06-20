package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 STEP 派发在<strong>调度时</strong>展开为带绝对时间戳的单块步骤（无运行时状态机）。
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
		int blocksPerBeat = Math.max(1, readInt(params.get("blocksPerBeat"), 1));
		boolean immediate = isImmediateStart(params);
		int slotCount = (int) Math.ceil(orderedBlocks.size() / (double) blocksPerBeat);

		PacingStrategy strategy = referenceBeatTimesSeconds != null && referenceBeatTimesSeconds.length > 0
			? PacingStrategy.beatGrid()
			: PacingStrategy.fixedInterval();
		List<Double> slotTimes = strategy.computeTimestamps(new PacingRequest(
			slotCount,
			sourceEvent.getTimeSeconds(),
			immediate,
			referenceBeatTimesSeconds,
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

	private static boolean isImmediateStart(Map<String, Object> params) {
		if (params == null) return false;
		Object raw = params.get("stepStartMode");
		if (raw == null) return false;
		return "IMMEDIATE".equalsIgnoreCase(String.valueOf(raw).trim());
	}

	private static int readInt(Object raw, int fallback) {
		if (raw instanceof Number n) return Math.max(1, n.intValue());
		if (raw == null) return fallback;
		try {
			return Math.max(1, (int) Math.round(Double.parseDouble(String.valueOf(raw).trim())));
		} catch (Exception ex) {
			return fallback;
		}
	}
}
