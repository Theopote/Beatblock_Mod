package com.beatblock.automap.choreography;

import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.GenerationSession;
import com.beatblock.timeline.generation.PacingMode;
import com.beatblock.timeline.generation.TimelineEventOwnership;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.timeline.generation.TimelineGeneratorIds;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequenceOwnershipTest {

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
	void manualBoundBuildClipSurvivesRecompile() {
		BuildLayer layer = layerManager.createFromSelection("Manual", List.of(new BlockPos(0, 64, 0)));
		assertNotNull(layer);
		Track track = BuildLayerTrackSupport.ensureDefaultTrack(timeline);
		assertNotNull(track);

		Clip manualClip = new Clip("clip_manual_build", 1.0, 5.0);
		Map<String, Object> params = new HashMap<>();
		params.put("actionMode", TimelineAnimationActionMode.BUILD.name());
		params.put("layerId", layer.getId());
		params.put("eventOrigin", TimelineEventOrigin.MANUAL.name());
		manualClip.addEvent(new TimelineEvent("evt_manual_build", 1.0, EventType.ANIMATION, params));
		track.addClip(manualClip);
		layerManager.bindToClip(layer, manualClip.getId());

		BuildSequencePlan sequence = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 8.0, 16.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false
		);
		int written = BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(sequence)), layerManager);

		assertEquals(0, written);
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertEquals(manualClip.getId(), layer.getBoundClipId());
		assertNotNull(track.getClip(manualClip.getId()));
	}

	@Test
	void userEditedBoundBuildClipSurvivesRecompile() {
		BuildLayer layer = layerManager.createFromSelection("Edited", List.of(new BlockPos(1, 64, 0)));
		assertNotNull(layer);
		Track track = BuildLayerTrackSupport.ensureDefaultTrack(timeline);

		Clip clip = new Clip("clip_edited_build", 0.0, 4.0);
		Map<String, Object> params = TimelineGenerationMetadataSupport.apply(
			Map.of(
				"actionMode", TimelineAnimationActionMode.BUILD.name(),
				"layerId", layer.getId()
			),
			new TimelineGenerationMetadata(
				TimelineEventOrigin.USER_EDITED,
				TimelineGeneratorIds.SMART_AUTOMAP,
				"gen-old",
				0,
				0,
				""
			)
		);
		clip.addEvent(new TimelineEvent("evt_edited_build", 0.0, EventType.ANIMATION, params));
		track.addClip(clip);
		layerManager.bindToClip(layer, clip.getId());

		BuildSequencePlan sequence = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 10.0, 20.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false
		);
		assertEquals(0, BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(sequence)), layerManager));
		assertEquals(clip.getId(), layer.getBoundClipId());
	}

	@Test
	void generatedBoundBuildClipIsReplacedOnRecompile() {
		BuildLayer layer = layerManager.createFromSelection("Auto", List.of(new BlockPos(2, 64, 0)));
		assertNotNull(layer);

		GenerationSession session = GenerationSession.create(TimelineGeneratorIds.SMART_AUTOMAP, timeline);
		BuildSequencePlan first = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 0.0, 4.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 0
		);
		assertEquals(1, BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(first)), layerManager, session));
		String firstClipId = layer.getBoundClipId();
		assertNotNull(firstClipId);

		BuildSequencePlan second = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 8.0, 16.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 0
		);
		assertEquals(1, BuildSequenceCompiler.compile(
			timeline,
			ChoreographyPlan.empty().withBuildSequences(List.of(second)),
			layerManager,
			GenerationSession.create(TimelineGeneratorIds.SMART_AUTOMAP, timeline)
		));
		assertNotNull(layer.getBoundClipId());
		assertTrue(!firstClipId.equals(layer.getBoundClipId()));
		Clip clip = BuildLayerTrackSupport.listTracks(timeline).getFirst().getClip(layer.getBoundClipId());
		assertNotNull(clip);
		assertEquals(8.0, clip.getStartTimeSeconds(), 1e-9);
	}

	@Test
	void lockedGeneratedBuildClipSurvivesRecompile() {
		BuildLayer layer = layerManager.createFromSelection("Locked", List.of(new BlockPos(3, 64, 0)));
		assertNotNull(layer);
		Track track = BuildLayerTrackSupport.ensureDefaultTrack(timeline);

		Clip clip = new Clip("clip_locked_build", 0.0, 4.0);
		Map<String, Object> params = TimelineGenerationMetadataSupport.apply(
			Map.of(
				"actionMode", TimelineAnimationActionMode.BUILD.name(),
				"layerId", layer.getId()
			),
			new TimelineGenerationMetadata(
				TimelineEventOrigin.GENERATED,
				TimelineGeneratorIds.SMART_AUTOMAP,
				"gen-lock",
				0,
				0,
				""
			)
		);
		params = TimelineEventOwnership.setLocked(params, true);
		clip.addEvent(new TimelineEvent("evt_locked_build", 0.0, EventType.ANIMATION, params));
		track.addClip(clip);
		layerManager.bindToClip(layer, clip.getId());

		BuildSequencePlan sequence = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 9.0, 18.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false
		);
		assertEquals(0, BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(sequence)), layerManager));
		assertEquals(clip.getId(), layer.getBoundClipId());
	}
}
