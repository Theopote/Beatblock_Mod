package com.beatblock.automap.choreography.grammar;

/**
 * 目标之间的时序关系。
 */
public sealed interface TimingPatternSpec permits TimingPatternSpec.Simultaneous, TimingPatternSpec.Stagger {

	record Simultaneous() implements TimingPatternSpec {}

	record Stagger(double stepSeconds) implements TimingPatternSpec {
		public Stagger {
			stepSeconds = Math.max(0.0, stepSeconds);
		}
	}

	static TimingPatternSpec stagger(double stepSeconds) {
		return stepSeconds > 0.0 ? new Stagger(stepSeconds) : new Simultaneous();
	}
}
