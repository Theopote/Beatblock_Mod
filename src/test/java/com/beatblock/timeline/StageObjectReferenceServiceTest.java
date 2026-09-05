package com.beatblock.timeline;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.AutoMapSettingsStore;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.ui.presenter.BuildLayersPresenter;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageObjectReferenceServiceTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager layerManager;
	private Timeline timeline;
	private CommandManager commandManager;
	private BuildLayersPresenter presenter;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		AutoMapSettingsStore.resetForTests();
		stageObjectSystem = new StageObjectSystem();
		layerManager = new BuildLayerManager(stageObjectSystem);
		timeline = Timeline.createDefault();
		commandManager = new CommandManager();
		presenter = new BuildLayersPresenter(() -> commandManager, () -> layerManager, () -> timeline);
	}

	@AfterEach
	void tearDown() {
		AutoMapSettingsStore.resetForTests();
	}

	@Test
	void findCollectsAnimationBindingPlanAndAutoMapTargets() {
		BuildLayer layer = layerManager.createFromSelection("Tower", List.of(new BlockPos(1, 64, 0)));
		assertNotNull(layer);
		String stageId = layer.getStageObjectId();

		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev1", 1.0, 0.5, "Pulse", stageId, 0.8f, Map.of()));

		com.beatblock.timeline.binding.AnimationBindingEngine.saveRules(timeline, List.of(
			com.beatblock.timeline.binding.AnimationBindingRule.builder()
				.id("r1")
				.sourceFeatureKey("kick")
				.animationTypeId("Pulse")
				.targetObjectId(stageId)
				.build()
		));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(),
			List.of(new ChoreographyPlan.StageRoleAssignment("kick", stageId)),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(new ChoreographyPhrase(
				new TriggerSpec.OnFeature("kick"),
				TargetSet.of(stageId),
				SpatialPatternSpec.leftToRight(),
				MotionPresetSpec.bounce(),
				new TimingPatternSpec.Simultaneous(),
				IntensityEnvelope.flat(0.8f),
				VariationSpec.none(),
				0
			))
		);
		AutoMapConfig config = AutoMapConfig.builder()
			.rule(new AutoMapRule("kick", 0.2f, "Pulse", 0.4, true, 1.5f, 0.1, stageId))
			.targetForFeature("snare", stageId)
			.build();
		ChoreographyPlanStore.save(timeline, plan, config);
		AutoMapSettingsStore.current().setTargetObjectIds(List.of(stageId));

		var summary = StageObjectReferenceService.find(timeline, Set.of(stageId));
		assertTrue(summary.count() >= 5);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.ANIMATION_EVENT) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.BINDING_RULE) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.STAGE_ROLE) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.GRAMMAR_TARGET) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.AUTOMAP_RULE) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.AUTOMAP_FEATURE_TARGET) >= 1);
		assertTrue(summary.countOf(StageObjectReferenceService.ReferenceType.AUTOMAP_SETTINGS) >= 1);
	}

	@Test
	void deleteWithoutClearingRefsIsBlocked() {
		BuildLayer layer = layerManager.createFromSelection("Used", List.of(new BlockPos(2, 64, 0)));
		assertNotNull(layer);
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev", 1.0, 0.5, "Pulse", layer.getStageObjectId(), 1f, Map.of()));

		var blocked = presenter.deleteLayer(layer.getId(), false);
		assertFalse(blocked.result().ok());
		assertFalse(blocked.blockedReferences().isEmpty());
		assertNotNull(layerManager.get(layer.getId()));
		assertNotNull(stageObjectSystem.get(layer.getStageObjectId()));
	}

	@Test
	void deleteWithClearRefsUnbindsAnimationTargetsAndRemovesLayer() {
		BuildLayer layer = layerManager.createFromSelection("Used", List.of(new BlockPos(3, 64, 0)));
		assertNotNull(layer);
		String stageId = layer.getStageObjectId();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev", 1.0, 0.5, "Pulse", stageId, 1f, Map.of()));
		AutoMapSettingsStore.current().setTargetObjectIds(List.of(stageId));

		var cleared = presenter.deleteLayer(layer.getId(), true);
		assertTrue(cleared.result().ok());
		assertNull(layerManager.get(layer.getId()));
		assertNull(stageObjectSystem.get(stageId));
		assertTrue(timeline.getStageEvents().getFirst().isUnboundTarget());
		assertTrue(AutoMapSettingsStore.current().getTargetObjectIds().isEmpty());
	}

	@Test
	void deleteClearRefsUndoRestoresTargets() {
		BuildLayer layer = layerManager.createFromSelection("Used", List.of(new BlockPos(4, 64, 0)));
		assertNotNull(layer);
		String stageId = layer.getStageObjectId();
		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev", 1.0, 0.5, "Pulse", stageId, 1f, Map.of()));

		DeleteLayerCommand cmd = new DeleteLayerCommand(layerManager, layer.getId(), timeline, true);
		cmd.execute();
		assertTrue(timeline.getStageEvents().getFirst().isUnboundTarget());

		cmd.undo();
		assertNotNull(layerManager.get(layer.getId()));
		assertEquals(stageId, timeline.getStageEvents().getFirst().getTargetObjectId());
	}

	@Test
	void remapRewritesAutoMapConfigAndGrammarTargets() {
		String oldId = "stage-old";
		String newId = "stage-new";
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(),
			List.of(new ChoreographyPlan.StageRoleAssignment("kick", oldId)),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(new ChoreographyPhrase(
				new TriggerSpec.OnFeature("kick"),
				TargetSet.of(oldId, "keep-me"),
				SpatialPatternSpec.leftToRight(),
				MotionPresetSpec.bounce(),
				new TimingPatternSpec.Simultaneous(),
				IntensityEnvelope.flat(0.8f),
				VariationSpec.none(),
				0
			))
		);
		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("kick", oldId)
			.build();
		ChoreographyPlanStore.save(timeline, plan, config);

		StageObjectReferenceService.MutationResult mutation =
			StageObjectReferenceService.remap(timeline, Set.of(oldId), newId);
		assertFalse(mutation.isEmpty());

		ChoreographyPlan remapped = ChoreographyPlanStore.loadPlan(timeline);
		assertEquals(newId, remapped.stageRoles().getFirst().targetObjectId());
		assertEquals(List.of(newId, "keep-me"), remapped.choreographyPhrases().getFirst().targets().objectIds());
		assertEquals(newId, ChoreographyPlanStore.loadConfig(timeline).getTargetByNormalizedFeature().get("low"));

		StageObjectReferenceService.restore(timeline, mutation);
		assertEquals(oldId, ChoreographyPlanStore.loadPlan(timeline).stageRoles().getFirst().targetObjectId());
	}
}
