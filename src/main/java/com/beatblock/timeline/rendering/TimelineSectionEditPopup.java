package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineSectionEditPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.List;
import java.util.Locale;

/**
 * Section 编舞编辑弹窗：按音乐段落调整类型、锁定、motion/camera/vfx 开关与覆盖参数。
 */
public final class TimelineSectionEditPopup {

	public static final String POPUP_ID = "tlSectionEdit";

	private final TimelineSectionEditPresenter presenter;
	private final TimelineToolbarFeedbackPresenter feedback;
	private final ImInt selectedSectionIndex = new ImInt(0);
	private final ImInt sectionTypeIndex = new ImInt(0);
	private final ImBoolean sectionLocked = new ImBoolean(false);
	private final ImBoolean motionEnabled = new ImBoolean(true);
	private final ImBoolean cameraEnabled = new ImBoolean(true);
	private final ImBoolean vfxEnabled = new ImBoolean(true);
	private final ImInt animationTypeIndex = new ImInt(0);
	private final ImInt spatialMotifIndex = new ImInt(0);
	private final ImInt grammarTriggerIndex = new ImInt(0);
	private final ImInt grammarIntensityIndex = new ImInt(0);
	private final ImInt grammarVariationIndex = new ImInt(0);
	private final ImFloat grammarStaggerSeconds = new ImFloat(0.06f);
	private final ImFloat densityThreshold = new ImFloat(0.15f);
	private final ImFloat timeOffsetSeconds = new ImFloat(0f);
	private final ImFloat energyScale = new ImFloat(1f);
	private boolean profileDirty;

	public TimelineSectionEditPopup(
		TimelineSectionEditPresenter presenter,
		TimelineToolbarFeedbackPresenter feedback
	) {
		this.presenter = presenter;
		this.feedback = feedback;
	}

	public void prepareForOpen(int sectionIndex) {
		if (sectionIndex >= 0) {
			selectedSectionIndex.set(sectionIndex);
		}
		profileDirty = false;
		loadProfileForSelectedSection();
	}

	public void renderIfOpen() {
		if (!ImGui.beginPopup(POPUP_ID)) return;
		String blocked = presenter.unavailableReason();
		if (blocked != null) {
			ImGui.textDisabled(blocked);
			ImGui.endPopup();
			return;
		}

		List<TimelineSectionEditPresenter.SectionView> sections = presenter.listSections();
		if (sections.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.section_edit.no_sections"));
			ImGui.endPopup();
			return;
		}

		if (selectedSectionIndex.get() >= sections.size()) {
			selectedSectionIndex.set(0);
		}
		if (!profileDirty) {
			loadProfileForSelectedSection();
		}

		renderSectionSelector(sections);
		ImGui.separator();
		renderSelectedSectionSummary(sections.get(selectedSectionIndex.get()));
		ImGui.separator();
		renderStructureFields();
		ImGui.separator();
		renderEditFields();
		ImGui.separator();
		renderApplyButton();

		ImGui.endPopup();
	}

	private void renderSectionSelector(List<TimelineSectionEditPresenter.SectionView> sections) {
		ImGui.textDisabled(BBTexts.get("beatblock.section_edit.sections"));
		for (TimelineSectionEditPresenter.SectionView section : sections) {
			String sourceTag = sourceLabel(section.source());
			String label = String.format(Locale.ROOT, "%s (%.1f-%.1fs) %s",
				section.label(), section.startSeconds(), section.endSeconds(), sourceTag);
			if (ImGui.selectable(label, selectedSectionIndex.get() == section.index())) {
				selectedSectionIndex.set(section.index());
				profileDirty = false;
				loadProfileForSelectedSection();
			}
		}
	}

