package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.PacingMode;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequenceCompilerTest {

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
	void compileWritesBuildEventAndBindsLayer() {
		BuildLayer layer = layerManager.createFromSelection("Reveal", List.of(new BlockPos(0, 64, 0)));
		assertNotNull(layer);
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());

		BuildSequencePlan sequence = new BuildSequencePlan(
			layer.getId(),
			layer.getStageObjectId(),
			4.0,
			12.0,
			BuildSequenceMode.WALL,
			PacingMode.FIXED_INTERVAL,
			false,
			null,
			1
		);
		ChoreographyPlan plan = ChoreographyPlan.empty().withBuildSequences(List.of(sequence));

		int count = BuildSequenceCompiler.compile(timeline, plan, layerManager);

		assertEquals(1, count);
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertNotNull(layer.getBoundClipId());

		List<Track> tracks = BuildLayerTrackSupport.listTracks(timeline);
		assertFalse(tracks.isEmpty());
		Clip clip = tracks.getFirst().getClip(layer.getBoundClipId());
		assertNotNull(clip);
		assertEquals(4.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(12.0, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(1, clip.getEvents().size());

		TimelineEvent event = clip.getEvents().getFirst();
		assertEquals(
			TimelineAnimationActionMode.BUILD.name(),
			String.valueOf(event.getParameters().get("actionMode"))
		);
		assertEquals(layer.getId(), String.valueOf(event.getParameters().get("layerId")));
		assertEquals("WALL", String.valueOf(event.getParameters().get("buildMode")));
	}

	@Test
	void applyBuildRevealSemanticsStripsMotionAndAttachesSequence() {
		ChoreographyPlan analyzed = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, SectionType.BUILD, "build"),
				new ChoreographyPlan.SectionPlan(8, 16, SectionType.DROP, "drop")
			),
			List.of(new ChoreographyPlan.StageRoleAssignment("low", "stage-1")),
			List.of(new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0)),
			List.of(new ChoreographyPlan.CameraPhrase(0.0, "hold", 0)),
			List.of(),
			DensityCurve.uniform(1.0)
		);

		AutoMapSettings settings = new AutoMapSettings();
		settings.setBuildLayerId("layer-reveal");
		settings.setTargetObjectIds(List.of("stage-1"));

		ChoreographyPlan result = SmartAutoMapEngine.applyBuildRevealSemantics(analyzed, settings);

		assertTrue(result.motionPhrases().isEmpty());
		assertEquals(1, result.cameraPhrases().size());
		assertEquals(1, result.buildSequences().size());
		assertEquals("layer-reveal", result.buildSequences().getFirst().layerId());
		assertEquals(0.0, result.buildSequences().getFirst().startSeconds(), 1e-9);
		assertEquals(8.0, result.buildSequences().getFirst().endSeconds(), 1e-9);
	}

	@Test
	void recompileRebindsAlreadyBoundLayer() {
		BuildLayer layer = layerManager.createFromSelection("Tower", List.of(new BlockPos(2, 64, 0)));
		assertNotNull(layer);

		BuildSequencePlan first = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 0.0, 4.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false
		);
		assertEquals(1, BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(first)), layerManager));
		String firstClipId = layer.getBoundClipId();
		assertNotNull(firstClipId);

		BuildSequencePlan second = new BuildSequencePlan(
			layer.getId(), layer.getStageObjectId(), 8.0, 16.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false
		);
		assertEquals(1, BuildSequenceCompiler.compile(
			timeline, ChoreographyPlan.empty().withBuildSequences(List.of(second)), layerManager));

		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertNotNull(layer.getBoundClipId());
		assertFalse(firstClipId.equals(layer.getBoundClipId()));

		Clip clip = BuildLayerTrackSupport.listTracks(timeline).getFirst().getClip(layer.getBoundClipId());
		assertNotNull(clip);
		assertEquals(8.0, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(16.0, clip.getEndTimeSeconds(), 1e-9);
	}
}
