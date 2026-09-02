package com.beatblock.ui.presenter;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyPlanEditor;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.choreography.SpatialMotifSelection;
import com.beatblock.automap.choreography.grammar.ChoreographyGrammarSelection;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 时间轴 section 编舞编辑业务逻辑。
 */
public final class TimelineSectionEditPresenter {

	public static final String[] MOTION_ANIMATION_IDS = {
		"bounce", "slide", "pulse", "spin", "fade"
	};

	public static final int SPATIAL_MOTIF_AUTO_INDEX = 0;
	public static final int SPATIAL_MOTIF_NONE_INDEX = 1;
	public static final SpatialMotifId[] SPATIAL_MOTIF_IDS = SpatialMotifId.values();

	public static final int GRAMMAR_TRIGGER_AUTO_INDEX = 0;
	public static final int[] GRAMMAR_TRIGGER_INTERVALS = { 2, 4, 8 };

	public static final int GRAMMAR_INTENSITY_AUTO_INDEX = 0;
	public static final String[] GRAMMAR_INTENSITY_CURVES = { "FLAT", "CRESCENDO" };

	public static final int GRAMMAR_VARIATION_AUTO_INDEX = 0;
	public static final String[] GRAMMAR_VARIATION_KINDS = { "NONE", "ALTERNATE_HEIGHT" };

	public static final List<SectionType> SECTION_TYPES = List.of(SectionType.values());

	public record SpatialMotifDropdownOption(int index, String labelKey) {}

	public static List<SpatialMotifDropdownOption> spatialMotifDropdownOptions() {
		List<SpatialMotifDropdownOption> options = new ArrayList<>(2 + SPATIAL_MOTIF_IDS.length);
		options.add(new SpatialMotifDropdownOption(SPATIAL_MOTIF_AUTO_INDEX, "beatblock.section_edit.spatial_motif.auto"));
		options.add(new SpatialMotifDropdownOption(SPATIAL_MOTIF_NONE_INDEX, "beatblock.section_edit.spatial_motif.none"));
		for (SpatialMotifId motifId : SPATIAL_MOTIF_IDS) {
			options.add(new SpatialMotifDropdownOption(
				options.size(),
				"beatblock.section_edit.spatial_motif." + motifId.name().toLowerCase(java.util.Locale.ROOT)
			));
		}
		return List.copyOf(options);
	}

	public static String[] spatialMotifDropdownLabels() {
		List<SpatialMotifDropdownOption> options = spatialMotifDropdownOptions();
		String[] labels = new String[options.size()];
		for (int i = 0; i < options.size(); i++) {
			labels[i] = BBTexts.get(options.get(i).labelKey());
		}
		return labels;
	}

	public static String[] grammarTriggerIntervalLabels() {
		String[] labels = new String[1 + GRAMMAR_TRIGGER_INTERVALS.length];
		labels[0] = BBTexts.get("beatblock.section_edit.grammar.trigger.auto");
		for (int i = 0; i < GRAMMAR_TRIGGER_INTERVALS.length; i++) {
			labels[i + 1] = BBTexts.get(
				"beatblock.section_edit.grammar.trigger.every_n",
				GRAMMAR_TRIGGER_INTERVALS[i]
			);
		}
		return labels;
	}

	public static String[] grammarIntensityLabels() {
		String[] labels = new String[1 + GRAMMAR_INTENSITY_CURVES.length];
		labels[0] = BBTexts.get("beatblock.section_edit.grammar.intensity.auto");
		for (int i = 0; i < GRAMMAR_INTENSITY_CURVES.length; i++) {
			labels[i + 1] = BBTexts.get(
				"beatblock.section_edit.grammar.intensity." + GRAMMAR_INTENSITY_CURVES[i].toLowerCase(java.util.Locale.ROOT)
			);
		}
		return labels;
	}

	public static String[] grammarVariationLabels() {
		String[] labels = new String[1 + GRAMMAR_VARIATION_KINDS.length];
		labels[0] = BBTexts.get("beatblock.section_edit.grammar.variation.auto");
		for (int i = 0; i < GRAMMAR_VARIATION_KINDS.length; i++) {
			labels[i + 1] = BBTexts.get(
				"beatblock.section_edit.grammar.variation." + GRAMMAR_VARIATION_KINDS[i].toLowerCase(java.util.Locale.ROOT)
			);
		}
		return labels;
	}

