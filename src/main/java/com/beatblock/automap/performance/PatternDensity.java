package com.beatblock.automap.performance;

import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.PatternGenerator;

/**
 * {@link EventDensityPolicy} → PatternGenerator 间隔 / 能量阈值。
 */
public final class PatternDensity {

	public record Gaps(double low, double mid, double high, float energyThreshold) {}

	private PatternDensity() {}

	public static Gaps gapsFor(EventDensityPolicy policy) {
		EventDensityPolicy safe = policy != null ? policy : EventDensityPolicy.SECTION_AWARE;
		return switch (safe) {
			case BEAT_HEAVY -> {
				PatternGenerator.FeatureMinGaps base = PatternGenerator.featureMinGaps(Complexity.HIGH);
				yield new Gaps(base.low(), base.mid(), base.high(), PatternGenerator.getEnergyThreshold(Complexity.HIGH));
			}
			case SECTION_AWARE -> {
				PatternGenerator.FeatureMinGaps base = PatternGenerator.featureMinGaps(Complexity.MEDIUM);
				yield new Gaps(base.low(), base.mid(), base.high(), PatternGenerator.getEnergyThreshold(Complexity.MEDIUM));
			}
			case SPARSE_CINEMATIC -> {
				PatternGenerator.FeatureMinGaps base = PatternGenerator.featureMinGaps(Complexity.LOW);
				yield new Gaps(base.low() * 1.15, base.mid() * 1.15, base.high() * 1.2,
					PatternGenerator.getEnergyThreshold(Complexity.LOW));
			}
		};
	}
}
