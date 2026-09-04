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
 * 将 {@link ChoreographyPhraseInstance}（或遗留 Phrase+Triggers）展开为动画事件草稿。
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

	/** Phrase Grammar v1：展开单个已预算的 Instance。 */
	public static List<ExpandedPhraseEvent> expand(
		ChoreographyPhraseInstance instance,
		@Nullable SpatialMotifLayout layout
	) {
		if (instance == null || instance.targetIds().size() < 1) {
			return List.of();
		}
		if (instance.layer() != com.beatblock.automap.choreography.ChoreographyLayer.ACCENT
			&& instance.targetIds().size() < 2) {
			// Spatial Phrase/Hero 仍要求至少 2 目标；Accent 允许单目标
			if (instance.isAtomicSpatial()) return List.of();
		}

		List<String> targets = instance.targetIds();
		SpatialMotifLayout resolvedLayout = layout != null
			? layout
			: SpatialMotifLayout.synthetic(targets, instance.spatial().resolvedAxis());

		if (instance.layer() == com.beatblock.automap.choreography.ChoreographyLayer.ACCENT) {
			return expandAccent(instance);
		}

		SpatialMotifPhrase motifPhrase = toMotifPhrase(instance);
		List<SpatialMotifCompiler.ExpandedEvent> expanded = SpatialMotifCompiler.expand(motifPhrase, resolvedLayout);
		List<ExpandedPhraseEvent> out = new ArrayList<>(expanded.size());
		for (int targetIndex = 0; targetIndex < expanded.size(); targetIndex++) {
			SpatialMotifCompiler.ExpandedEvent event = expanded.get(targetIndex);
			Map<String, Object> params = new HashMap<>(event.params());
			params.put("phraseGrammar", true);
			params.put("triggerSequenceIndex", instance.triggerSequenceIndex());
			params.put("phraseInstanceId", instance.instanceId());
			applyVariationHeight(params, instance.variation(), targetIndex);

			out.add(new ExpandedPhraseEvent(
				event.targetObjectId(),
				event.timeSeconds(),
				event.primitiveId(),
				event.energy(),
				event.durationSeconds(),
				params,
				instance.sectionIndex(),
				instance.triggerSequenceIndex()
			));
		}
		return List.copyOf(out);
	}

	public static List<ExpandedPhraseEvent> expand(
		ChoreographyPhrase phrase,
		List<TriggerInstance> triggers,
		@Nullable SpatialMotifLayout layout
	) {
		if (phrase == null || phrase.targets().size() < 2 || triggers == null || triggers.isEmpty()) {
			return List.of();
		}
		List<ChoreographyPhraseInstance> instances = new ArrayList<>(triggers.size());
		int triggerCount = triggers.size();
		for (TriggerInstance trigger : triggers) {
			instances.add(ChoreographyPhraseInstance.fromGrammarTrigger(
				phrase, trigger, 0, triggerCount, -1
			));
		}
		List<ExpandedPhraseEvent> out = new ArrayList<>();
		for (ChoreographyPhraseInstance instance : instances) {
			out.addAll(expand(instance, layout));
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

	private static List<ExpandedPhraseEvent> expandAccent(ChoreographyPhraseInstance instance) {
		String target = instance.targetIds().isEmpty() ? "" : instance.targetIds().getFirst();
		Map<String, Object> params = new HashMap<>();
		params.put("energy", instance.intensity());
		if (instance.motion().useEnergyForHeight()) {
			params.put("height", instance.intensity() * instance.motion().heightMultiplier());
		}
		params.put("phraseInstanceId", instance.instanceId());
		return List.of(new ExpandedPhraseEvent(
			target,
			instance.triggerTime(),
			instance.motion().presetId(),
			instance.intensity(),
			instance.motion().durationSeconds(),
			params,
			instance.sectionIndex(),
			instance.triggerSequenceIndex()
		));
	}

	private static SpatialMotifPhrase toMotifPhrase(ChoreographyPhraseInstance instance) {
		SpatialPatternSpec spatial = instance.spatial();
		MotionPresetSpec motion = instance.motion();
		return new SpatialMotifPhrase(
			instance.triggerTime(),
			spatial.resolvedPattern(),
			instance.targetIds(),
			spatial.resolvedAxis(),
			instance.staggerStepSeconds(),
			motion.presetId(),
			MotifPhaseMode.IN_PHASE,
			instance.intensity(),
			motion.durationSeconds(),
			motion.useEnergyForHeight(),
			motion.heightMultiplier(),
			instance.sectionIndex(),
			instance.timingSnap()
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
