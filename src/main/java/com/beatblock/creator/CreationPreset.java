package com.beatblock.creator;

import com.beatblock.automap.choreography.ChoreographyLayerProfile;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.ProjectTemplatePresenter;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 用户可见的创作预设：统一 Quick Start 视频类型与工程模板的产品概念。
 * AutoMap Style、Binding Template、BUILD/BURST 等仍作为内部实现细节。
 */
public enum CreationPreset {
	BUILD_REVEAL,
	RHYTHM_PULSE,
	RHYTHM_PATH,
	DROP_IMPACT,
	FULL_CHOREOGRAPHY,
	BLANK;

	public static final CreationPreset[] WIZARD_PRESETS = {
		BUILD_REVEAL,
		RHYTHM_PULSE,
		RHYTHM_PATH,
		DROP_IMPACT,
		FULL_CHOREOGRAPHY
	};

	/** 可对当前时间线直接应用的工程模板（原「从模板新建」）。 */
	public static final CreationPreset[] TIMELINE_TEMPLATE_PRESETS = {
		BLANK,
		BUILD_REVEAL,
		RHYTHM_PATH
	};

	public enum GenerationStrategy {
		SMART_AUTO_MAP,
		BINDING_TEMPLATE_MAP,
		RHYTHM_DROP,
		NONE
	}

	/** Quick Start DONE 页「精修编舞」按钮应打开的目标。 */
	public enum DoneRefinementTarget {
		SECTION_EDIT,
		BINDING_EDITOR,
		RHYTHM_DROP,
		TIMELINE
	}

	public GenerationStrategy generationStrategy() {
		return switch (this) {
			case BUILD_REVEAL, RHYTHM_PULSE, FULL_CHOREOGRAPHY -> GenerationStrategy.SMART_AUTO_MAP;
			case RHYTHM_PATH -> GenerationStrategy.BINDING_TEMPLATE_MAP;
			case DROP_IMPACT -> GenerationStrategy.RHYTHM_DROP;
			case BLANK -> GenerationStrategy.NONE;
		};
	}

	/** Drop Impact 仅需节拍网格；其余向导类型需要完整音频分析。 */
	public boolean requiresFullAudioAnalysis() {
		return this != DROP_IMPACT;
	}

	public boolean wantsCamera() {
		return this == BUILD_REVEAL || this == FULL_CHOREOGRAPHY;
	}

	public boolean wantsVfx() {
		return this == FULL_CHOREOGRAPHY;
	}

