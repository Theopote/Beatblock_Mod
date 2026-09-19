package com.beatblock.automap.performance;

import com.beatblock.automap.camera.CameraCollisionPolicy;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotBeatAlignment;
import com.beatblock.automap.camera.CameraShotEasing;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraShotTransition;
import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.creator.CreationPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceProfileContractTest {

	@Test
	void presetFingerprintsDivergeOnDirectorAxes() {
		var pulse = CreationPreset.RHYTHM_PULSE.performanceProfile();
		var full = CreationPreset.FULL_CHOREOGRAPHY.performanceProfile();
		var reveal = CreationPreset.BUILD_REVEAL.performanceProfile();

		assertNotNull(pulse);
		assertNotNull(full);
		assertNotNull(reveal);

		assertEquals(CameraTemperament.OFF, pulse.camera());
		assertEquals(EventDensityPolicy.BEAT_HEAVY, pulse.density());
		assertEquals(LayerIntensity.OFF, pulse.hero());
		assertFalse(pulse.wantsCamera());
		assertFalse(pulse.wantsVfx());

		assertEquals(CameraTemperament.ENERGETIC, full.camera());
		assertEquals(EventDensityPolicy.SECTION_AWARE, full.density());
		assertEquals(LayerIntensity.HIGH, full.hero());
		assertTrue(full.wantsCamera());
		assertTrue(full.wantsVfx());

		assertEquals(CameraTemperament.SLOW_CINEMATIC, reveal.camera());
		assertEquals(EventDensityPolicy.SPARSE_CINEMATIC, reveal.density());
		assertTrue(reveal.wantsBuildPrimary());
		assertTrue(reveal.heroSectionEntrancesOnly());
		assertFalse(reveal.wantsVfx());
	}

	@Test
	void autoMapSettingsCarryPerformanceProfileAndDensityGaps() {
		var pulseSettings = CreationPreset.RHYTHM_PULSE.buildAutoMapSettings("obj");
		var fullSettings = CreationPreset.FULL_CHOREOGRAPHY.buildAutoMapSettings("obj");
		var revealSettings = CreationPreset.BUILD_REVEAL.buildAutoMapSettings("obj", "layer");

		assertNotNull(pulseSettings);
		assertNotNull(fullSettings);
		assertNotNull(revealSettings);

		assertNotNull(pulseSettings.getPerformanceProfile());
		assertTrue(pulseSettings.getMinGapLow() > 0);
		assertFalse(pulseSettings.isCameraEnabled());

		assertTrue(fullSettings.isCameraEnabled());
		assertTrue(fullSettings.isParticlesEnabled());
		// BEAT_HEAVY denser than SECTION_AWARE; SPARSE_CINEMATIC sparsest
		assertTrue(pulseSettings.getMinGapLow() < fullSettings.getMinGapLow());
		assertTrue(revealSettings.getMinGapLow() > fullSettings.getMinGapLow());

		assertEquals("layer", revealSettings.getBuildLayerId());
		assertTrue(revealSettings.isCameraEnabled());
		assertFalse(revealSettings.isParticlesEnabled());
	}

	@Test
	void rhythmPulseShaperKeepsAccentsDropsCameraAndHero() {
		ChoreographyPlan shaped = PerformancePlanShaper.apply(
			richPlan(), PerformanceProfile.rhythmPulse());

		assertFalse(shaped.motionPhrases().isEmpty());
		assertTrue(shaped.cameraPhrases().isEmpty());
		assertTrue(shaped.choreographyPhrases().stream().noneMatch(ChoreographyPhrase::isHero));
		assertTrue(shaped.vfxPhrases().isEmpty());
	}

	@Test
	void buildRevealShaperClearsSpatialAndMostMotion() {
		ChoreographyPlan shaped = PerformancePlanShaper.apply(
			richPlan(), PerformanceProfile.buildReveal());

		assertTrue(shaped.spatialMotifPhrases().isEmpty());
		assertTrue(shaped.motionPhrases().size() <= 2);
		assertTrue(shaped.vfxPhrases().isEmpty());
		assertTrue(shaped.cameraPhrases().stream()
			.noneMatch(c -> c.movement() != null && c.movement().toUpperCase().contains("SHAKE")));
	}

	@Test
	void fullChoreographyKeepsHeroAndCamera() {
		ChoreographyPlan plan = richPlan();
		ChoreographyPlan shaped = PerformancePlanShaper.apply(plan, PerformanceProfile.fullChoreography());

		assertTrue(shaped.choreographyPhrases().stream().anyMatch(ChoreographyPhrase::isHero));
		assertFalse(shaped.cameraPhrases().isEmpty());
		assertFalse(shaped.vfxPhrases().isEmpty());
	}

	@Test
	void slowCinematicFilterDropsShakeKeepsPush() {
		CameraShot hold = shot(CameraShotMovement.HOLD);
		CameraShot shake = shot(CameraShotMovement.SHAKE);
		CameraShot push = shot(CameraShotMovement.PUSH_IN);

		List<CameraShot> filtered = PerformancePlanShaper.filterShots(
			List.of(hold, shake, push), CameraTemperament.SLOW_CINEMATIC);

		assertEquals(2, filtered.size());
		assertTrue(filtered.stream().noneMatch(s -> s.movement() == CameraShotMovement.SHAKE));
	}

	@Test
	void offTemperamentClearsAllShots() {
		assertTrue(PerformancePlanShaper.filterShots(
			List.of(shot(CameraShotMovement.HOLD)), CameraTemperament.OFF).isEmpty());
	}

	private static CameraShot shot(CameraShotMovement movement) {
		return new CameraShot(
			0.0,
			1.0,
			CameraSubject.worldPosition(0, 64, 0),
			CameraShotFraming.MEDIUM,
			movement,
			CameraSubject.worldPosition(0, 64, 0),
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			0
		);
	}

	private static ChoreographyPlan richPlan() {
		List<ChoreographyPlan.MotionPhrase> motions = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			motions.add(new ChoreographyPlan.MotionPhrase(
				i * 0.5, "kick", "low", 0.3f + i * 0.07f, "pulse", 0.4, true, 4f, 0.0, 0));
		}
		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNFeatureHits("kick", 4),
			TargetSet.of("Tower_A"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.9f),
			VariationSpec.none(),
			0,
			ChoreographyTimingSnap.BEAT,
			ChoreographyLayer.PHRASE
		);
		ChoreographyPhrase hero = new ChoreographyPhrase(
			new TriggerSpec.OnFeature("low"),
			TargetSet.of("Tower_A", "Tower_B"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			new TimingPatternSpec.Simultaneous(),
			IntensityEnvelope.flat(1.0f),
			VariationSpec.none(),
			0,
			ChoreographyTimingSnap.SECTION,
			ChoreographyLayer.HERO
		);
		SpatialMotifPhrase spatial = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.CASCADE,
			List.of("Tower_A", "Tower_B"),
			MotifAxis.X,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		ChoreographyPlan.CameraPhrase pan = new ChoreographyPlan.CameraPhrase(
			2.0, "PAN", 0, "", "", 3.0, "", "PAN", "", false,
			ChoreographyTimingSnap.BAR, "");
		ChoreographyPlan.CameraPhrase shake = new ChoreographyPlan.CameraPhrase(
			4.0, "SHAKE", 0, "", "", 0.3, "", "SHAKE", "", false,
			ChoreographyTimingSnap.BAR, "");
		ChoreographyVfx burst = new ChoreographyVfx.ParticleBurst(
			1.0, "burst", "minecraft:poof", CameraSubject.allStageObjects(), 8, 0.5, 0.2, 0);
		return new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.DROP, "drop")),
			List.of(new ChoreographyPlan.StageRoleAssignment("low", "Tower_A")),
			motions,
			List.of(pan, shake),
			List.of(burst),
			DensityCurve.uniform(0.8),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(spatial),
			List.of(phrase, hero)
		);
	}
}