	private void renderSelectedSectionSummary(TimelineSectionEditPresenter.SectionView section) {
		ImGui.text(BBTexts.get("beatblock.section_edit.summary",
			section.motionCount(), section.cameraCount(), section.vfxCount(), section.grammarPhraseCount()));
		String motifLabel = section.spatialMotifId() != null
			? section.spatialMotifId().name()
			: BBTexts.get("beatblock.section_edit.spatial_motif.none");
		ImGui.textDisabled(BBTexts.get("beatblock.section_edit.spatial_motif.current", motifLabel));
		if (section.grammarPhraseCount() > 0) {
			String motion = section.grammarMotionPreset() != null ? section.grammarMotionPreset() : "-";
			Integer triggerInterval = section.grammarTriggerInterval();
			String trigger = triggerInterval != null
				? BBTexts.get("beatblock.section_edit.grammar.trigger.every_n", triggerInterval)
				: BBTexts.get("beatblock.section_edit.grammar.trigger.auto");
			ImGui.textDisabled(BBTexts.get(
				"beatblock.section_edit.grammar.current",
				trigger,
				motion
			));
		}
		ImGui.textDisabled(BBTexts.get(
			"beatblock.section_edit.confidence",
			(int) Math.round(section.confidence() * 100.0),
			sourceLabel(section.source())
		));
	}

	private void renderStructureFields() {
		String[] typeLabels = sectionTypeLabels();
		if (ImGui.combo(BBTexts.get("beatblock.section_edit.section_type"), sectionTypeIndex, typeLabels)) {
			profileDirty = true;
		}
		if (ImGui.checkbox(BBTexts.get("beatblock.section_edit.lock_section"), sectionLocked)) {
			profileDirty = true;
		}
		if (sectionLocked.get()) {
			ImGui.sameLine();
			ImGui.textDisabled(BBTexts.get("beatblock.section_edit.lock_hint"));
		}
	}

	private void renderEditFields() {
		if (ImGui.checkbox(BBTexts.get("beatblock.section_edit.motion_enabled"), motionEnabled)) profileDirty = true;
		ImGui.sameLine();
		if (ImGui.checkbox(BBTexts.get("beatblock.section_edit.camera_enabled"), cameraEnabled)) profileDirty = true;
		ImGui.sameLine();
		if (ImGui.checkbox(BBTexts.get("beatblock.section_edit.vfx_enabled"), vfxEnabled)) profileDirty = true;

		if (ImGui.combo(BBTexts.get("beatblock.section_edit.motion_animation"), animationTypeIndex,
			TimelineSectionEditPresenter.MOTION_ANIMATION_IDS)) {
			profileDirty = true;
		}
		if (ImGui.combo(BBTexts.get("beatblock.section_edit.spatial_motif"), spatialMotifIndex,
			TimelineSectionEditPresenter.spatialMotifDropdownLabels())) {
			profileDirty = true;
		}
		if (ImGui.combo(BBTexts.get("beatblock.section_edit.grammar.trigger"), grammarTriggerIndex,
			TimelineSectionEditPresenter.grammarTriggerIntervalLabels())) {
			profileDirty = true;
		}
		if (ImGui.inputFloat(BBTexts.get("beatblock.section_edit.grammar.stagger"), grammarStaggerSeconds, 0.01f, 0.02f, "%.2f")) {
			profileDirty = true;
		}
		if (ImGui.combo(BBTexts.get("beatblock.section_edit.grammar.intensity"), grammarIntensityIndex,
			TimelineSectionEditPresenter.grammarIntensityLabels())) {
			profileDirty = true;
		}
		if (ImGui.combo(BBTexts.get("beatblock.section_edit.grammar.variation"), grammarVariationIndex,
			TimelineSectionEditPresenter.grammarVariationLabels())) {
			profileDirty = true;
		}
		if (ImGui.inputFloat(BBTexts.get("beatblock.section_edit.density_threshold"), densityThreshold, 0.01f, 0.05f, "%.2f")) {
			profileDirty = true;
		}
		if (ImGui.inputFloat(BBTexts.get("beatblock.section_edit.time_offset"), timeOffsetSeconds, 0.05f, 0.25f, "%.2f")) {
			profileDirty = true;
		}
		if (ImGui.inputFloat(BBTexts.get("beatblock.section_edit.energy_scale"), energyScale, 0.05f, 0.1f, "%.2f")) {
			profileDirty = true;
		}
	}

