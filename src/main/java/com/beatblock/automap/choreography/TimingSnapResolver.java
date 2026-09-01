package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

import java.util.List;

/**
 * 按 {@link ChoreographyTimingSnap} 将编舞事件时间对齐到节拍 / 小节 / 乐句 / Section 网格。
 */
public final class TimingSnapResolver {

	static final double DEFAULT_TOLERANCE_SECONDS = 0.08;
	private static final double BEAT_TOLERANCE_SECONDS = 0.06;

	private TimingSnapResolver() {}

	public record SnapContext(
		ChoreographyPlan.MusicalStructure musical,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		public SnapContext {
			musical = musical != null ? musical : ChoreographyPlan.MusicalStructure.empty();
			sections = sections != null ? List.copyOf(sections) : List.of();
		}

		public static SnapContext from(ChoreographyPlan plan) {
			if (plan == null) {
				return new SnapContext(ChoreographyPlan.MusicalStructure.empty(), List.of());
			}
			return new SnapContext(plan.musicalStructure(), plan.sections());
		}
	}

	public static double snap(
		double timeSeconds,
		ChoreographyTimingSnap snap,
		SnapContext context
	) {
		return snap(timeSeconds, snap, context, toleranceFor(snap));
	}

	public static double snap(
		double timeSeconds,
		ChoreographyTimingSnap snap,
		SnapContext context,
		double toleranceSeconds
	) {
		if (snap == null || snap == ChoreographyTimingSnap.NONE || context == null) {
			return timeSeconds;
		}
		return switch (snap) {
			case BEAT -> snapToNearestBeat(timeSeconds, context, toleranceSeconds);
			case HALF_BEAT -> snapToSubdivision(timeSeconds, context, 2, toleranceSeconds);
			case QUARTER_BEAT -> snapToSubdivision(timeSeconds, context, 4, toleranceSeconds);
			case BAR -> snapToNearestBarStart(timeSeconds, context.musical(), toleranceSeconds);
			case PHRASE -> snapToNearestPhraseStart(timeSeconds, context.musical(), toleranceSeconds);
			case SECTION -> snapToNearestSectionStart(timeSeconds, context.sections(), toleranceSeconds);
			case NONE -> timeSeconds;
		};
	}

	private static double toleranceFor(ChoreographyTimingSnap snap) {
		return switch (snap) {
			case BEAT, HALF_BEAT, QUARTER_BEAT -> BEAT_TOLERANCE_SECONDS;
			case BAR, PHRASE, SECTION -> DEFAULT_TOLERANCE_SECONDS;
			case NONE -> 0.0;
		};
	}

	static double snapToNearestBarStart(
		double timeSeconds,
		ChoreographyPlan.MusicalStructure musical,
		double toleranceSeconds
	) {
		if (musical == null || musical.bars().isEmpty() || toleranceSeconds <= 0) {
			return timeSeconds;
		}
		return snapToNearestCandidate(timeSeconds, musical.bars().stream()
			.mapToDouble(ChoreographyPlan.BarPlan::startSeconds)
			.toArray(), toleranceSeconds);
	}

	private static double snapToNearestPhraseStart(
		double timeSeconds,
		ChoreographyPlan.MusicalStructure musical,
		double toleranceSeconds
	) {
		if (musical == null || musical.phrases().isEmpty() || toleranceSeconds <= 0) {
			return timeSeconds;
		}
		return snapToNearestCandidate(timeSeconds, musical.phrases().stream()
			.mapToDouble(ChoreographyPlan.MusicalPhrasePlan::startSeconds)
			.toArray(), toleranceSeconds);
	}

	private static double snapToNearestSectionStart(
		double timeSeconds,
		List<ChoreographyPlan.SectionPlan> sections,
		double toleranceSeconds
	) {
		if (sections == null || sections.isEmpty() || toleranceSeconds <= 0) {
			return timeSeconds;
		}
		return snapToNearestCandidate(timeSeconds, sections.stream()
			.mapToDouble(ChoreographyPlan.SectionPlan::startSeconds)
			.toArray(), toleranceSeconds);
	}

	private static double snapToNearestBeat(double timeSeconds, SnapContext context, double toleranceSeconds) {
		List<Double> beatTimes = context.musical() != null ? context.musical().beatTimes() : List.of();
		if (!beatTimes.isEmpty()) {
			return snapToNearestCandidate(timeSeconds,
				beatTimes.stream().mapToDouble(Double::doubleValue).toArray(),
				toleranceSeconds);
		}
		return snapToSubdivision(timeSeconds, context, 1, toleranceSeconds);
	}

	private static double snapToSubdivision(
		double timeSeconds,
		SnapContext context,
		int partsPerBeat,
		double toleranceSeconds
	) {
		BeatGrid grid = resolveBeatGrid(context);
		if (grid == null || partsPerBeat <= 0) return timeSeconds;
		double step = grid.beatDurationSeconds / partsPerBeat;
		double snapped = snapToGrid(timeSeconds, grid.originSeconds, step);
		return Math.abs(snapped - timeSeconds) <= toleranceSeconds ? snapped : timeSeconds;
	}

	private static double snapToNearestCandidate(double timeSeconds, double[] candidates, double toleranceSeconds) {
		double bestTime = timeSeconds;
		double bestDistance = toleranceSeconds;
		for (double candidate : candidates) {
			double distance = Math.abs(timeSeconds - candidate);
			if (distance < bestDistance) {
				bestDistance = distance;
				bestTime = candidate;
			}
		}
		return bestTime;
	}

	private static double snapToGrid(double timeSeconds, double origin, double step) {
		if (step <= 0) return timeSeconds;
		double offset = timeSeconds - origin;
		return origin + Math.round(offset / step) * step;
	}

	private static BeatGrid resolveBeatGrid(SnapContext context) {
		if (context == null || context.musical() == null) return null;
		ChoreographyPlan.MusicalStructure musical = context.musical();
		if (!musical.beatTimes().isEmpty()) {
			double origin = musical.beatTimes().getFirst();
			double beatDuration = musical.beatTimes().size() >= 2
				? musical.beatTimes().get(1) - musical.beatTimes().get(0)
				: estimateBeatDuration(musical);
			if (beatDuration > 0) {
				return new BeatGrid(origin, beatDuration);
			}
		}
		if (!musical.bars().isEmpty()) {
			ChoreographyPlan.BarPlan first = musical.bars().getFirst();
			double barDuration = Math.max(0.01, first.endSeconds() - first.startSeconds());
			return new BeatGrid(first.startSeconds(), barDuration / 4.0);
		}
		return null;
	}

	private static double estimateBeatDuration(ChoreographyPlan.MusicalStructure musical) {
		List<Double> beats = musical.beatTimes();
		if (beats.size() < 2) return 0.0;
		double sum = 0.0;
		int count = 0;
		for (int i = 1; i < beats.size(); i++) {
			double delta = beats.get(i) - beats.get(i - 1);
			if (delta > 0.01) {
				sum += delta;
				count++;
			}
		}
		return count > 0 ? sum / count : 0.0;
	}

	private record BeatGrid(double originSeconds, double beatDurationSeconds) {}
}