	public record SectionView(
		int index,
		String label,
		SectionType sectionType,
		double startSeconds,
		double endSeconds,
		double confidence,
		SectionPlanSource source,
		int motionCount,
		int cameraCount,
		int vfxCount,
		int grammarPhraseCount,
		@org.jspecify.annotations.Nullable SpatialMotifId spatialMotifId,
		@org.jspecify.annotations.Nullable String grammarMotionPreset,
		@org.jspecify.annotations.Nullable Integer grammarTriggerInterval
	) {}

	public record ApplyOutcome(PresenterResult result, int animationEvents, int cameraEvents, int vfxEvents) {}

	private final Supplier<BeatBlockContext> context;

	public TimelineSectionEditPresenter(Supplier<BeatBlockContext> context) {
		this.context = context;
	}

	public Timeline currentTimeline() {
		return context.get().timeline();
	}

	public String unavailableReason() {
		Timeline timeline = currentTimeline();
		if (timeline == null) {
			return BBTexts.get("beatblock.message.timeline_unavailable");
		}
		if (!ChoreographyPlanStore.hasPlan(timeline)) {
			return BBTexts.get("beatblock.section_edit.no_plan");
		}
		return null;
	}

	public boolean canEdit() {
		return unavailableReason() == null;
	}

	public List<SectionView> listSections() {
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return List.of();
		List<SectionView> views = new ArrayList<>(plan.sections().size());
		for (int i = 0; i < plan.sections().size(); i++) {
			var section = plan.sections().get(i);
			ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, i);
			views.add(new SectionView(
				i,
				section.label().isBlank() ? section.sectionType().name() : section.label(),
				section.sectionType(),
				section.startSeconds(),
				section.endSeconds(),
				section.confidence(),
				section.source(),
				ChoreographyPlanEditor.motionPhrasesInSection(plan, i).size(),
				ChoreographyPlanEditor.cameraPhrasesInSection(plan, i).size(),
				ChoreographyPlanEditor.vfxPhrasesInSection(plan, i).size(),
				Math.max(
					ChoreographyPlanEditor.choreographyPhrasesInSection(plan, i).size(),
					ChoreographyPlanEditor.spatialMotifPhrasesInSection(plan, i).size()
				),
				resolveDisplayedSpatialMotif(plan, i),
				grammar != null ? grammar.motion().presetId() : null,
				resolveDisplayedTriggerInterval(grammar, plan.sections().get(i).sectionType())
			));
		}
		return views;
	}

	public SectionEditProfile loadEditProfile(int sectionIndex) {
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return SectionEditProfile.defaults(sectionIndex);
		SectionEditProfile existing = ChoreographyPlanEditor.editForSection(plan, sectionIndex);
		return existing != null ? existing : SectionEditProfile.defaults(sectionIndex);
	}

	public int resolveSpatialMotifDropdownIndex(int sectionIndex, SectionEditProfile profile) {
		if (profile != null && !profile.spatialMotifEnabled()) {
			return SPATIAL_MOTIF_NONE_INDEX;
		}
		if (profile != null && profile.spatialMotifIdOverride() != null) {
			return indexOfSpatialMotif(profile.spatialMotifIdOverride());
		}
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return SPATIAL_MOTIF_AUTO_INDEX;
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		if (grammar != null) {
			return indexOfSpatialMotif(grammar.spatial().resolvedPattern());
		}
		SpatialMotifPhrase phrase = ChoreographyPlanEditor.primarySpatialMotifInSection(plan, sectionIndex);
		if (phrase == null) return SPATIAL_MOTIF_AUTO_INDEX;
		return indexOfSpatialMotif(phrase.motifId());
	}

	public int resolveGrammarTriggerDropdownIndex(int sectionIndex, SectionEditProfile profile) {
		if (profile != null && profile.grammarTriggerIntervalOverride() != null) {
			return indexOfGrammarTriggerInterval(profile.grammarTriggerIntervalOverride());
		}
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return GRAMMAR_TRIGGER_AUTO_INDEX;
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		if (grammar != null && grammar.trigger() instanceof TriggerSpec.EveryNBeats everyN) {
			return indexOfGrammarTriggerInterval(everyN.interval());
		}
		if (sectionIndex >= 0 && sectionIndex < plan.sections().size()) {
			TriggerSpec trigger = ChoreographyGrammarSelection.defaultTrigger(
				plan.sections().get(sectionIndex).sectionType()
			);
			if (trigger instanceof TriggerSpec.EveryNBeats everyN) {
				return indexOfGrammarTriggerInterval(everyN.interval());
			}
		}
		return GRAMMAR_TRIGGER_AUTO_INDEX;
	}

	public int resolveGrammarIntensityDropdownIndex(int sectionIndex, SectionEditProfile profile) {
		if (profile != null && profile.grammarIntensityCurveOverride() != null) {
			return indexOfGrammarIntensity(profile.grammarIntensityCurveOverride());
		}
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return GRAMMAR_INTENSITY_AUTO_INDEX;
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		IntensityEnvelope.EnvelopeCurve curve = grammar != null
			? grammar.intensity().curve()
			: (sectionIndex >= 0 && sectionIndex < plan.sections().size()
				? ChoreographyGrammarSelection.intensity(plan.sections().get(sectionIndex).sectionType()).curve()
				: IntensityEnvelope.EnvelopeCurve.FLAT);
		return indexOfGrammarIntensity(curve.name());
	}

	public int resolveGrammarVariationDropdownIndex(int sectionIndex, SectionEditProfile profile) {
		if (profile != null && profile.grammarVariationOverride() != null) {
			return indexOfGrammarVariation(profile.grammarVariationOverride());
		}
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return GRAMMAR_VARIATION_AUTO_INDEX;
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		VariationSpec.VariationKind kind = grammar != null
			? grammar.variation().kind()
			: (sectionIndex >= 0 && sectionIndex < plan.sections().size()
				? ChoreographyGrammarSelection.variation(plan.sections().get(sectionIndex).sectionType()).kind()
				: VariationSpec.VariationKind.NONE);
		return indexOfGrammarVariation(kind.name());
	}

	public float resolveGrammarStaggerSeconds(int sectionIndex, SectionEditProfile profile) {
		if (profile != null && profile.grammarStaggerStepOverride() != null) {
			return profile.grammarStaggerStepOverride().floatValue();
		}
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return 0.06f;
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		if (grammar != null && grammar.timing() instanceof TimingPatternSpec.Stagger stagger) {
			return (float) stagger.stepSeconds();
		}
		if (sectionIndex >= 0 && sectionIndex < plan.sections().size()) {
			TimingPatternSpec timing = ChoreographyGrammarSelection.timing(
				plan.sections().get(sectionIndex).sectionType()
			);
			if (timing instanceof TimingPatternSpec.Stagger stagger) {
				return (float) stagger.stepSeconds();
			}
		}
		return 0.06f;
	}

	public SectionEditProfile applySpatialMotifDropdownIndex(SectionEditProfile profile, int dropdownIndex) {
		if (profile == null) return SectionEditProfile.defaults(0);
		if (dropdownIndex <= SPATIAL_MOTIF_AUTO_INDEX) {
			return profile.withSpatialMotifAuto();
		}
		if (dropdownIndex == SPATIAL_MOTIF_NONE_INDEX) {
			return profile.withSpatialMotifEnabled(false);
		}
		int motifIndex = dropdownIndex - 2;
		if (motifIndex < 0 || motifIndex >= SPATIAL_MOTIF_IDS.length) {
			return profile.withSpatialMotifAuto();
		}
		return profile.withSpatialMotifId(SPATIAL_MOTIF_IDS[motifIndex]);
	}

	public SectionEditProfile applyGrammarTriggerDropdownIndex(SectionEditProfile profile, int dropdownIndex) {
		if (profile == null) return SectionEditProfile.defaults(0);
		if (dropdownIndex <= GRAMMAR_TRIGGER_AUTO_INDEX) {
			return profile.withGrammarTriggerInterval(null);
		}
		int intervalIndex = dropdownIndex - 1;
		if (intervalIndex < 0 || intervalIndex >= GRAMMAR_TRIGGER_INTERVALS.length) {
			return profile.withGrammarTriggerInterval(null);
		}
		return profile.withGrammarTriggerInterval(GRAMMAR_TRIGGER_INTERVALS[intervalIndex]);
	}

	public SectionEditProfile applyGrammarIntensityDropdownIndex(SectionEditProfile profile, int dropdownIndex) {
		if (profile == null) return SectionEditProfile.defaults(0);
		if (dropdownIndex <= GRAMMAR_INTENSITY_AUTO_INDEX) {
			return profile.withGrammarIntensityCurve(null);
		}
		int curveIndex = dropdownIndex - 1;
		if (curveIndex < 0 || curveIndex >= GRAMMAR_INTENSITY_CURVES.length) {
			return profile.withGrammarIntensityCurve(null);
		}
		return profile.withGrammarIntensityCurve(GRAMMAR_INTENSITY_CURVES[curveIndex]);
	}

	public SectionEditProfile applyGrammarVariationDropdownIndex(SectionEditProfile profile, int dropdownIndex) {
		if (profile == null) return SectionEditProfile.defaults(0);
		if (dropdownIndex <= GRAMMAR_VARIATION_AUTO_INDEX) {
			return profile.withGrammarVariation(null);
		}
		int variationIndex = dropdownIndex - 1;
		if (variationIndex < 0 || variationIndex >= GRAMMAR_VARIATION_KINDS.length) {
			return profile.withGrammarVariation(null);
		}
		return profile.withGrammarVariation(GRAMMAR_VARIATION_KINDS[variationIndex]);
	}

	public SectionEditProfile applyGrammarStaggerSeconds(SectionEditProfile profile, float staggerSeconds) {
		if (profile == null) return SectionEditProfile.defaults(0);
		return profile.withGrammarStaggerStep((double) Math.max(0f, staggerSeconds));
	}

	public SectionEditProfile applyGrammarDropdowns(
		SectionEditProfile profile,
		int spatialMotifIndex,
		int triggerIntervalIndex,
		int intensityIndex,
		int variationIndex,
		float staggerSeconds
	) {
		SectionEditProfile updated = applySpatialMotifDropdownIndex(profile, spatialMotifIndex);
		updated = applyGrammarTriggerDropdownIndex(updated, triggerIntervalIndex);
		updated = applyGrammarIntensityDropdownIndex(updated, intensityIndex);
		updated = applyGrammarVariationDropdownIndex(updated, variationIndex);
		return applyGrammarStaggerSeconds(updated, staggerSeconds);
	}

	public static int indexOfSpatialMotif(SpatialMotifId motifId) {
		if (motifId == null) return SPATIAL_MOTIF_AUTO_INDEX;
		for (int i = 0; i < SPATIAL_MOTIF_IDS.length; i++) {
			if (SPATIAL_MOTIF_IDS[i] == motifId) return i + 2;
		}
		return SPATIAL_MOTIF_AUTO_INDEX;
	}

	public static int indexOfGrammarTriggerInterval(int interval) {
		for (int i = 0; i < GRAMMAR_TRIGGER_INTERVALS.length; i++) {
			if (GRAMMAR_TRIGGER_INTERVALS[i] == interval) return i + 1;
		}
		return GRAMMAR_TRIGGER_AUTO_INDEX;
	}

	public static int indexOfGrammarIntensity(String curve) {
		if (curve == null || curve.isBlank()) return GRAMMAR_INTENSITY_AUTO_INDEX;
		for (int i = 0; i < GRAMMAR_INTENSITY_CURVES.length; i++) {
			if (GRAMMAR_INTENSITY_CURVES[i].equalsIgnoreCase(curve)) return i + 1;
		}
		return GRAMMAR_INTENSITY_AUTO_INDEX;
	}

	public static int indexOfGrammarVariation(String variation) {
		if (variation == null || variation.isBlank()) return GRAMMAR_VARIATION_AUTO_INDEX;
		for (int i = 0; i < GRAMMAR_VARIATION_KINDS.length; i++) {
			if (GRAMMAR_VARIATION_KINDS[i].equalsIgnoreCase(variation)) return i + 1;
		}
		return GRAMMAR_VARIATION_AUTO_INDEX;
	}

	private static SpatialMotifId resolveDisplayedSpatialMotif(ChoreographyPlan plan, int sectionIndex) {
		SectionEditProfile edit = ChoreographyPlanEditor.editForSection(plan, sectionIndex);
		if (edit != null && !edit.spatialMotifEnabled()) return null;
		if (edit != null && edit.spatialMotifIdOverride() != null) {
			return edit.spatialMotifIdOverride();
		}
		ChoreographyPhrase grammar = ChoreographyPlanEditor.primaryGrammarPhraseInSection(plan, sectionIndex);
		if (grammar != null) return grammar.spatial().resolvedPattern();
		SpatialMotifPhrase phrase = ChoreographyPlanEditor.primarySpatialMotifInSection(plan, sectionIndex);
		if (phrase != null) return phrase.motifId();
		if (sectionIndex < 0 || sectionIndex >= plan.sections().size()) return null;
		return SpatialMotifSelection.forSection(plan.sections().get(sectionIndex).sectionType());
	}

	private static @org.jspecify.annotations.Nullable Integer resolveDisplayedTriggerInterval(
		@org.jspecify.annotations.Nullable ChoreographyPhrase grammar,
		SectionType sectionType
	) {
		if (grammar != null && grammar.trigger() instanceof TriggerSpec.EveryNBeats everyN) {
			return everyN.interval();
		}
		TriggerSpec trigger = ChoreographyGrammarSelection.defaultTrigger(sectionType);
		if (trigger instanceof TriggerSpec.EveryNBeats everyN) {
			return everyN.interval();
		}
		return null;
	}

	public ApplyOutcome applySectionEdit(
		int sectionIndex,
		SectionType sectionType,
		boolean locked,
		SectionEditProfile edit
	) {
		String blocked = unavailableReason();
		if (blocked != null) {
			return new ApplyOutcome(PresenterResult.failure(blocked), 0, 0, 0);
		}
		Timeline timeline = currentTimeline();
		ChoreographyPlan plan = loadPlan();
		AutoMapConfig config = loadConfig();
		if (timeline == null || plan == null || config == null) {
			return new ApplyOutcome(PresenterResult.failure(BBTexts.get("beatblock.section_edit.no_plan")), 0, 0, 0);
		}

		plan = ChoreographyPlanEditor.updateSection(
			plan,
			sectionIndex,
			sectionType,
			resolveSectionLabel(plan.sections().get(sectionIndex), sectionType),
			locked
		);
		plan = ChoreographyPlanEditor.withSectionEdit(plan, edit.withSectionIndex(sectionIndex));
		plan = ChoreographyPlanEditor.bakePhraseOverrides(plan);
		ChoreographyPlanStore.save(timeline, plan, config);

		var compiled = ChoreographyPlanCompiler.compileSection(timeline, plan, sectionIndex);
		var editor = context.get().timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
		}
		return new ApplyOutcome(
			PresenterResult.success(BBTexts.get("beatblock.section_edit.applied")),
			compiled.animationEvents(),
			compiled.cameraEvents(),
			compiled.vfxEvents()
		);
	}

	private ChoreographyPlan loadPlan() {
		return ChoreographyPlanStore.loadPlan(currentTimeline());
	}

	private AutoMapConfig loadConfig() {
		AutoMapConfig config = ChoreographyPlanStore.loadConfig(currentTimeline());
		return config != null ? config : AutoMapConfig.createDefault();
	}

	private static String resolveSectionLabel(ChoreographyPlan.SectionPlan current, SectionType sectionType) {
		if (sectionType == current.sectionType() && !current.label().isBlank()) {
			return current.label();
		}
		return sectionType.name().toLowerCase(java.util.Locale.ROOT);
	}
}
