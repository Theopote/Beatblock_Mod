package com.beatblock.automap.vfx;

import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.performance.PerformanceProfile;
import com.beatblock.creator.CreationPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxPlannerTest {

	@Test
	void fullChoreographyGetsSectionEdgeFlashAndHeroBurst() {
		ChoreographyPlan plan = samplePlan();
		AutoMapSettings settings = CreationPreset.FULL_CHOREOGRAPHY.buildAutoMapSettings("tower");

		ChoreographyPlan shaped = VfxPlanner.apply(plan, peakBands(), settings);

		assertTrue(shaped.vfxPhrases().stream().anyMatch(v ->
			v instanceof ChoreographyVfx.ScreenFlash flash && "drop_entrance".equals(flash.name())));
		assertTrue(shaped.vfxPhrases().stream().anyMatch(v ->
			v instanceof ChoreographyVfx.ParticleBurst burst && "hero_burst".equals(burst.name())));
		assertTrue(shaped.vfxPhrases().stream().anyMatch(v ->
			v instanceof ChoreographyVfx.EnvironmentLighting));
		assertTrue(shaped.vfxPhrases().stream().anyMatch(v ->
			v instanceof ChoreographyVfx.ScreenTint tint && "outro_fade".equals(tint.name())));
	}

	@Test
	void rhythmPulseClearsVfx() {
		AutoMapSettings settings = CreationPreset.RHYTHM_PULSE.buildAutoMapSettings("tower");
		ChoreographyPlan shaped = VfxPlanner.apply(samplePlan(), peakBands(), settings);
		assertTrue(shaped.vfxPhrases().isEmpty());
	}

	@Test
	void buildRevealClearsVfx() {
		AutoMapSettings settings = CreationPreset.BUILD_REVEAL.buildAutoMapSettings("tower", "layer");
		ChoreographyPlan shaped = VfxPlanner.apply(samplePlan(), peakBands(), settings);
		assertTrue(shaped.vfxPhrases().isEmpty());
	}

	@Test
	void particlesDisabledClearsVfx() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setParticlesEnabled(false);
		settings.setPerformanceProfile(PerformanceProfile.fullChoreography());

		ChoreographyPlan shaped = VfxPlanner.apply(samplePlan(), peakBands(), settings);
		assertTrue(shaped.vfxPhrases().isEmpty());
	}

	@Test
	void accentDensityHigherInDropThanIntro() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 4, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(4, 8, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.5)
		);
		AutoMapSettings settings = CreationPreset.FULL_CHOREOGRAPHY.buildAutoMapSettings("tower");
		List<FrequencyBands> bands = densePeaks(0, 8, 0.1f, 0.55f);

		ChoreographyPlan shaped = VfxPlanner.apply(plan, bands, settings);
		long introAccents = shaped.vfxPhrases().stream()
			.filter(v -> v instanceof ChoreographyVfx.ParticleBurst b && !"hero_burst".equals(b.name()))
			.filter(v -> v.timeSeconds() < 4)
			.count();
		long dropAccents = shaped.vfxPhrases().stream()
			.filter(v -> v instanceof ChoreographyVfx.ParticleBurst b && !"hero_burst".equals(b.name()))
			.filter(v -> v.timeSeconds() >= 4)
			.count();

		assertTrue(dropAccents > introAccents, "intro=" + introAccents + " drop=" + dropAccents);
	}

	@Test
	void sectionEdgeOnlyWhenIntensityAllows() {
		List<ChoreographyVfx> edges = VfxPlanner.sectionEdgeVfx(samplePlan(),
			com.beatblock.automap.performance.LayerIntensity.MEDIUM);
		assertFalse(edges.isEmpty());
		assertEquals(1, edges.stream().filter(v -> v instanceof ChoreographyVfx.ScreenFlash).count());
	}

	private static ChoreographyPlan samplePlan() {
		ChoreographyPhrase hero = new ChoreographyPhrase(
			new TriggerSpec.OnFeature("low"),
			TargetSet.of("tower"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			new TimingPatternSpec.Simultaneous(),
			IntensityEnvelope.flat(1.0f),
			VariationSpec.none(),
			1,
			ChoreographyTimingSnap.SECTION,
			ChoreographyLayer.HERO
		);
		return new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 4, SectionType.VERSE, "verse"),
				new ChoreographyPlan.SectionPlan(4, 8, SectionType.DROP, "drop"),
				new ChoreographyPlan.SectionPlan(8, 12, SectionType.BREAK, "break"),
				new ChoreographyPlan.SectionPlan(12, 16, SectionType.OUTRO, "outro")
			),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.6),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(hero)
		);
	}

	private static List<FrequencyBands> peakBands() {
		return densePeaks(0, 16, 0.2f, 0.5f);
	}

	private static List<FrequencyBands> densePeaks(double start, double end, float stepEnergy, float high) {
		List<FrequencyBands> bands = new ArrayList<>();
		int i = 0;
		for (double t = start; t <= end; t += 0.05) {
			float energy = (i % 3 == 1) ? high : stepEnergy;
			bands.add(new FrequencyBands(t, 0.2f, 0.2f, energy));
			i++;
		}
		return bands;
	}
}
