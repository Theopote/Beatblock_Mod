package com.beatblock.timeline.project.golden;

import com.beatblock.BeatBlock;
import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creator Alpha Showcase 结构回归（非审美）：load / compile / roundtrip + section recompile 边界。
 */
@WithBeatBlockContext
class CreatorAlphaShowcaseRegressionTest {

	private static final String FILE = "creator-alpha-showcase.osc";
	private static final int CHORUS_SECTION_INDEX = 3;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@TempDir
	Path tempDir;

	@Test
	void showcaseRoundTripAndStructureAssertions() throws Exception {
		Path source = GoldenProjectRegressionHarness.copyResourceTo(tempDir, FILE);
		Path roundTrip = tempDir.resolve("roundtrip-" + FILE);

		GoldenProjectRegressionHarness.RoundTripResult result = GoldenProjectRegressionHarness.run(
			source,
			roundTrip,
			0.0, 10.0, 30.0, 50.0, 70.0, 80.0
		);
		assertEquals(result.compileFingerprint(), result.reloadedCompileFingerprint());

		GoldenProjectRegressionHarness.LoadedProject loaded =
			GoldenProjectRegressionHarness.loadProject(source);
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(loaded.timeline());
		assertNotNull(plan);
		assertEquals(8, plan.sections().size());
		assertEquals(SectionType.CHORUS, plan.sections().get(CHORUS_SECTION_INDEX).sectionType());
		assertFalse(plan.musicalStructure().beatTimes().isEmpty());
		assertFalse(plan.motionPhrases().isEmpty());
		assertFalse(plan.cameraPhrases().isEmpty());
		assertFalse(plan.vfxPhrases().isEmpty());

		Set<SpatialMotifId> patterns = new HashSet<>();
		boolean hasHero = false;
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			patterns.add(phrase.spatial().resolvedPattern());
			if (phrase.isHero()) hasHero = true;
		}
		assertTrue(patterns.size() >= 4, "expected ≥4 spatial patterns, got " + patterns);
		assertTrue(patterns.contains(SpatialMotifId.CASCADE));
		assertTrue(patterns.contains(SpatialMotifId.WAVE));
		assertTrue(patterns.contains(SpatialMotifId.ALTERNATE));
		assertTrue(patterns.contains(SpatialMotifId.EXPLODE));
		assertTrue(hasHero, "showcase must include at least one HERO phrase");

		assertTrue(hasManualEvent(loaded.timeline()), "showcase must keep a MANUAL origin event");
	}

	@Test
	void chorusSectionRecompilePreservesManualAndOtherSections() throws Exception {
		Path source = GoldenProjectRegressionHarness.copyResourceTo(tempDir, FILE);
		GoldenProjectRegressionHarness.LoadedProject loaded =
			GoldenProjectRegressionHarness.loadProject(source);
		registerStagesForAutomap(loaded.layers());
		Timeline timeline = loaded.timeline();
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		assertNotNull(plan);

		long manualBefore = countManual(timeline);
		long dropBefore = countEventsInSectionWindow(timeline, plan, 5);
		assertTrue(manualBefore >= 1);
		assertTrue(dropBefore >= 1);

		ChoreographyPlanCompiler.compileSection(timeline, plan, CHORUS_SECTION_INDEX);

		assertEquals(manualBefore, countManual(timeline), "MANUAL events must survive section recompile");
		assertEquals(dropBefore, countEventsInSectionWindow(timeline, plan, 5),
			"Drop-section generated events must not be cleared by chorus recompile");
		assertTrue(hasManualEvent(timeline));
	}

	private static void registerStagesForAutomap(BuildLayerManager layers) {
		if (layers == null || BeatBlock.getContext() == null
			|| BeatBlock.getContext().blockAnimationEngine() == null) {
			return;
		}
		var stageSystem = BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem();
		for (BuildLayer layer : layers.getAll()) {
			if (layer != null && layer.getStageObject() != null) {
				stageSystem.register(layer.getStageObject());
			}
		}
	}

	private static boolean hasManualEvent(Timeline timeline) {
		return timeline.getAutoAnimationEvents().stream().anyMatch(event ->
			event.getEventOrigin() == TimelineEventOrigin.MANUAL
				|| "MANUAL".equals(String.valueOf(event.getParameters().get("eventOrigin"))));
	}

	private static long countManual(Timeline timeline) {
		return timeline.getAutoAnimationEvents().stream()
			.filter(event -> event.getEventOrigin() == TimelineEventOrigin.MANUAL
				|| "MANUAL".equals(String.valueOf(event.getParameters().get("eventOrigin"))))
			.count();
	}

	private static long countEventsInSectionWindow(Timeline timeline, ChoreographyPlan plan, int sectionIndex) {
		ChoreographyPlan.SectionPlan section = plan.sections().get(sectionIndex);
		return timeline.getAutoAnimationEvents().stream()
			.filter(event -> event.getTimeSeconds() >= section.startSeconds()
				&& event.getTimeSeconds() < section.endSeconds())
			.filter(event -> {
				Object layer = event.getParameters().get(ChoreographyLayer.PARAM_KEY);
				return layer != null;
			})
			.count();
	}
}
