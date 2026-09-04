package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyPhraseBatchSupport;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.choreography.TimingSnapResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Plan 中的 Accent / Spatial / Grammar 物化为 {@link ChoreographyPhraseInstance}。
 */
public final class ChoreographyPhraseInstanceMaterializer {

	private ChoreographyPhraseInstanceMaterializer() {}

	public static List<ChoreographyPhraseInstance> fromGrammarPhrase(
		ChoreographyPhrase phrase,
		PhraseTriggerContext context,
		int sourceOrdinal,
		ChoreographyPlan plan,
		TimingSnapResolver.SnapContext snapContext
	) {
		if (phrase == null) return List.of();
		List<TriggerInstance> triggers = PhraseTriggerResolver.resolve(phrase, context);
		if (triggers.isEmpty()) return List.of();
		List<ChoreographyPhraseInstance> out = new ArrayList<>(triggers.size());
		int triggerCount = triggers.size();
		for (TriggerInstance trigger : triggers) {
			double snappedTime = TimingSnapResolver.snap(
				trigger.timeSeconds(),
				phrase.timingSnap(),
				snapContext
			);
			TriggerInstance snapped = new TriggerInstance(
				snappedTime,
				trigger.featureEnergy(),
				trigger.sequenceIndex()
			);
			int phraseIndex = plan != null ? plan.musicalPhraseIndexAt(snappedTime) : -1;
			out.add(ChoreographyPhraseInstance.fromGrammarTrigger(
				phrase, snapped, sourceOrdinal, triggerCount, phraseIndex
			));
		}
		return List.copyOf(out);
	}

	public static ChoreographyPhraseInstance fromSpatialMotif(
		SpatialMotifPhrase phrase,
		int spatialOrdinal,
		ChoreographyPlan plan,
		TimingSnapResolver.SnapContext snapContext
	) {
		double baseTime = TimingSnapResolver.snap(
			phrase.timeSeconds(),
			phrase.timingSnap(),
			snapContext
		);
		SpatialMotifId motifId = phrase.motifId() != null ? phrase.motifId() : SpatialMotifId.CASCADE;
		MotifAxis axis = phrase.axis() != null ? phrase.axis() : MotifAxis.X;
		MotionPresetSpec motion = new MotionPresetSpec(
			phrase.primitiveId(),
			phrase.durationSeconds(),
			phrase.useEnergyForHeight(),
			phrase.heightMultiplier()
		);
		int phraseIndex = plan != null ? plan.musicalPhraseIndexAt(baseTime) : -1;
		return ChoreographyPhraseInstance.fromSpatialMotif(
			ChoreographyPhraseBatchSupport.spatialInstanceId(spatialOrdinal),
			motifId.name(),
			phrase.sectionIndex(),
			phraseIndex,
			baseTime,
			SpatialPatternSpec.of(motifId, axis),
			motion,
			phrase.participantIds(),
			phrase.energy(),
			phrase.timingSnap(),
			phrase.propagationDelaySeconds()
		);
	}

	public static ChoreographyPhraseInstance fromAccent(
		ChoreographyPlan.MotionPhrase phrase,
		String targetId,
		int accentOrdinal,
		ChoreographyPlan plan,
		TimingSnapResolver.SnapContext snapContext
	) {
		double eventTime = TimingSnapResolver.snap(
			phrase.timeSeconds(),
			phrase.timingSnap(),
			snapContext
		);
		MotionPresetSpec motion = new MotionPresetSpec(
			phrase.animationTypeId(),
			phrase.durationSeconds(),
			phrase.useEnergyForHeight(),
			phrase.heightMultiplier()
		);
		int phraseIndex = plan != null ? plan.musicalPhraseIndexAt(eventTime) : -1;
		String sourceId = phrase.normalizedFeatureKey() != null && !phrase.normalizedFeatureKey().isBlank()
			? phrase.normalizedFeatureKey()
			: "accent";
		return ChoreographyPhraseInstance.accent(
			"accent:" + Math.max(0, accentOrdinal),
			sourceId,
			phrase.sectionIndex(),
			phraseIndex,
			eventTime,
			targetId,
			motion,
			phrase.energy(),
			phrase.timingSnap() != null ? phrase.timingSnap() : ChoreographyTimingSnap.BEAT
		);
	}
}
