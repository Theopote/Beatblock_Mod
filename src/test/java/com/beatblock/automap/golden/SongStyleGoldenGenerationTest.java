package com.beatblock.automap.golden;

import com.beatblock.creator.CreationPreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 黄金生成契约：四类曲风在 FULL_CHOREOGRAPHY 下必须呈现可区分指纹。
 */
class SongStyleGoldenGenerationTest {

	@Test
	void edmHasStrongDropBreathingAndImpactVfx() {
		GenerationFingerprint fp = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.FULL_CHOREOGRAPHY);

		assertTrue(fp.motionCount() > 0);
		assertTrue(fp.dropOrClimaxMotionDensity() > fp.introMotionDensity() * 1.5,
			"EDM drop should be denser than intro: drop=" + fp.dropOrClimaxMotionDensity()
				+ " intro=" + fp.introMotionDensity());
		assertTrue(fp.shakeCameraCount() + fp.orbitCameraCount() > 0, "EDM drop cameras should include orbit/shake");
		assertTrue(fp.hasDropEntranceFlash() || fp.structuralVfxCount() > 0);
		assertTrue(fp.vfxCount() > 0);
	}

	@Test
	void popEmphasizesChorusHeroAndRepeatedVerseBreathing() {
		GenerationFingerprint fp = SongStyleGenerationHarness.generate(
			SongStyleFixture.POP, CreationPreset.FULL_CHOREOGRAPHY);

		assertTrue(fp.heroCount() > 0, "Pop should keep hero grammar");
		assertTrue(fp.dropOrClimaxMotionDensity() > fp.verseMotionDensity(),
			"chorus denser than verse: chorus=" + fp.dropOrClimaxMotionDensity()
				+ " verse=" + fp.verseMotionDensity());
		assertTrue(fp.hasDropEntranceFlash() || fp.hasHeroBurst() || fp.structuralVfxCount() > 0);
		assertTrue(fp.cameraCount() > 0);
	}

	@Test
	void cinematicIsSparseEarlyWithPushInBuildArc() {
		GenerationFingerprint fp = SongStyleGenerationHarness.generate(
			SongStyleFixture.CINEMATIC, CreationPreset.FULL_CHOREOGRAPHY);

		assertTrue(fp.introMotionDensity() < fp.dropOrClimaxMotionDensity(),
			"cinematic climax denser than intro");
		assertTrue(fp.pushInCameraCount() > 0, "long BUILD should yield PUSH_IN");
		assertTrue(fp.motionCount() > 0);
	}

	@Test
	void percussiveIsMotionDenseWithFewerImpactCamerasThanEdm() {
		GenerationFingerprint perc = SongStyleGenerationHarness.generate(
			SongStyleFixture.PERCUSSIVE, CreationPreset.FULL_CHOREOGRAPHY);
		GenerationFingerprint edm = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.FULL_CHOREOGRAPHY);

		double percRate = perc.motionCount() / 32.0;
		double edmRate = edm.motionCount() / 64.0;
		assertTrue(percRate > edmRate * 0.85,
			"percussive motion rate should rival or exceed EDM: perc=" + percRate + " edm=" + edmRate);
		assertTrue(perc.shakeCameraCount() <= edm.shakeCameraCount(),
			"verse-heavy perc should not out-shake EDM drops");
		assertTrue(perc.verseMotionDensity() > 0);
	}

	@Test
	void stylesDivergeOnCombinedFingerprintAxes() {
		GenerationFingerprint edm = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.FULL_CHOREOGRAPHY);
		GenerationFingerprint pop = SongStyleGenerationHarness.generate(
			SongStyleFixture.POP, CreationPreset.FULL_CHOREOGRAPHY);
		GenerationFingerprint cine = SongStyleGenerationHarness.generate(
			SongStyleFixture.CINEMATIC, CreationPreset.FULL_CHOREOGRAPHY);
		GenerationFingerprint perc = SongStyleGenerationHarness.generate(
			SongStyleFixture.PERCUSSIVE, CreationPreset.FULL_CHOREOGRAPHY);

		assertTrue(edm.dropOrClimaxMotionDensity() > cine.introMotionDensity());
		assertTrue(edm.shakeCameraCount() >= cine.shakeCameraCount());
		assertTrue(pop.heroCount() >= cine.heroCount() || pop.structuralVfxCount() >= 1);
		assertTrue(perc.verseMotionDensity() > cine.introMotionDensity());
		assertTrue(edm.vfxCount() > 0 && pop.vfxCount() > 0);
		assertTrue(cine.pushInCameraCount() >= 1);
		assertFalse(edm.hasBuildSequence());
	}

	@Test
	void presetsDivergeOnSameEdmFixture() {
		GenerationFingerprint full = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.FULL_CHOREOGRAPHY);
		GenerationFingerprint pulse = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.RHYTHM_PULSE);
		GenerationFingerprint reveal = SongStyleGenerationHarness.generate(
			SongStyleFixture.EDM, CreationPreset.BUILD_REVEAL);

		assertTrue(full.cameraCount() > pulse.cameraCount());
		assertTrue(full.vfxCount() > pulse.vfxCount());
		assertTrue(pulse.vfxCount() == 0);
		assertTrue(reveal.hasBuildSequence());
		assertTrue(reveal.vfxCount() == 0);
		assertTrue(reveal.pushInCameraCount() + reveal.orbitCameraCount() + reveal.holdCameraCount() > 0);
		assertTrue(full.heroCount() >= pulse.heroCount());
	}
}
