package com.beatblock.automap.choreography;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class SpatialMotifCompilerIntegrationTest {

	private BlockAnimationEngine engine;

	@BeforeEach
	void setUp() {
		engine = BeatBlock.getContext().blockAnimationEngine();
		engine.getStageObjectSystem().clear();
	}

	@Test
	void compilesSpatialMotifPhraseIntoStaggeredAnimationEvents() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.BUILD, "build")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "tower-a"),
				new ChoreographyPlan.StageRoleAssignment("mid", "tower-b"),
				new ChoreographyPlan.StageRoleAssignment("high", "tower-c")
			),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(new SpatialMotifPhrase(
				1.0,
				SpatialMotifId.CASCADE,
				List.of("tower-a", "tower-b", "tower-c"),
				MotifAxis.X,
				0.1,
				"pulse",
				0.8f,
				0.5,
				0
			))
		);

		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(3, count);
		assertEquals("tower-a", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("tower-b", timeline.getAutoAnimationEvents().get(1).getTargetObjectId());
		assertEquals("tower-c", timeline.getAutoAnimationEvents().get(2).getTargetObjectId());
		assertTrue(timeline.getAutoAnimationEvents().get(1).getTimeSeconds()
			> timeline.getAutoAnimationEvents().get(0).getTimeSeconds());
		assertEquals("CASCADE", timeline.getAutoAnimationEvents().get(0).getParameters().get("spatialMotifId"));
	}

	@Test
	void compilerUsesRealStageObjectCentersForCascadeOrder() {
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-a", "A", List.of(new BlockPos(10, 64, 0))));
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-b", "B", List.of(new BlockPos(0, 64, 0))));
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-c", "C", List.of(new BlockPos(20, 64, 0))));

		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.BUILD, "build")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "tower-a"),
				new ChoreographyPlan.StageRoleAssignment("mid", "tower-b"),
				new ChoreographyPlan.StageRoleAssignment("high", "tower-c")
			),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(new SpatialMotifPhrase(
				1.0,
				SpatialMotifId.CASCADE,
				List.of("tower-a", "tower-b", "tower-c"),
				MotifAxis.X,
				0.1,
				"pulse",
				0.8f,
				0.5,
				0
			))
		);

		ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals("tower-b", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("tower-a", timeline.getAutoAnimationEvents().get(1).getTargetObjectId());
		assertEquals("tower-c", timeline.getAutoAnimationEvents().get(2).getTargetObjectId());
	}

	@Test
	void planBuilderAddsGrammarPhrasesWhenMultipleStageRolesExist() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new com.beatblock.timeline.FeatureEvent(1.0, 0.6f));

		var config = com.beatblock.automap.AutoMapConfig.builder()
			.targetForFeature("low", "tower-a")
			.targetForFeature("mid", "tower-b")
			.rule(new com.beatblock.automap.AutoMapRule("low", 0.1f, "bounce", 0.5, true, 4f, 0.0, null))
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(
			timeline,
			config,
			List.of(new com.beatblock.automap.engine.StructuralSection(0, 16, SectionType.BUILD, "build", 1.0))
		);

		assertEquals(2, plan.choreographyPhrases().size());
		assertEquals(0, plan.spatialMotifPhrases().size());
		assertInstanceOf(
			com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNFeatureHits.class,
			plan.choreographyPhrases().getFirst().trigger()
		);
		assertEquals(2, plan.choreographyPhrases().getFirst().targets().size());
		assertEquals(SpatialMotifId.CASCADE, plan.choreographyPhrases().getFirst().spatial().resolvedPattern());
		assertTrue(plan.choreographyPhrases().get(1).isHero());
	}
}
