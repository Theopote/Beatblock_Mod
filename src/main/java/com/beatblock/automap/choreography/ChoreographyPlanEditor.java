package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyGrammarSelection;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.SectionType;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对 {@link ChoreographyPlan} 进行 section-aware 查询与编辑。
 * <p>
 * 短语在构建时会绑定 {@code sectionIndex}；编辑器可按段落筛选、覆盖参数，或重绑段落索引。
 */
public final class ChoreographyPlanEditor {

	private ChoreographyPlanEditor() {}

	public static List<ChoreographyPlan.MotionPhrase> motionPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.motionPhrases(), sectionIndex, ChoreographyPlan.MotionPhrase::sectionIndex);
	}

	public static List<ChoreographyPlan.CameraPhrase> cameraPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.cameraPhrases(), sectionIndex, ChoreographyPlan.CameraPhrase::sectionIndex);
	}

	public static List<ChoreographyVfx> vfxPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.vfxPhrases(), sectionIndex, ChoreographyVfx::sectionIndex);
	}

	public static List<SpatialMotifPhrase> spatialMotifPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.spatialMotifPhrases(), sectionIndex, SpatialMotifPhrase::sectionIndex);
	}

	public static List<ChoreographyPhrase> choreographyPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.choreographyPhrases(), sectionIndex, ChoreographyPhrase::sectionIndex);
	}

	public static @Nullable SectionEditProfile editForSection(ChoreographyPlan plan, int sectionIndex) {
		for (SectionEditProfile edit : plan.sectionEdits()) {
			if (edit.sectionIndex() == sectionIndex) return edit;
		}
		return null;
	}

	public static ChoreographyPlan withSectionEdit(ChoreographyPlan plan, SectionEditProfile edit) {
		if (plan == null || edit == null) return plan;
		List<SectionEditProfile> merged = new ArrayList<>(plan.sectionEdits());
		boolean replaced = false;
		for (int i = 0; i < merged.size(); i++) {
			if (merged.get(i).sectionIndex() == edit.sectionIndex()) {
				merged.set(i, edit);
				replaced = true;
				break;
			}
		}
		if (!replaced) merged.add(edit);
		return copyPlan(plan, plan.sections(), plan.stageRoles(), plan.motionPhrases(),
			plan.cameraPhrases(), plan.vfxPhrases(), plan.densityCurve(), merged, plan.spatialMotifPhrases());
	}

	public static ChoreographyPlan withSectionEditForType(
		ChoreographyPlan plan,
		SectionType sectionType,
		SectionEditProfile template
	) {
		if (plan == null || sectionType == null || template == null) return plan;
		ChoreographyPlan result = plan;
		for (int i = 0; i < plan.sections().size(); i++) {
			if (plan.sections().get(i).sectionType() == sectionType) {
				SectionEditProfile edit = new SectionEditProfile(
					i,
					template.motionEnabled(),
					template.cameraEnabled(),
					template.vfxEnabled(),
					template.motionAnimationTypeOverride(),
					template.densityThresholdOverride(),
					template.timeOffsetSeconds(),
					template.energyScale(),
					template.spatialMotifEnabled(),
					template.spatialMotifIdOverride(),
					template.grammarTriggerIntervalOverride(),
					template.grammarStaggerStepOverride(),
					template.grammarIntensityCurveOverride(),
					template.grammarVariationOverride()
				);
				result = withSectionEdit(result, edit);
			}
		}
		return result;
	}

	/** 拖动段落边界：更新相邻 section 的 start/end，并重绑短语与密度曲线。 */
	public static boolean canMoveBoundary(ChoreographyPlan plan, int boundaryIndex) {
		if (plan == null || boundaryIndex < 1 || boundaryIndex >= plan.sections().size()) return false;
		ChoreographyPlan.SectionPlan left = plan.sections().get(boundaryIndex - 1);
		ChoreographyPlan.SectionPlan right = plan.sections().get(boundaryIndex);
		return left.source() != SectionPlanSource.LOCKED && right.source() != SectionPlanSource.LOCKED;
	}

	public static ChoreographyPlan updateSection(
		ChoreographyPlan plan,
		int sectionIndex,
		SectionType sectionType,
		String label,
		boolean locked
	) {
		if (plan == null || sectionIndex < 0 || sectionIndex >= plan.sections().size()) return plan;
		ChoreographyPlan.SectionPlan current = plan.sections().get(sectionIndex);
		SectionPlanSource source = locked
			? SectionPlanSource.LOCKED
			: SectionPlanSource.USER_EDITED;
		List<ChoreographyPlan.SectionPlan> sections = new ArrayList<>(plan.sections());
		sections.set(sectionIndex, new ChoreographyPlan.SectionPlan(
			current.startSeconds(),
			current.endSeconds(),
			sectionType != null ? sectionType : current.sectionType(),
			label != null ? label : current.label(),
			current.confidence(),
			source
		));
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			plan.motionPhrases(),
			plan.cameraPhrases(),
			plan.vfxPhrases(),
			rebuildDensityCurve(sections),
			plan.sectionEdits()
		);
	}

	public static ChoreographyPlan moveSectionBoundary(
		ChoreographyPlan plan,
		int boundaryIndex,
		double newTimeSeconds
	) {
		if (plan == null || boundaryIndex < 1 || boundaryIndex >= plan.sections().size()) return plan;
		if (!canMoveBoundary(plan, boundaryIndex)) return plan;
		List<ChoreographyPlan.SectionPlan> sections = new ArrayList<>(plan.sections());
		ChoreographyPlan.SectionPlan left = sections.get(boundaryIndex - 1);
		ChoreographyPlan.SectionPlan right = sections.get(boundaryIndex);
		double minTime = left.startSeconds() + MIN_SECTION_DURATION_SECONDS;
		double maxTime = right.endSeconds() - MIN_SECTION_DURATION_SECONDS;
		double clamped = Math.max(minTime, Math.min(maxTime, newTimeSeconds));
		sections.set(boundaryIndex - 1, new ChoreographyPlan.SectionPlan(
			left.startSeconds(),
			clamped,
			left.sectionType(),
			left.label(),
			left.confidence(),
			SectionPlanSource.USER_EDITED
		));
		sections.set(boundaryIndex, new ChoreographyPlan.SectionPlan(
			clamped,
			right.endSeconds(),
			right.sectionType(),
			right.label(),
			right.confidence(),
			SectionPlanSource.USER_EDITED
		));
		DensityCurve density = rebuildDensityCurve(sections);
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			rebindMotionPhrases(plan.motionPhrases(), sections),
			rebindCameraPhrases(plan.cameraPhrases(), sections),
			rebindVfxPhrases(plan.vfxPhrases(), sections),
			density,
			plan.sectionEdits(),
			rebindSpatialMotifPhrases(plan.spatialMotifPhrases(), sections)
		);
	}

	/**
	 * Set a section start time (index 0 or via boundary move). Marks adjacent sections USER_EDITED.
	 * Used by SECTION Marker → Plan projection bridge.
	 */
	public static ChoreographyPlan setSectionStartSeconds(
		ChoreographyPlan plan,
		int sectionIndex,
		double newStartSeconds
	) {
		if (plan == null || sectionIndex < 0 || sectionIndex >= plan.sections().size()) {
			return plan;
		}
		if (sectionIndex >= 1) {
			return moveSectionBoundary(plan, sectionIndex, newStartSeconds);
		}
		List<ChoreographyPlan.SectionPlan> sections = new ArrayList<>(plan.sections());
		ChoreographyPlan.SectionPlan first = sections.getFirst();
		if (first.source() == SectionPlanSource.LOCKED) {
			return plan;
		}
		double maxStart = first.endSeconds() - MIN_SECTION_DURATION_SECONDS;
		double clamped = Math.max(0.0, Math.min(maxStart, newStartSeconds));
		if (Math.abs(clamped - first.startSeconds()) <= 1e-6) {
			return plan;
		}
		sections.set(0, new ChoreographyPlan.SectionPlan(
			clamped,
			first.endSeconds(),
			first.sectionType(),
			first.label(),
			first.confidence(),
			SectionPlanSource.USER_EDITED
		));
		DensityCurve density = rebuildDensityCurve(sections);
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			rebindMotionPhrases(plan.motionPhrases(), sections),
			rebindCameraPhrases(plan.cameraPhrases(), sections),
			rebindVfxPhrases(plan.vfxPhrases(), sections),
			density,
			plan.sectionEdits(),
			rebindSpatialMotifPhrases(plan.spatialMotifPhrases(), sections)
		);
	}

	public static final double MIN_SECTION_DURATION_SECONDS = 0.5;

	/** 将段落内短语整体平移（同时更新 section 绑定）。 */
	public static ChoreographyPlan shiftSection(ChoreographyPlan plan, int sectionIndex, double deltaSeconds) {
		if (plan == null || Math.abs(deltaSeconds) < 1e-9) return plan;
		return copyPlan(
			plan,
			plan.sections(),
			plan.stageRoles(),
			transformMotionPhrases(plan, sectionIndex, deltaSeconds, null),
			transformCameraPhrases(plan, sectionIndex, deltaSeconds, null),
			transformVfxPhrases(plan, sectionIndex, deltaSeconds, null),
			plan.densityCurve(),
			plan.sectionEdits(),
			transformSpatialMotifPhrases(plan, sectionIndex, deltaSeconds)
		);
	}

	/** 按当前 sections 列表重绑所有短语的 sectionIndex。 */
	public static ChoreographyPlan rebindSectionIndices(ChoreographyPlan plan) {
		if (plan == null) return plan;
		List<ChoreographyPlan.SectionPlan> sections = plan.sections();
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			rebindMotionPhrases(plan.motionPhrases(), sections),
			rebindCameraPhrases(plan.cameraPhrases(), sections),
			rebindVfxPhrases(plan.vfxPhrases(), sections),
			plan.densityCurve(),
			plan.sectionEdits(),
			rebindSpatialMotifPhrases(plan.spatialMotifPhrases(), sections)
		);
	}

	/**
	 * 将 section 编辑覆盖烘焙进短语列表（动画类型、时间偏移、能量缩放），返回新计划。
	 * 启用开关与密度门槛仍由 {@link ChoreographyPlanCompiler} 在编译阶段读取 {@code sectionEdits}。
	 */
	public static ChoreographyPlan bakePhraseOverrides(ChoreographyPlan plan) {
		if (plan == null || plan.sectionEdits().isEmpty()) return plan;
		Map<Integer, SectionEditProfile> edits = indexEdits(plan.sectionEdits());
		return copyPlan(
			plan,
			plan.sections(),
			plan.stageRoles(),
			applyMotionOverrides(plan.motionPhrases(), edits),
			applyCameraOverrides(plan.cameraPhrases(), edits),
			applyVfxOverrides(plan.vfxPhrases(), edits),
			plan.densityCurve(),
			plan.sectionEdits(),
			applySpatialMotifOverrides(plan, plan.spatialMotifPhrases(), edits),
			applyGrammarPhraseOverrides(plan, plan.choreographyPhrases(), edits)
		);
	}

	public static @Nullable ChoreographyPhrase primaryGrammarPhraseInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		List<ChoreographyPhrase> phrases = choreographyPhrasesInSection(plan, sectionIndex);
		for (ChoreographyPhrase phrase : phrases) {
			if (!phrase.isHero()) return phrase;
		}
		return phrases.isEmpty() ? null : phrases.getFirst();
	}

	public static List<ChoreographyPhrase> heroPhrasesInSection(ChoreographyPlan plan, int sectionIndex) {
		List<ChoreographyPhrase> out = new ArrayList<>();
		for (ChoreographyPhrase phrase : choreographyPhrasesInSection(plan, sectionIndex)) {
			if (phrase.isHero()) out.add(phrase);
		}
		return out;
	}

	public static List<ChoreographyPhrase> phraseLayerPhrasesInSection(ChoreographyPlan plan, int sectionIndex) {
		List<ChoreographyPhrase> out = new ArrayList<>();
		for (ChoreographyPhrase phrase : choreographyPhrasesInSection(plan, sectionIndex)) {
			if (!phrase.isHero()) out.add(phrase);
		}
		return out;
	}

	public static @Nullable SpatialMotifPhrase primarySpatialMotifInSection(ChoreographyPlan plan, int sectionIndex) {
		List<SpatialMotifPhrase> phrases = spatialMotifPhrasesInSection(plan, sectionIndex);
		return phrases.isEmpty() ? null : phrases.getFirst();
	}

	static double resolveDensityThreshold(ChoreographyPlan plan, ChoreographyPlan.MotionPhrase phrase, double fallback) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		Double override = edit != null ? edit.densityThresholdOverride() : null;
		if (override != null) {
			return override;
		}
		return fallback;
	}

	static boolean isMotionEnabled(ChoreographyPlan plan, ChoreographyPlan.MotionPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.motionEnabled();
	}

	static boolean isCameraEnabled(ChoreographyPlan plan, ChoreographyPlan.CameraPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.cameraEnabled();
	}

	static boolean isVfxEnabled(ChoreographyPlan plan, ChoreographyVfx phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.vfxEnabled();
	}

	static boolean isSpatialMotifEnabled(ChoreographyPlan plan, SpatialMotifPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.motionEnabled();
	}

	static boolean isGrammarPhraseEnabled(ChoreographyPlan plan, ChoreographyPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		if (edit == null) return true;
		return edit.motionEnabled() && edit.spatialMotifEnabled();
	}

	private static <T> List<T> filterBySection(
		List<T> phrases,
		int sectionIndex,
		java.util.function.ToIntFunction<T> sectionIndexFn
	) {
		List<T> out = new ArrayList<>();
		for (T phrase : phrases) {
			if (sectionIndexFn.applyAsInt(phrase) == sectionIndex) out.add(phrase);
		}
		return out;
	}

	private static Map<Integer, SectionEditProfile> indexEdits(List<SectionEditProfile> edits) {
		Map<Integer, SectionEditProfile> byIndex = new HashMap<>();
		for (SectionEditProfile edit : edits) {
			byIndex.put(edit.sectionIndex(), edit);
		}
		return byIndex;
	}

	private static List<ChoreographyPlan.MotionPhrase> applyMotionOverrides(
		List<ChoreographyPlan.MotionPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.MotionPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			String animationType = edit.motionAnimationTypeOverride() != null
				? edit.motionAnimationTypeOverride()
				: phrase.animationTypeId();
			float energy = Math.min(1f, phrase.energy() * edit.energyScale());
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				energy,
				animationType,
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				phrase.sectionIndex(),
				phrase.timingSnap()
			));
		}
		return out;
	}

	private static List<SpatialMotifPhrase> applySpatialMotifOverrides(
		ChoreographyPlan plan,
		List<SpatialMotifPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		if (edits.isEmpty()) return phrases;
		List<SpatialMotifPhrase> retained = new ArrayList<>();
		for (SpatialMotifPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				retained.add(phrase);
			}
		}

		List<SpatialMotifPhrase> out = new ArrayList<>(retained);
		for (SectionEditProfile edit : edits.values()) {
			if (!edit.spatialMotifEnabled()) continue;
			SpatialMotifPhrase existing = findSpatialMotifPhrase(phrases, edit.sectionIndex());
			if (existing != null) {
				out.add(applySpatialMotifEdit(plan, existing, edit));
				continue;
			}
			SpatialMotifPhrase created = createSpatialMotifForSection(plan, edit);
			if (created != null) out.add(created);
		}
		return out;
	}

	private static @Nullable SpatialMotifPhrase findSpatialMotifPhrase(
		List<SpatialMotifPhrase> phrases,
		int sectionIndex
	) {
		for (SpatialMotifPhrase phrase : phrases) {
			if (phrase.sectionIndex() == sectionIndex) return phrase;
		}
		return null;
	}

	private static SpatialMotifPhrase applySpatialMotifEdit(
		ChoreographyPlan plan,
		SpatialMotifPhrase phrase,
		SectionEditProfile edit
	) {
		ChoreographyPlan.SectionPlan section = sectionAt(plan, edit.sectionIndex());
		SectionType sectionType = section != null ? section.sectionType() : SectionType.VERSE;
		SpatialMotifId motifId = edit.spatialMotifIdOverride() != null
			? edit.spatialMotifIdOverride()
			: SpatialMotifSelection.forSection(sectionType);
		String primitive = edit.motionAnimationTypeOverride() != null
			? edit.motionAnimationTypeOverride()
			: phrase.primitiveId();
		float energy = Math.min(1f, phrase.energy() * edit.energyScale());
		return new SpatialMotifPhrase(
			phrase.timeSeconds() + edit.timeOffsetSeconds(),
			motifId,
			phrase.participantIds(),
			edit.spatialMotifIdOverride() != null ? phrase.axis() : SpatialMotifSelection.defaultAxis(sectionType),
			phrase.propagationDelaySeconds() > 0
				? phrase.propagationDelaySeconds()
				: SpatialMotifSelection.defaultPropagationDelay(sectionType),
			primitive,
			phrase.phaseMode(),
			energy,
			phrase.durationSeconds(),
			phrase.useEnergyForHeight(),
			phrase.heightMultiplier(),
			phrase.sectionIndex(),
			phrase.timingSnap()
		);
	}

	private static @Nullable SpatialMotifPhrase createSpatialMotifForSection(
		ChoreographyPlan plan,
		SectionEditProfile edit
	) {
		ChoreographyPlan.SectionPlan section = sectionAt(plan, edit.sectionIndex());
		if (section == null) return null;
		List<String> participants = uniqueParticipantIds(plan.stageRoles());
		if (participants.size() < 2) return null;

		SectionType sectionType = section.sectionType();
		SpatialMotifId motifId = edit.spatialMotifIdOverride() != null
			? edit.spatialMotifIdOverride()
			: SpatialMotifSelection.forSection(sectionType);
		String primitive = edit.motionAnimationTypeOverride() != null
			? edit.motionAnimationTypeOverride()
			: SpatialMotifSelection.defaultPrimitive(sectionType);
		return new SpatialMotifPhrase(
			section.startSeconds() + 0.05 + edit.timeOffsetSeconds(),
			motifId,
			participants,
			SpatialMotifSelection.defaultAxis(sectionType),
			SpatialMotifSelection.defaultPropagationDelay(sectionType),
			primitive,
			0.75f * edit.energyScale(),
			0.5,
			edit.sectionIndex()
		);
	}

	private static List<ChoreographyPhrase> applyGrammarPhraseOverrides(
		ChoreographyPlan plan,
		List<ChoreographyPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		if (edits.isEmpty()) return phrases;
		List<ChoreographyPhrase> retained = new ArrayList<>();
		for (ChoreographyPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				retained.add(phrase);
				continue;
			}
			// Section 编辑作用于 Phrase 层；HERO 在 motif 启用时原样保留。
			if (phrase.isHero() && edit.spatialMotifEnabled()) {
				retained.add(phrase);
			}
		}

		List<ChoreographyPhrase> out = new ArrayList<>(retained);
		for (SectionEditProfile edit : edits.values()) {
			if (!edit.spatialMotifEnabled()) continue;
			ChoreographyPhrase existing = findGrammarPhrase(phrases, edit.sectionIndex());
			if (existing != null) {
				out.add(applyGrammarPhraseEdit(plan, existing, edit));
				continue;
			}
			ChoreographyPhrase created = createGrammarPhraseForSection(plan, edit);
			if (created != null) out.add(created);
		}
		return out;
	}

	private static @Nullable ChoreographyPhrase findGrammarPhrase(
		List<ChoreographyPhrase> phrases,
		int sectionIndex
	) {
		for (ChoreographyPhrase phrase : phrases) {
			if (phrase.sectionIndex() == sectionIndex && !phrase.isHero()) return phrase;
		}
		return null;
	}

	private static ChoreographyPhrase applyGrammarPhraseEdit(
		ChoreographyPlan plan,
ChoreographyPhrase phrase,
		SectionEditProfile edit
	) {
		ChoreographyPlan.SectionPlan section = sectionAt(plan, edit.sectionIndex());
		SectionType sectionType = section != null ? section.sectionType() : SectionType.VERSE;

SpatialPatternSpec defaultSpatial =
ChoreographyGrammarSelection.spatialPattern(sectionType);
		SpatialMotifId motifId = edit.spatialMotifIdOverride() != null
			? edit.spatialMotifIdOverride()
			: defaultSpatial.resolvedPattern();
		MotifAxis axis = edit.spatialMotifIdOverride() != null
			? phrase.spatial().resolvedAxis()
			: defaultSpatial.resolvedAxis();
SpatialPatternSpec spatial =
			motifId == SpatialMotifId.CASCADE && axis == MotifAxis.X
				? SpatialPatternSpec.leftToRight()
				: SpatialPatternSpec.of(motifId, axis);

		String motionId = edit.motionAnimationTypeOverride() != null
			? edit.motionAnimationTypeOverride()
			: phrase.motion().presetId();
MotionPresetSpec motion =
			new MotionPresetSpec(
				motionId,
				phrase.motion().durationSeconds(),
				phrase.motion().useEnergyForHeight(),
				phrase.motion().heightMultiplier()
			);

		TriggerSpec trigger = phrase.trigger();
		Integer intervalOverride = edit.grammarTriggerIntervalOverride();
		if (intervalOverride != null) {
			trigger = applyIntervalOverride(trigger, intervalOverride);
		}

TimingPatternSpec timing = phrase.timing();
		if (edit.grammarStaggerStepOverride() != null) {
			timing = TimingPatternSpec.stagger(
				edit.grammarStaggerStepOverride()
			);
		}

IntensityEnvelope intensity = resolveGrammarIntensity(
			phrase.intensity(),
			edit,
			sectionType
		);

VariationSpec variation = resolveGrammarVariation(
			phrase.variation(),
			edit,
			sectionType
		);

		return new ChoreographyPhrase(
			trigger,
			phrase.targets(),
			spatial,
			motion,
			timing,
			intensity,
			variation,
			phrase.sectionIndex(),
			phrase.timingSnap(),
			phrase.layer()
		);
	}

	private static @Nullable ChoreographyPhrase createGrammarPhraseForSection(
		ChoreographyPlan plan,
		SectionEditProfile edit
	) {
		ChoreographyPlan.SectionPlan section = sectionAt(plan, edit.sectionIndex());
		if (section == null) return null;
		List<String> participants = uniqueParticipantIds(plan.stageRoles());
		if (participants.size() < 2) return null;

		SectionType sectionType = section.sectionType();
		TriggerSpec trigger = ChoreographyGrammarSelection.defaultTrigger(sectionType);
		Integer intervalOverride = edit.grammarTriggerIntervalOverride();
		if (intervalOverride != null) {
			trigger = applyIntervalOverride(trigger, intervalOverride);
		}

TimingPatternSpec timing =
			edit.grammarStaggerStepOverride() != null
				? TimingPatternSpec.stagger(edit.grammarStaggerStepOverride())
				: ChoreographyGrammarSelection.timing(sectionType);

SpatialPatternSpec spatial =
			resolveGrammarSpatialPattern(edit, sectionType);
		String motionId = edit.motionAnimationTypeOverride() != null
			? edit.motionAnimationTypeOverride()
			: ChoreographyGrammarSelection.motion(sectionType).presetId();

		return new ChoreographyPhrase(
			trigger,
TargetSet.of(
				participants.toArray(String[]::new)
			),
			spatial,
MotionPresetSpec.of(motionId),
			timing,
			resolveGrammarIntensity(
ChoreographyGrammarSelection.intensity(sectionType),
				edit,
				sectionType
			),
			resolveGrammarVariation(
ChoreographyGrammarSelection.variation(sectionType),
				edit,
				sectionType
			),
			edit.sectionIndex()
		);
	}

	private static TriggerSpec applyIntervalOverride(TriggerSpec trigger, int interval) {
		int resolved = Math.max(1, interval);
		return switch (trigger) {
			case TriggerSpec.EveryNFeatureHits hits ->
				new TriggerSpec.EveryNFeatureHits(hits.featureKey(), resolved);
			case TriggerSpec.EveryNBeats beats ->
				new TriggerSpec.EveryNBeats(resolved, beats.phaseOffset());
			default -> trigger;
		};
	}

	private static SpatialPatternSpec resolveGrammarSpatialPattern(
		SectionEditProfile edit,
		SectionType sectionType
	) {
		if (edit.spatialMotifIdOverride() == null) {
			return ChoreographyGrammarSelection.spatialPattern(sectionType);
		}
		MotifAxis axis = SpatialMotifSelection.defaultAxis(sectionType);
		SpatialMotifId motifId = edit.spatialMotifIdOverride();
		return motifId == SpatialMotifId.CASCADE && axis == MotifAxis.X
			? SpatialPatternSpec.leftToRight()
			: SpatialPatternSpec.of(motifId, axis);
	}

	private static IntensityEnvelope resolveGrammarIntensity(
IntensityEnvelope fallback,
		SectionEditProfile edit,
		SectionType sectionType
	) {
		if (edit.grammarIntensityCurveOverride() != null) {
			String curve = edit.grammarIntensityCurveOverride();
			return switch (curve.toUpperCase(java.util.Locale.ROOT)) {
				case "CRESCENDO" -> IntensityEnvelope.crescendo(
					Math.min(1f, 0.6f * edit.energyScale()),
					Math.min(1f, 1.0f * edit.energyScale())
				);
				default -> IntensityEnvelope.flat(
					Math.min(1f, 0.75f * edit.energyScale())
				);
			};
		}
		if (Math.abs(edit.energyScale() - 1f) < 1e-6f) return fallback;
		return IntensityEnvelope.flat(
			Math.min(1f, fallback.startEnergy() * edit.energyScale())
		);
	}

	private static VariationSpec resolveGrammarVariation(
VariationSpec fallback,
		SectionEditProfile edit,
		SectionType sectionType
	) {
		if (edit.grammarVariationOverride() == null) return fallback;
		String variation = edit.grammarVariationOverride();
		return switch (variation.toUpperCase(java.util.Locale.ROOT)) {
			case "ALTERNATE_HEIGHT" -> VariationSpec.alternateHeight(0.3f);
			default -> VariationSpec.none();
		};
	}

	private static ChoreographyPlan.@Nullable SectionPlan sectionAt(ChoreographyPlan plan, int sectionIndex) {
		if (sectionIndex < 0 || sectionIndex >= plan.sections().size()) return null;
		return plan.sections().get(sectionIndex);
	}

	private static List<String> uniqueParticipantIds(List<ChoreographyPlan.StageRoleAssignment> roles) {
		if (roles == null || roles.isEmpty()) return List.of();
		java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
		for (ChoreographyPlan.StageRoleAssignment role : roles) {
			if (role == null || role.targetObjectId() == null || role.targetObjectId().isBlank()) continue;
			ids.add(role.targetObjectId());
		}
		return List.copyOf(ids);
	}

	private static List<ChoreographyPlan.CameraPhrase> applyCameraOverrides(
		List<ChoreographyPlan.CameraPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.CameraPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			out.add(new ChoreographyPlan.CameraPhrase(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.action(),
				phrase.sectionIndex(),
				phrase.subjectKind(),
				phrase.subjectRef(),
				phrase.durationSeconds(),
				phrase.framing(),
				phrase.movement(),
				phrase.easing(),
				phrase.beatAligned(),
				phrase.timingSnap(),
				phrase.transition()
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> applyVfxOverrides(
		List<ChoreographyVfx> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyVfx> out = new ArrayList<>(phrases.size());
		for (ChoreographyVfx phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			out.add(phrase.withTiming(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.sectionIndex()
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.MotionPhrase> transformMotionPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(plan.motionPhrases().size());
		for (ChoreographyPlan.MotionPhrase phrase : plan.motionPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds() + deltaSeconds,
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				phrase.energy(),
				phrase.animationTypeId(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				resolveSectionIndex(plan.sections(), phrase.timeSeconds() + deltaSeconds),
				phrase.timingSnap()
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.CameraPhrase> transformCameraPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(plan.cameraPhrases().size());
		for (ChoreographyPlan.CameraPhrase phrase : plan.cameraPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			double time = phrase.timeSeconds() + deltaSeconds;
			out.add(new ChoreographyPlan.CameraPhrase(
				time,
				phrase.action(),
				resolveSectionIndex(plan.sections(), time),
				phrase.subjectKind(),
				phrase.subjectRef(),
				phrase.durationSeconds(),
				phrase.framing(),
				phrase.movement(),
				phrase.easing(),
				phrase.beatAligned(),
				phrase.timingSnap(),
				phrase.transition()
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> transformVfxPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyVfx> out = new ArrayList<>(plan.vfxPhrases().size());
		for (ChoreographyVfx phrase : plan.vfxPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			double time = phrase.timeSeconds() + deltaSeconds;
			out.add(phrase.withTiming(time, resolveSectionIndex(plan.sections(), time)));
		}
		return out;
	}

	private static List<ChoreographyPlan.MotionPhrase> rebindMotionPhrases(
		List<ChoreographyPlan.MotionPhrase> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.MotionPhrase phrase : phrases) {
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds(),
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				phrase.energy(),
				phrase.animationTypeId(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				resolveSectionIndex(sections, phrase.timeSeconds()),
				phrase.timingSnap()
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.CameraPhrase> rebindCameraPhrases(
		List<ChoreographyPlan.CameraPhrase> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.CameraPhrase phrase : phrases) {
			out.add(new ChoreographyPlan.CameraPhrase(
				phrase.timeSeconds(),
				phrase.action(),
				resolveSectionIndex(sections, phrase.timeSeconds()),
				phrase.subjectKind(),
				phrase.subjectRef(),
				phrase.durationSeconds(),
				phrase.framing(),
				phrase.movement(),
				phrase.easing(),
				phrase.beatAligned(),
				phrase.timingSnap(),
				phrase.transition()
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> rebindVfxPhrases(
		List<ChoreographyVfx> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyVfx> out = new ArrayList<>(phrases.size());
		for (ChoreographyVfx phrase : phrases) {
			out.add(phrase.withTiming(
				phrase.timeSeconds(),
				resolveSectionIndex(sections, phrase.timeSeconds())
			));
		}
		return out;
	}

	private static List<SpatialMotifPhrase> rebindSpatialMotifPhrases(
		List<SpatialMotifPhrase> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<SpatialMotifPhrase> out = new ArrayList<>(phrases.size());
		for (SpatialMotifPhrase phrase : phrases) {
			out.add(new SpatialMotifPhrase(
				phrase.timeSeconds(),
				phrase.motifId(),
				phrase.participantIds(),
				phrase.axis(),
				phrase.propagationDelaySeconds(),
				phrase.primitiveId(),
				phrase.phaseMode(),
				phrase.energy(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				resolveSectionIndex(sections, phrase.timeSeconds()),
				phrase.timingSnap()
			));
		}
		return out;
	}

	private static List<SpatialMotifPhrase> transformSpatialMotifPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds
	) {
		List<SpatialMotifPhrase> out = new ArrayList<>(plan.spatialMotifPhrases().size());
		for (SpatialMotifPhrase phrase : plan.spatialMotifPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			double time = phrase.timeSeconds() + deltaSeconds;
			out.add(new SpatialMotifPhrase(
				time,
				phrase.motifId(),
				phrase.participantIds(),
				phrase.axis(),
				phrase.propagationDelaySeconds(),
				phrase.primitiveId(),
				phrase.phaseMode(),
				phrase.energy(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				resolveSectionIndex(plan.sections(), time),
				phrase.timingSnap()
			));
		}
		return out;
	}

	private static int resolveSectionIndex(List<ChoreographyPlan.SectionPlan> sections, double timeSeconds) {
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.endSeconds()
				: timeSeconds < section.endSeconds();
			if (timeSeconds >= section.startSeconds() && withinEnd) {
				return i;
			}
		}
		return -1;
	}

	private static DensityCurve rebuildDensityCurve(List<ChoreographyPlan.SectionPlan> sections) {
		if (sections.isEmpty()) return DensityCurve.uniform(1.0);
		List<DensityCurve.Point> points = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sections) {
			double density = ChoreographyBudget.sectionVisualDensity(section.sectionType());
			points.add(new DensityCurve.Point(section.startSeconds(), density));
		}
		return DensityCurve.ofPoints(points);
	}

	private static ChoreographyPlan copyPlan(
		ChoreographyPlan source,
		List<ChoreographyPlan.SectionPlan> sections,
		List<ChoreographyPlan.StageRoleAssignment> roles,
		List<ChoreographyPlan.MotionPhrase> motions,
		List<ChoreographyPlan.CameraPhrase> cameras,
		List<ChoreographyVfx> vfx,
		DensityCurve density,
		List<SectionEditProfile> edits
	) {
		return copyPlan(
			source,
			sections,
			roles,
			motions,
			cameras,
			vfx,
			density,
			edits,
			source.spatialMotifPhrases(),
			source.choreographyPhrases()
		);
	}

	private static ChoreographyPlan copyPlan(
		ChoreographyPlan source,
		List<ChoreographyPlan.SectionPlan> sections,
		List<ChoreographyPlan.StageRoleAssignment> roles,
		List<ChoreographyPlan.MotionPhrase> motions,
		List<ChoreographyPlan.CameraPhrase> cameras,
		List<ChoreographyVfx> vfx,
		DensityCurve density,
		List<SectionEditProfile> edits,
		List<SpatialMotifPhrase> spatialMotifPhrases
	) {
		return copyPlan(
			source,
			sections,
			roles,
			motions,
			cameras,
			vfx,
			density,
			edits,
			spatialMotifPhrases,
			source.choreographyPhrases()
		);
	}

	private static ChoreographyPlan copyPlan(
		ChoreographyPlan source,
		List<ChoreographyPlan.SectionPlan> sections,
		List<ChoreographyPlan.StageRoleAssignment> roles,
		List<ChoreographyPlan.MotionPhrase> motions,
		List<ChoreographyPlan.CameraPhrase> cameras,
		List<ChoreographyVfx> vfx,
		DensityCurve density,
		List<SectionEditProfile> edits,
		List<SpatialMotifPhrase> spatialMotifPhrases,
		List<ChoreographyPhrase> choreographyPhrases
	) {
		return new ChoreographyPlan(
			sections,
			roles,
			motions,
			cameras,
			vfx,
			density,
			edits,
			source.musicalStructure(),
			spatialMotifPhrases,
			choreographyPhrases
		);
	}
}
