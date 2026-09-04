package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 同 target / 时间窗冲突的语义压制：{@link ChoreographyLayer#HERO} &gt;
 * {@link ChoreographyLayer#PHRASE} &gt; {@link ChoreographyLayer#ACCENT}。
 * <p>
 * 不同 target 可并存；共享 target 且活跃时间重叠时，整组保留高优先级 Instance、丢弃低优先级。
 * 在 Budget 之后、Expand 之前运行。
 */
public final class ChoreographyConflictResolver {

	private static final double TIME_EPSILON = 1e-6;

	private ChoreographyConflictResolver() {}

	public static List<ChoreographyPhraseInstance> resolve(List<ChoreographyPhraseInstance> instances) {
		if (instances == null || instances.isEmpty()) return List.of();

		List<ChoreographyPhraseInstance> ranked = new ArrayList<>(instances);
		ranked.sort(Comparator
			.comparingDouble(ChoreographyPhraseInstance::priority).reversed()
			.thenComparing(Comparator.comparingInt(ChoreographyConflictResolver::layerRank).reversed())
			.thenComparingDouble(ChoreographyPhraseInstance::triggerTime)
			.thenComparing(ChoreographyPhraseInstance::instanceId));

		List<ChoreographyPhraseInstance> accepted = new ArrayList<>(ranked.size());
		for (ChoreographyPhraseInstance candidate : ranked) {
			if (isSuppressedBy(candidate, accepted)) continue;
			accepted.add(candidate);
		}

		accepted.sort(Comparator
			.comparingDouble(ChoreographyPhraseInstance::triggerTime)
			.thenComparing(Comparator.comparingDouble(ChoreographyPhraseInstance::priority).reversed())
			.thenComparing(ChoreographyPhraseInstance::instanceId));
		return List.copyOf(accepted);
	}

	private static boolean isSuppressedBy(
		ChoreographyPhraseInstance candidate,
		List<ChoreographyPhraseInstance> accepted
	) {
		for (ChoreographyPhraseInstance winner : accepted) {
			if (conflicts(winner, candidate)) return true;
		}
		return false;
	}

	static boolean conflicts(ChoreographyPhraseInstance a, ChoreographyPhraseInstance b) {
		if (a == null || b == null) return false;
		if (a.instanceId().equals(b.instanceId())) return false;
		if (!sharesTarget(a, b)) return false;
		return timeOverlaps(a, b);
	}

	static boolean sharesTarget(ChoreographyPhraseInstance a, ChoreographyPhraseInstance b) {
		Set<String> left = targetSet(a);
		if (left.isEmpty()) return false;
		for (String target : b.targetIds()) {
			if (target != null && !target.isBlank() && left.contains(target)) {
				return true;
			}
		}
		return false;
	}

	static boolean timeOverlaps(ChoreographyPhraseInstance a, ChoreographyPhraseInstance b) {
		return a.activeStartSeconds() < b.activeEndSeconds() - TIME_EPSILON
			&& b.activeStartSeconds() < a.activeEndSeconds() - TIME_EPSILON;
	}

	private static Set<String> targetSet(ChoreographyPhraseInstance instance) {
		Set<String> targets = new HashSet<>();
		for (String target : instance.targetIds()) {
			if (target != null && !target.isBlank()) targets.add(target);
		}
		return targets;
	}

	private static int layerRank(ChoreographyPhraseInstance instance) {
		return switch (instance.layer()) {
			case HERO -> 3;
			case PHRASE -> 2;
			case ACCENT -> 1;
		};
	}
}
