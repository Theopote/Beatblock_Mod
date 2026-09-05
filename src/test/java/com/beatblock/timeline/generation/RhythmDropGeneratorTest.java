package com.beatblock.timeline.generation;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class RhythmDropGeneratorTest {

	private Timeline timeline;
	private StageObjectSystem stageObjects;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		stageObjects = new StageObjectSystem();
	}

	@Test
	void generateWritesEventsOnBeatGrid() {
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 1f));
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 1f));
		timeline.addFeatureEvent("kick", new FeatureEvent(3.0, 1f));

		List<BlockPos> positions = List.of(
			new BlockPos(1, 64, 1),
			new BlockPos(2, 64, 2)
		);
		var config = new RhythmDropGenerator.Config(0.5, false, 1.0, 6.0, RhythmDropGenerator.DEFAULT_ANCHOR_ID);

		RhythmDropGenerator.Outcome outcome = RhythmDropGenerator.generate(timeline, stageObjects, positions, config);

		assertTrue(outcome.success());
		assertEquals(2, outcome.eventCount());
		assertEquals(RhythmDropGenerator.DEFAULT_ANCHOR_ID, outcome.targetObjectId());
		assertNotNull(stageObjects.get(RhythmDropGenerator.DEFAULT_ANCHOR_ID));

		List<TimelineAnimationEvent> events = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
		assertEquals(2, events.size());
		assertEquals("RhythmDrop", events.get(0).getAnimationTypeId());
		assertEquals(0.0, events.get(0).getTimeSeconds(), 1e-9);
		assertEquals(0.0, events.get(1).getTimeSeconds(), 1e-9);
		assertEquals(1, events.get(0).getParameters().get("singleBlockX"));
		assertEquals(2, events.get(1).getParameters().get("singleBlockX"));

		assertTrue(outcome.generationId().startsWith("gen-"));
		assertEquals(TimelineGeneratorIds.RHYTHM_DROP,
			events.get(0).getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID));
		assertEquals(outcome.generationId(),
			events.get(0).getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
		assertEquals(TimelineEventOrigin.GENERATED.name(),
			events.get(0).getParameters().get(TimelineGenerationMetadataSupport.PARAM_ORIGIN));
	}

	@Test
	void generateTagsSharedSessionAcrossEvents() {
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 1f));
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 1f));

		GenerationSession session = GenerationSession.create(TimelineGeneratorIds.RHYTHM_DROP, timeline);
		var outcome = RhythmDropGenerator.generate(
			timeline,
			stageObjects,
			List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)),
			RhythmDropGenerator.Config.defaults(0.0),
			session
		);

		assertTrue(outcome.success());
		assertEquals(session.generationId(), outcome.generationId());
		List<TimelineAnimationEvent> events = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
		assertEquals(2, events.size());
		assertEquals(session.generationId(),
			events.get(0).getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
		assertEquals(session.generationId(),
			events.get(1).getParameters().get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
	}

	@Test
	void contentReplacePolicyCanClearByGenerationId() {
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 1f));
		var outcome = RhythmDropGenerator.generate(
			timeline,
			stageObjects,
			List.of(new BlockPos(3, 64, 3)),
			RhythmDropGenerator.Config.defaults(0.0)
		);
		assertTrue(outcome.success());
		assertEquals(1, timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK).size());

		timeline.applyContentReplacePolicy(
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			ContentReplacePolicy.replaceGeneration(outcome.generationId())
		);
		assertTrue(timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK).isEmpty());
	}

	@Test
	void generateFailsWithoutLandingPositions() {
		var outcome = RhythmDropGenerator.generate(
			timeline, stageObjects, List.of(), RhythmDropGenerator.Config.defaults(0.0));
		assertFalse(outcome.success());
	}
}
