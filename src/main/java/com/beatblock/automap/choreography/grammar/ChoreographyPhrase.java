package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyTimingSnap;

/**
 * 编舞短语语法：音乐触发 → 多目标空间编排 → 运动原语。
 * <p>
 * 由 {@link PhraseGrammarExpander} 在编译期展开为多条动画事件。
 */
public record ChoreographyPhrase(
	TriggerSpec trigger,
	TargetSet targets,
	SpatialPatternSpec spatial,
	MotionPresetSpec motion,
	TimingPatternSpec timing,
	IntensityEnvelope intensity,
	VariationSpec variation,
	int sectionIndex,
	ChoreographyTimingSnap timingSnap
) {
	public ChoreographyPhrase(
		TriggerSpec trigger,
		TargetSet targets,
		SpatialPatternSpec spatial,
		MotionPresetSpec motion,
		TimingPatternSpec timing,
		IntensityEnvelope intensity,
		VariationSpec variation,
		int sectionIndex
	) {
		this(trigger, targets, spatial, motion, timing, intensity, variation, sectionIndex, ChoreographyTimingSnap.BAR);
	}

	public ChoreographyPhrase {
		trigger = trigger != null ? trigger : new TriggerSpec.OnFeature("low");
		targets = targets != null ? targets : TargetSet.of();
		spatial = spatial != null ? spatial : SpatialPatternSpec.leftToRight();
		motion = motion != null ? motion : MotionPresetSpec.bounce();
		timing = timing != null ? timing : new TimingPatternSpec.Simultaneous();
		intensity = intensity != null ? intensity : IntensityEnvelope.flat(0.8f);
		variation = variation != null ? variation : VariationSpec.none();
		sectionIndex = Math.max(-1, sectionIndex);
		timingSnap = timingSnap != null ? timingSnap : ChoreographyTimingSnap.BAR;
	}

	public double staggerStepSeconds() {
		if (timing instanceof TimingPatternSpec.Stagger stagger) {
			return stagger.stepSeconds();
		}
		return 0.0;
	}
}
