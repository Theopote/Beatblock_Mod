package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.TimingSnapDefaults;

/**
 * 编舞短语语法：音乐触发 → 多目标空间编排 → 运动原语。
 * <p>
 * 由 {@link PhraseTriggerResolver} 解析为 {@link ChoreographyPhraseInstance}，
 * 经 Budget 后再由 {@link PhraseGrammarExpander} 展开为动画事件。
 * {@link #layer()} 区分 {@link ChoreographyLayer#PHRASE} 与 {@link ChoreographyLayer#HERO}。
 * {@link #timingSnap()} 默认由 {@link TimingSnapDefaults#forTrigger(TriggerSpec)} 决定。
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
	ChoreographyTimingSnap timingSnap,
	ChoreographyLayer layer
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
		this(
			trigger, targets, spatial, motion, timing, intensity, variation,
			sectionIndex,
			TimingSnapDefaults.forTrigger(trigger),
			ChoreographyLayer.PHRASE
		);
	}

	public ChoreographyPhrase(
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
		this(
			trigger, targets, spatial, motion, timing, intensity, variation,
			sectionIndex, timingSnap, ChoreographyLayer.PHRASE
		);
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
		timingSnap = timingSnap != null ? timingSnap : TimingSnapDefaults.forTrigger(trigger);
		layer = layer == ChoreographyLayer.HERO ? ChoreographyLayer.HERO : ChoreographyLayer.PHRASE;
	}

	public double staggerStepSeconds() {
		if (timing instanceof TimingPatternSpec.Stagger stagger) {
			return stagger.stepSeconds();
		}
		return 0.0;
	}

	public boolean isHero() {
		return layer == ChoreographyLayer.HERO;
	}
}