	private void renderApplyButton() {
		if (ImGui.button(BBTexts.get("beatblock.section_edit.apply"), 140, 0)) {
			int sectionIndex = selectedSectionIndex.get();
			int animIndex = Math.max(0, Math.min(animationTypeIndex.get(),
				TimelineSectionEditPresenter.MOTION_ANIMATION_IDS.length - 1));
			SectionType sectionType = TimelineSectionEditPresenter.SECTION_TYPES.get(
				Math.max(0, Math.min(sectionTypeIndex.get(), TimelineSectionEditPresenter.SECTION_TYPES.size() - 1)));
			SectionEditProfile edit = presenter.applyGrammarDropdowns(
				new SectionEditProfile(
					sectionIndex,
					motionEnabled.get(),
					cameraEnabled.get(),
					vfxEnabled.get(),
					TimelineSectionEditPresenter.MOTION_ANIMATION_IDS[animIndex],
					(double) densityThreshold.get(),
					timeOffsetSeconds.get(),
					energyScale.get()
				),
				spatialMotifIndex.get(),
				grammarTriggerIndex.get(),
				grammarIntensityIndex.get(),
				grammarVariationIndex.get(),
				grammarStaggerSeconds.get()
			);
			var outcome = presenter.applySectionEdit(
				sectionIndex, sectionType, sectionLocked.get(), edit);
			feedback.setTemplateApplyFeedback(outcome.result().messageOrEmpty(), outcome.result().ok());
			profileDirty = false;
		}
		TimelineToolbarImGui.renderFeedback(feedback.viewTemplateApplyFeedback());
	}

	private void loadProfileForSelectedSection() {
		List<TimelineSectionEditPresenter.SectionView> sections = presenter.listSections();
		int index = selectedSectionIndex.get();
		if (index >= 0 && index < sections.size()) {
			TimelineSectionEditPresenter.SectionView section = sections.get(index);
			sectionTypeIndex.set(indexOfSectionType(section.sectionType()));
			sectionLocked.set(section.source() == SectionPlanSource.LOCKED);
		}
		SectionEditProfile profile = presenter.loadEditProfile(index);
		motionEnabled.set(profile.motionEnabled());
		cameraEnabled.set(profile.cameraEnabled());
		vfxEnabled.set(profile.vfxEnabled());
		animationTypeIndex.set(indexOfAnimation(profile.motionAnimationTypeOverride()));
		spatialMotifIndex.set(presenter.resolveSpatialMotifDropdownIndex(index, profile));
		grammarTriggerIndex.set(presenter.resolveGrammarTriggerDropdownIndex(index, profile));
		grammarIntensityIndex.set(presenter.resolveGrammarIntensityDropdownIndex(index, profile));
		grammarVariationIndex.set(presenter.resolveGrammarVariationDropdownIndex(index, profile));
		grammarStaggerSeconds.set(presenter.resolveGrammarStaggerSeconds(index, profile));
		Double densityOverride = profile.densityThresholdOverride();
		densityThreshold.set(densityOverride != null ? densityOverride.floatValue() : 0.15f);
		timeOffsetSeconds.set((float) profile.timeOffsetSeconds());
		energyScale.set(profile.energyScale());
	}

	private static String[] sectionTypeLabels() {
		List<SectionType> types = TimelineSectionEditPresenter.SECTION_TYPES;
		String[] labels = new String[types.size()];
		for (int i = 0; i < types.size(); i++) {
			labels[i] = types.get(i).name();
		}
		return labels;
	}

	private static int indexOfSectionType(SectionType type) {
		List<SectionType> types = TimelineSectionEditPresenter.SECTION_TYPES;
		for (int i = 0; i < types.size(); i++) {
			if (types.get(i) == type) return i;
		}
		return 0;
	}

	private static int indexOfAnimation(String animationTypeId) {
		if (animationTypeId == null) return 0;
		for (int i = 0; i < TimelineSectionEditPresenter.MOTION_ANIMATION_IDS.length; i++) {
			if (animationTypeId.equals(TimelineSectionEditPresenter.MOTION_ANIMATION_IDS[i])) return i;
		}
		return 0;
	}

	private static String sourceLabel(SectionPlanSource source) {
		if (source == null) return "";
		return switch (source) {
			case ANALYZED -> BBTexts.get("beatblock.section_edit.source.analyzed");
			case USER_EDITED -> BBTexts.get("beatblock.section_edit.source.user_edited");
			case LOCKED -> BBTexts.get("beatblock.section_edit.source.locked");
		};
	}
}
