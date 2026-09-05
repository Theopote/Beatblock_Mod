package com.beatblock.timeline;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.timeline.command.layer.MergeLayersCommand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageObjectTargetRemapperTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager layerManager;
	private Timeline timeline;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		layerManager = new BuildLayerManager(stageObjectSystem);
		timeline = Timeline.createDefault();
	}

	@Test
	void mergeLayersCommandRemapsAnimationEventTargetsAndRestoresOnUndo() {
		BuildLayer layerA = layerManager.createFromSelection("A", List.of(new BlockPos(10, 64, 0)));
		BuildLayer layerB = layerManager.createFromSelection("B", List.of(new BlockPos(11, 64, 0)));
		assertNotNull(layerA);
		assertNotNull(layerB);
		String stageA = layerA.getStageObjectId();
		String stageB = layerB.getStageObjectId();

		timeline.addBlockAnimationEvent(new TimelineAnimationEvent(
			"ev-a", 1.0, 0.5, "Pulse", stageA, 0.8f, Map.of()));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev-b", 2.0, 0.5, "Pulse", stageB, 0.6f, Map.of()));

		MergeLayersCommand merge = new MergeLayersCommand(
			layerManager, List.of(layerA.getId(), layerB.getId()), "Merged", timeline);
		merge.execute();
		BuildLayer merged = merge.getMergedLayer();
		assertNotNull(merged);
		String mergedStage = merged.getStageObjectId();

		assertNull(stageObjectSystem.get(stageA));
		assertNull(stageObjectSystem.get(stageB));
		assertNotNull(stageObjectSystem.get(mergedStage));

		List<TimelineAnimationEvent> events = timeline.getStageEvents();
		assertEquals(2, events.size());
		for (TimelineAnimationEvent event : events) {
			assertEquals(mergedStage, event.getTargetObjectId());
		}

		merge.undo();
		assertNotNull(stageObjectSystem.get(stageA));
		assertNotNull(stageObjectSystem.get(stageB));
		assertNull(layerManager.get(merged.getId()));

		var restoredTargets = timeline.getStageEvents().stream()
			.map(TimelineAnimationEvent::getTargetObjectId)
			.collect(java.util.stream.Collectors.toSet());
		assertEquals(Set.of(stageA, stageB), restoredTargets);
	}

	@Test
	void remapperUpdatesBindingRulesAndChoreographyRoles() {
		AnimationBindingEngine.saveRules(timeline, List.of(
			AnimationBindingRule.builder()
				.id("rule-1")
				.sourceFeatureKey("kick")
				.animationTypeId("Pulse")
				.targetObjectId("stage-old")
				.build()
		));
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(),
			List.of(new ChoreographyPlan.StageRoleAssignment("kick", "stage-old")),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyPlanStore.save(timeline, plan, null);

		StageObjectTargetRemapper.RemapResult result = StageObjectTargetRemapper.remap(
			timeline, Set.of("stage-old"), "stage-new");
		assertTrue(!result.isEmpty());

		assertEquals("stage-new", AnimationBindingEngine.loadRules(timeline).getFirst().targetObjectId());
		assertEquals("stage-new", ChoreographyPlanStore.loadPlan(timeline).stageRoles().getFirst().targetObjectId());

		StageObjectTargetRemapper.restore(timeline, result);
		assertEquals("stage-old", AnimationBindingEngine.loadRules(timeline).getFirst().targetObjectId());
		assertEquals("stage-old", ChoreographyPlanStore.loadPlan(timeline).stageRoles().getFirst().targetObjectId());
	}

	@Test
	void mergeWithoutTimelineStillDissolvesStages() {
		RuntimeStageObject a = StageObjectSystem.fromBlocks("sa", "A", List.of(new BlockPos(1, 64, 0)));
		RuntimeStageObject b = StageObjectSystem.fromBlocks("sb", "B", List.of(new BlockPos(2, 64, 0)));
		layerManager.registerRestored(new BuildLayer(
			"la", "A", a, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));
		layerManager.registerRestored(new BuildLayer(
			"lb", "B", b, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		MergeLayersCommand merge = new MergeLayersCommand(layerManager, List.of("la", "lb"), "M");
		merge.execute();
		assertNull(stageObjectSystem.get("sa"));
		assertNull(stageObjectSystem.get("sb"));
		assertNotNull(merge.getMergedLayer());
	}
}
