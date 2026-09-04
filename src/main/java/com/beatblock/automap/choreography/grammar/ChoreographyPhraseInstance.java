package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.TimingSnapDefaults;

import java.util.List;

/**
 * 一次已解析触发的编舞实例：Trigger 之后、Expand 之前的决策单元。
 * <p>
 * 流水线：{@link ChoreographyPhrase} → Trigger → {@code ChoreographyPhraseInstance}
 * → Budget / {@link ChoreographyConflictResolver} → Expander → Timeline events。
 * Budget 在此层整组保留或丢弃，避免拆烂空间 Phrase；Conflict 按 target/时间做层间压制。
 */
public record ChoreographyPhraseInstance(
	String instanceId,
	String sourcePhraseId,
	ChoreographyLayer layer,
	int sectionIndex,
	int phraseIndex,
	double triggerTime,
	SpatialPatternSpec spatial,
	MotionPresetSpec motion,
	List<String> targetIds,
	float intensity,
	double priority,
	ChoreographyTimingSnap timingSnap,
	TimingPatternSpec timing,
	VariationSpec variation,
	int triggerSequenceIndex
) {
	public ChoreographyPhraseInstance {
		instanceId = instanceId != null ? instanceId.trim() : "";
		sourcePhraseId = sourcePhraseId != null ? sourcePhraseId.trim() : "";
		layer = layer != null ? layer : ChoreographyLayer.ACCENT;
		sectionIndex = Math.max(-1, sectionIndex);
		phraseIndex = Math.max(-1, phraseIndex);
		spatial = spatial != null ? spatial : SpatialPatternSpec.leftToRight();
		motion = motion != null ? motion : MotionPresetSpec.of("pulse");
		targetIds = targetIds != null ? List.copyOf(targetIds) : List.of();
		intensity = Math.max(0f, Math.min(1f, intensity));
		priority = Math.max(0.0, priority);
		timingSnap = timingSnap != null ? timingSnap : TimingSnapDefaults.forTrigger(new TriggerSpec.OnFeature("low"));
		timing = timing != null ? timing : new TimingPatternSpec.Simultaneous();
		variation = variation != null ? variation : VariationSpec.none();
		triggerSequenceIndex = Math.max(0, triggerSequenceIndex);
	}

	public int estimatedEventCount() {
		return Math.max(1, targetIds.size());
	}

	public boolean isAtomicSpatial() {
		return layer == ChoreographyLayer.PHRASE || layer == ChoreographyLayer.HERO;
	}

	public boolean countsTowardPhraseLayerBudget() {
		return layer == ChoreographyLayer.PHRASE || layer == ChoreographyLayer.HERO;
	}

	public boolean isHero() {
		return layer == ChoreographyLayer.HERO;
	}

	public double staggerStepSeconds() {
		if (timing instanceof TimingPatternSpec.Stagger stagger) {
			return stagger.stepSeconds();
		}
		return 0.0;
	}

	/** 该 Instance 占用舞台的起始时刻（含 stagger 起点）。 */
	public double activeStartSeconds() {
		return triggerTime;
	}

	/** 该 Instance 占用舞台的结束时刻（motion 时长 + stagger 跨度）。 */
	public double activeEndSeconds() {
		double staggerSpan = staggerStepSeconds() * Math.max(0, targetIds.size() - 1);
		return triggerTime + Math.max(0.01, motion.durationSeconds()) + staggerSpan;
	}

	public static double priorityFor(ChoreographyLayer layer, float intensity) {
		ChoreographyLayer resolved = layer != null ? layer : ChoreographyLayer.ACCENT;
		int layerRank = switch (resolved) {
			case HERO -> 3;
			case PHRASE -> 2;
			case ACCENT -> 1;
		};
		return layerRank * 10.0 + Math.max(0f, Math.min(1f, intensity));
	}

	/** Accent：单目标局部纹理。 */
	public static ChoreographyPhraseInstance accent(
		String instanceId,
		String sourcePhraseId,
		int sectionIndex,
		int phraseIndex,
		double triggerTime,
		String targetId,
		MotionPresetSpec motion,
		float intensity,
		ChoreographyTimingSnap timingSnap
	) {
		String target = targetId != null ? targetId : "";
		return new ChoreographyPhraseInstance(
			instanceId,
			sourcePhraseId,
			ChoreographyLayer.ACCENT,
			sectionIndex,
			phraseIndex,
			triggerTime,
			SpatialPatternSpec.leftToRight(),
			motion,
			target.isBlank() ? List.of() : List.of(target),
			intensity,
			priorityFor(ChoreographyLayer.ACCENT, intensity),
			timingSnap,
			new TimingPatternSpec.Simultaneous(),
			VariationSpec.none(),
			0
		);
	}

	public static ChoreographyPhraseInstance fromGrammarTrigger(
		ChoreographyPhrase phrase,
		TriggerInstance trigger,
		int sourceOrdinal,
		int triggerCount,
		int phraseIndex
	) {
		if (phrase == null || trigger == null) {
			throw new IllegalArgumentException("phrase/trigger required");
		}
		float phraseEnergy = phrase.intensity().sample(trigger.sequenceIndex(), Math.max(1, triggerCount));
		float intensity = Math.min(1f, phraseEnergy * Math.max(0.1f, trigger.featureEnergy()));
		String sourcePhraseId = phrase.spatial().resolvedPattern().name();
		String instanceId = "grammar:" + Math.max(0, sourceOrdinal) + ":t" + trigger.sequenceIndex();
		return new ChoreographyPhraseInstance(
			instanceId,
			sourcePhraseId,
			phrase.layer(),
			phrase.sectionIndex(),
			phraseIndex,
			trigger.timeSeconds(),
			phrase.spatial(),
			phrase.motion(),
			phrase.targets().objectIds(),
			intensity,
			priorityFor(phrase.layer(), intensity),
			phrase.timingSnap(),
			phrase.timing(),
			phrase.variation(),
			trigger.sequenceIndex()
		);
	}

	public static ChoreographyPhraseInstance fromSpatialMotif(
		String instanceId,
		String sourcePhraseId,
		int sectionIndex,
		int phraseIndex,
		double triggerTime,
		SpatialPatternSpec spatial,
		MotionPresetSpec motion,
		List<String> targetIds,
		float intensity,
		ChoreographyTimingSnap timingSnap,
		double staggerStepSeconds
	) {
		TimingPatternSpec timing = staggerStepSeconds > 1e-9
			? TimingPatternSpec.stagger(staggerStepSeconds)
			: new TimingPatternSpec.Simultaneous();
		return new ChoreographyPhraseInstance(
			instanceId,
			sourcePhraseId != null ? sourcePhraseId : "spatial",
			ChoreographyLayer.PHRASE,
			sectionIndex,
			phraseIndex,
			triggerTime,
			spatial,
			motion,
			targetIds,
			intensity,
			priorityFor(ChoreographyLayer.PHRASE, intensity),
			timingSnap,
			timing,
			VariationSpec.none(),
			0
		);
	}
}
