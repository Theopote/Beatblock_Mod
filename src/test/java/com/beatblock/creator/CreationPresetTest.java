package com.beatblock.creator;

import com.beatblock.ui.presenter.ProjectTemplatePresenter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreationPresetTest {

	@Test
	void wizardPresetsExposeFiveVideoTypes() {
		assertEquals(5, CreationPreset.WIZARD_PRESETS.length);
		assertEquals(CreationPreset.BUILD_REVEAL, CreationPreset.WIZARD_PRESETS[0]);
		assertEquals(CreationPreset.FULL_CHOREOGRAPHY, CreationPreset.WIZARD_PRESETS[4]);
	}

	@Test
	void projectTemplateMappingIsSymmetricForSupportedTypes() {
		assertEquals(CreationPreset.RHYTHM_PATH,
			CreationPreset.fromProjectTemplate(ProjectTemplatePresenter.TemplateId.RHYTHM_PARKOUR));
		assertEquals(CreationPreset.BUILD_REVEAL,
			CreationPreset.fromProjectTemplate(ProjectTemplatePresenter.TemplateId.ARCHITECTURAL_SHOW));
		assertEquals(CreationPreset.BLANK,
			CreationPreset.fromProjectTemplate(ProjectTemplatePresenter.TemplateId.BLANK));

		assertEquals(ProjectTemplatePresenter.TemplateId.RHYTHM_PARKOUR,
			CreationPreset.RHYTHM_PATH.toProjectTemplate());
		assertEquals(ProjectTemplatePresenter.TemplateId.ARCHITECTURAL_SHOW,
			CreationPreset.BUILD_REVEAL.toProjectTemplate());
	}

	@Test
	void generationStrategyMatchesProductIntent() {
		assertEquals(CreationPreset.GenerationStrategy.SMART_AUTO_MAP,
			CreationPreset.BUILD_REVEAL.generationStrategy());
		assertEquals(CreationPreset.GenerationStrategy.BINDING_TEMPLATE_MAP,
			CreationPreset.RHYTHM_PATH.generationStrategy());
		assertEquals(CreationPreset.GenerationStrategy.RHYTHM_DROP,
			CreationPreset.DROP_IMPACT.generationStrategy());
		assertFalse(CreationPreset.DROP_IMPACT.requiresFullAudioAnalysis());
		assertTrue(CreationPreset.FULL_CHOREOGRAPHY.wantsCamera());
		assertTrue(CreationPreset.FULL_CHOREOGRAPHY.wantsVfx());
		assertFalse(CreationPreset.RHYTHM_PULSE.wantsCamera());
		assertTrue(CreationPreset.BUILD_REVEAL.wantsBuildLayer());
		assertFalse(CreationPreset.RHYTHM_PULSE.wantsBuildLayer());
	}

	@Test
	void fromWizardIndexClampsOutOfRange() {
		assertEquals(CreationPreset.FULL_CHOREOGRAPHY, CreationPreset.fromWizardIndex(99));
		assertEquals(CreationPreset.BUILD_REVEAL, CreationPreset.fromWizardIndex(0));
	}

	@Test
	void timelineTemplatePresetsMatchLegacyTemplates() {
		assertEquals(3, CreationPreset.TIMELINE_TEMPLATE_PRESETS.length);
		assertTrue(CreationPreset.BLANK.supportsTimelineTemplateApply());
		assertTrue(CreationPreset.BUILD_REVEAL.supportsTimelineTemplateApply());
		assertTrue(CreationPreset.RHYTHM_PATH.supportsTimelineTemplateApply());
		assertFalse(CreationPreset.FULL_CHOREOGRAPHY.supportsTimelineTemplateApply());
	}

	@Test
	void doneRefinementTargetMatchesPresetIntent() {
		assertEquals(CreationPreset.DoneRefinementTarget.BINDING_EDITOR,
			CreationPreset.RHYTHM_PATH.doneRefinementTarget());
		assertEquals(CreationPreset.DoneRefinementTarget.RHYTHM_DROP,
			CreationPreset.DROP_IMPACT.doneRefinementTarget());
		assertEquals(CreationPreset.DoneRefinementTarget.SECTION_EDIT,
			CreationPreset.FULL_CHOREOGRAPHY.doneRefinementTarget());
		assertEquals(CreationPreset.DoneRefinementTarget.SECTION_EDIT,
			CreationPreset.BUILD_REVEAL.doneRefinementTarget());
		assertEquals(CreationPreset.DoneRefinementTarget.TIMELINE,
			CreationPreset.BLANK.doneRefinementTarget());
	}

	@Test
	void doneRefinementHintKeyReflectsChoreographyPlan() {
		assertEquals("beatblock.wizard.done.refinement.section_edit",
			CreationPreset.FULL_CHOREOGRAPHY.doneRefinementHintKey(true));
		assertEquals("beatblock.wizard.done.refinement.timeline_only",
			CreationPreset.FULL_CHOREOGRAPHY.doneRefinementHintKey(false));
	}

	@Test
	void autoMapSettingsOnlyForSmartAutoMapPresets() {
		assertNotNull(CreationPreset.FULL_CHOREOGRAPHY.buildAutoMapSettings("stage_a"));
		assertNull(CreationPreset.RHYTHM_PATH.buildAutoMapSettings("stage_a"));
		assertNull(CreationPreset.DROP_IMPACT.buildAutoMapSettings("stage_a"));
	}
}
