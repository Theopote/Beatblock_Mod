package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.MotifPhaseMode;
import com.beatblock.automap.choreography.SpatialMotifCompiler;
import com.beatblock.automap.choreography.SpatialMotifLayout;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link ChoreographyPhrase} + 已解析触发时刻展开为动画事件草稿。
 */
public final class PhraseGrammarExpander {

	private PhraseGrammarExpander() {}

	public record ExpandedPhraseEvent(
		String targetObjectId,
		double timeSeconds,
		String primitiveId,
		float energy,
		double durationSeconds,
		Map<String, Object> params,
		int sectionIndex,
		int triggerSequenceIndex
	) {
		public ExpandedPhraseEvent {
			if (targetObjectId == null) targetObjectId = "";
			if (primitiveId == null) primitiveId = "";
			params = params != null ? Map.copyOf(params) : Map.of();
		}
	}

	public static List<ExpandedPhraseEvent> expand(
		ChoreographyPhrase phrase,
		List<TriggerInstance> triggers,
		@Nullable SpatialMotifLayout layout
	) {
		if (phrase == null || phrase.targets().size() < 2 || triggers == null || triggers.isEmpty()) {
			return List.of();
		}
		SpatialMotifLayout resolvedLayout = layout != null
			? layout
			: SpatialMotifLayout.synthetic(phrase.targets().objectIds(), phrase.spatial().resolvedAxis());

		List<ExpandedPhraseEvent> out = new ArrayList<>();
		int triggerCount = triggers.size();
		for (TriggerInstance trigger : triggers) {
			float phraseEnergy = phrase.intensity().sample(trigger.sequenceIndex(), triggerCount);
			float energy = Math.min(1f, phraseEnergy * Math.max(0.1f, trigger.featureEnergy()));

			SpatialMotifPhrase motifPhrase = toMotifPhrase(phrase, trigger.timeSeconds(), energy);
			List<SpatialMotifCompiler.ExpandedEvent> expanded = SpatialMotifCompiler.expand(motifPhrase, resolvedLayout);
			for (int targetIndex = 0; targetIndex < expanded.size(); targetIndex++) {
				SpatialMotifCompiler.ExpandedEvent event = expanded.get(targetIndex);
				Map<String, Object> params = new HashMap<>(event.params());
				params.put("phraseGrammar", true);
				params.put("triggerSequenceIndex", trigger.sequenceIndex());
				applyVariationHeight(params, phrase.variation(), targetIndex);

				out.add(new ExpandedPhraseEvent(
					event.targetObjectId(),
					event.timeSeconds(),
					event.primitiveId(),
					event.energy(),
					event.durationSeconds(),
					params,
					phrase.sectionIndex(),
					trigger.sequenceIndex()
				));
			}
		}
		return List.copyOf(out);
	}

	public static List<ExpandedPhraseEvent> expand(
		ChoreographyPhrase phrase,
		PhraseTriggerContext context,
		@Nullable SpatialMotifLayout layout
	) {
		return expand(phrase, PhraseTriggerResolver.resolve(phrase, context), layout);
	}

	private static SpatialMotifPhrase toMotifPhrase(
		ChoreographyPhrase phrase,
		double timeSeconds,
		float energy
	) {
		SpatialPatternSpec spatial = phrase.spatial();
		MotionPresetSpec motion = phrase.motion();
		return new SpatialMotifPhrase(
			timeSeconds,
			spatial.resolvedPattern(),
			phrase.targets().objectIds(),
			spatial.resolvedAxis(),
			phrase.staggerStepSeconds(),
			motion.presetId(),
			MotifPhaseMode.IN_PHASE,
			energy,
			motion.durationSeconds(),
			motion.useEnergyForHeight(),
			motion.heightMultiplier(),
			phrase.sectionIndex(),
			phrase.timingSnap()
		);
	}

	private static void applyVariationHeight(Map<String, Object> params, VariationSpec variation, int targetIndex) {
		float scale = variation.heightScaleForTargetIndex(targetIndex);
		if (Math.abs(scale - 1f) < 1e-6f) return;
		Object height = params.get("height");
		if (height instanceof Number number) {
			params.put("height", number.floatValue() * scale);
		}
	}
}