	public String titleKey() {
		return switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.style.cinematic";
			case RHYTHM_PULSE -> "beatblock.wizard.style.rhythmic";
			case RHYTHM_PATH -> "beatblock.wizard.style.rhythm_path";
			case DROP_IMPACT -> "beatblock.wizard.style.drop";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.style.full";
			case BLANK -> "beatblock.template.blank";
		};
	}

	public String cardDescKey() {
		return switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.preset.build_reveal.desc";
			case RHYTHM_PULSE -> "beatblock.wizard.preset.rhythm_pulse.desc";
			case RHYTHM_PATH -> "beatblock.wizard.preset.rhythm_path.desc";
			case DROP_IMPACT -> "beatblock.wizard.preset.drop_impact.desc";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.preset.full_choreography.desc";
			case BLANK -> "beatblock.template.blank.desc";
		};
	}

	public String tooltipKey() {
		return switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.style.cinematic.tooltip";
			case RHYTHM_PULSE -> "beatblock.wizard.style.rhythmic.tooltip";
			case RHYTHM_PATH -> "beatblock.wizard.style.rhythm_path.tooltip";
			case DROP_IMPACT -> "beatblock.wizard.style.drop.tooltip";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.style.full.tooltip";
			case BLANK -> "beatblock.template.blank.desc";
		};
	}

	public String styleLabel() {
		return BBTexts.get(titleKey());
	}

	public String animationPlanSummary() {
		return BBTexts.get(switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.plan.animation.cinematic";
			case RHYTHM_PULSE -> "beatblock.wizard.plan.animation.rhythmic";
			case RHYTHM_PATH -> "beatblock.wizard.plan.animation.rhythm_path";
			case DROP_IMPACT -> "beatblock.wizard.plan.animation.drop";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.plan.animation.full";
			case BLANK -> "beatblock.wizard.plan.animation.blank";
		});
	}

	public String cameraPlanSummary() {
		return BBTexts.get(switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.plan.camera.restrained";
			case RHYTHM_PULSE, RHYTHM_PATH, DROP_IMPACT -> "beatblock.wizard.plan.camera.off";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.plan.camera.auto";
			case BLANK -> "beatblock.wizard.plan.camera.off";
		});
	}

	public String vfxPlanSummary() {
		return BBTexts.get(switch (this) {
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.plan.vfx.auto";
			case DROP_IMPACT -> "beatblock.wizard.plan.vfx.impact";
			default -> "beatblock.wizard.plan.vfx.off";
		});
	}

	public String generatedMessageKey() {
		return switch (this) {
			case BUILD_REVEAL -> "beatblock.wizard.generated_cinematic";
			case RHYTHM_PULSE -> "beatblock.wizard.generated_rhythmic";
			case RHYTHM_PATH -> "beatblock.wizard.generated_rhythm_path";
			case DROP_IMPACT -> "beatblock.wizard.generated_drop";
			case FULL_CHOREOGRAPHY -> "beatblock.wizard.generated_full";
			case BLANK -> "beatblock.template.blank.applied";
		};
	}

	public int wizardIndex() {
		for (int i = 0; i < WIZARD_PRESETS.length; i++) {
			if (WIZARD_PRESETS[i] == this) {
				return i;
			}
		}
		return WIZARD_PRESETS.length - 1;
	}

	public static CreationPreset fromWizardIndex(int index) {
		if (index < 0 || index >= WIZARD_PRESETS.length) {
			return FULL_CHOREOGRAPHY;
		}
		return WIZARD_PRESETS[index];
	}

	public @Nullable AutoMapSettings buildAutoMapSettings(String objectId) {
		if (generationStrategy() != GenerationStrategy.SMART_AUTO_MAP || objectId == null || objectId.isBlank()) {
			return null;
		}
		return switch (this) {
			case BUILD_REVEAL -> buildAutoMapSettings(
				AutoMapStyle.CINEMATIC, Complexity.MEDIUM, true, false,
				ChoreographyLayerProfile.PHRASE, objectId
			);
			case RHYTHM_PULSE -> buildAutoMapSettings(
				AutoMapStyle.EDM, Complexity.MEDIUM, false, false,
				ChoreographyLayerProfile.PHRASE, objectId
			);
			case FULL_CHOREOGRAPHY -> buildAutoMapSettings(
				AutoMapStyle.EDM, Complexity.MEDIUM, true, true,
				ChoreographyLayerProfile.HERO_FULL, objectId
			);
			default -> null;
		};
	}

	/** {@link com.beatblock.ui.presenter.TimelineBindingEditorPresenter} 模板索引。 */
	public int bindingTemplateIndex() {
		return switch (this) {
			case RHYTHM_PATH -> 0;
			case BUILD_REVEAL -> 1;
			default -> -1;
		};
	}

	public static @Nullable CreationPreset fromProjectTemplate(ProjectTemplatePresenter.TemplateId templateId) {
		if (templateId == null) {
			return null;
		}
		return switch (templateId) {
			case BLANK -> BLANK;
			case RHYTHM_PARKOUR -> RHYTHM_PATH;
			case ARCHITECTURAL_SHOW -> BUILD_REVEAL;
		};
	}

	public DoneRefinementTarget doneRefinementTarget() {
		return switch (this) {
			case RHYTHM_PATH -> DoneRefinementTarget.BINDING_EDITOR;
			case DROP_IMPACT -> DoneRefinementTarget.RHYTHM_DROP;
			case BUILD_REVEAL, RHYTHM_PULSE, FULL_CHOREOGRAPHY -> DoneRefinementTarget.SECTION_EDIT;
			default -> DoneRefinementTarget.TIMELINE;
		};
	}

	public String doneEditChoreographyLabelKey() {
		return switch (doneRefinementTarget()) {
			case BINDING_EDITOR -> "beatblock.wizard.done.edit_bindings";
			case RHYTHM_DROP -> "beatblock.wizard.done.edit_rhythm_drop";
			case SECTION_EDIT -> "beatblock.wizard.done.edit_section";
			case TIMELINE -> "beatblock.wizard.done.edit_choreography";
		};
	}

	public String doneRefinementHintKey(boolean hasChoreographyPlan) {
		return switch (this) {
			case RHYTHM_PATH -> "beatblock.wizard.done.refinement.rhythm_path";
			case DROP_IMPACT -> "beatblock.wizard.done.refinement.drop_impact";
			case BUILD_REVEAL, RHYTHM_PULSE, FULL_CHOREOGRAPHY -> hasChoreographyPlan
				? "beatblock.wizard.done.refinement.section_edit"
				: "beatblock.wizard.done.refinement.timeline_only";
			default -> "beatblock.wizard.done.refinement.timeline_only";
		};
	}

	public boolean supportsTimelineTemplateApply() {
		return toProjectTemplate() != null;
	}

	public ProjectTemplatePresenter.@Nullable TemplateId toProjectTemplate() {
		return switch (this) {
			case BLANK -> ProjectTemplatePresenter.TemplateId.BLANK;
			case RHYTHM_PATH -> ProjectTemplatePresenter.TemplateId.RHYTHM_PARKOUR;
			case BUILD_REVEAL -> ProjectTemplatePresenter.TemplateId.ARCHITECTURAL_SHOW;
			default -> null;
		};
	}

	private static AutoMapSettings buildAutoMapSettings(
		AutoMapStyle style,
		Complexity complexity,
		boolean camera,
		boolean particles,
		ChoreographyLayerProfile layerProfile,
		String objectId
	) {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(style);
		settings.setComplexity(complexity);
		settings.setCameraEnabled(camera);
		settings.setParticlesEnabled(particles);
		settings.setLayerProfile(layerProfile);
		settings.setTargetObjectIds(List.of(objectId));
		return settings;
	}
}
