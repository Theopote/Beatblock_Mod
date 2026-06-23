package com.beatblock.timeline.project;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.TimelineMarker;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscProjectStoreTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@TempDir
	Path tempDir;

	@Test
	void roundTripsProjectMetadataAndMarkers() throws Exception {
		Path file = tempDir.resolve("demo.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.setName("Demo Show");
		timeline.setMetadata("audioPath", "C:/music/track.mp3");
		timeline.setMetadata("projectId", "proj-123");
		timeline.addMarker(new TimelineMarker("mk1", 12.5, "Drop", MarkerType.DROP));

		OscProjectStore.save(file, timeline);

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);

		assertEquals("proj-123", loaded.getProjectId());
		assertEquals("Demo Show", loaded.getTimelineName());
		assertTrue(loaded.getAudioPath().replace('\\', '/').endsWith("track.mp3"));
		assertEquals(1, loaded.getMarkers().size());
		assertEquals("mk1", loaded.getMarkers().getFirst().getId());
		assertEquals(12.5, loaded.getMarkers().getFirst().getTimeSeconds(), 1e-6);
		assertEquals(MarkerType.DROP, loaded.getMarkers().getFirst().getType());
	}

	@Test
	void loadsLegacyV1JsonWithoutVersionField() throws Exception {
		Path file = tempDir.resolve("legacy.osc");
		Files.writeString(file, """
			{
			  "projectId": "legacy-id",
			  "timelineName": "Legacy",
			  "audioPath": "/audio/old.wav"
			}
			""");

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);

		assertEquals("legacy-id", loaded.getProjectId());
		assertEquals("Legacy", loaded.getTimelineName());
		assertEquals("/audio/old.wav", loaded.getAudioPath());
		assertTrue(loaded.getMarkers().isEmpty());
	}

	@Test
	void rejectsUnsupportedFutureVersion() throws Exception {
		Path file = tempDir.resolve("future.osc");
		Files.writeString(file, """
			{"version": 99, "projectId": "x"}
			""");

		assertThrows(Exception.class, () -> OscProjectStore.load(file));
	}

	@Test
	void roundTripsBuildLayersWhenManagerProvided() throws Exception {
		Path file = tempDir.resolve("layers.osc");
		StageObjectSystem stageObjects = new StageObjectSystem();
		BlockPos pos = new BlockPos(1, 64, 2);
		StageObject stage = StageObjectSystem.fromBlocks("stage-1", "Layer Object", List.of(pos));
		stageObjects.register(stage);
		BuildLayerManager layers = new BuildLayerManager(stageObjects);
		layers.registerRestored(new BuildLayer(
			"layer-1",
			"Test Layer",
			stage,
			LayerVisibilityState.FREE_VISIBLE,
			Map.of(),
			null
		));

		Timeline timeline = Timeline.createDefault();
		timeline.setName("Layer Project");
		OscProjectStore.save(file, timeline, layers);

		StageObjectSystem restoredStages = new StageObjectSystem();
		BuildLayerManager restoredLayers = new BuildLayerManager(restoredStages);
		OscProjectStore.load(file, restoredLayers);

		assertEquals(1, restoredLayers.getAll().size());
		assertEquals("layer-1", restoredLayers.getAll().iterator().next().getId());
	}

	@Test
	void roundTripsCapturedBlockStatesInBuildLayers() throws Exception {
		Path file = tempDir.resolve("layers-capture.osc");
		BlockPos pos = new BlockPos(2, 64, 3);
		StageObjectSystem stageObjects = new StageObjectSystem();
		StageObject stage = StageObjectSystem.fromBlocks("stage-cap", "Captured", List.of(pos));
		stageObjects.register(stage);
		BuildLayerManager layers = new BuildLayerManager(stageObjects);
		layers.registerRestored(new BuildLayer(
			"layer-cap",
			"Captured Layer",
			stage,
			LayerVisibilityState.FREE_HIDDEN,
			Map.of(pos, Blocks.DIAMOND_BLOCK.getDefaultState()),
			"clip-cap"
		));

		Timeline timeline = Timeline.createDefault();
		OscProjectStore.save(file, timeline, layers);

		StageObjectSystem restoredStages = new StageObjectSystem();
		BuildLayerManager restoredLayers = new BuildLayerManager(restoredStages);
		OscProjectStore.load(file, restoredLayers);

		BuildLayer loaded = restoredLayers.getAll().iterator().next();
		assertEquals("layer-cap", loaded.getId());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, loaded.getState());
		assertEquals("clip-cap", loaded.getBoundClipId());
		assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(), loaded.getCapturedStates().get(pos));
	}

	@Test
	void roundTripsAnimationTracksWhenTimelineProvided() throws Exception {
		Path file = tempDir.resolve("animation.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev-auto", 2.5, 1.0, "build", "stage-x", 0.9f,
			Map.of("eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name(), "buildMode", "tower")));
		OscProjectStore.save(file, timeline);

		Timeline restored = Timeline.createDefault();
		OscProjectStore.load(file, null, restored);

		assertEquals(1, restored.getAutoAnimationEvents().size());
		assertEquals(2.5, restored.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
		assertEquals("stage-x", restored.getAutoAnimationEvents().getFirst().getTargetObjectId());
		assertEquals("tower", restored.getAutoAnimationEvents().getFirst().getParameters().get("buildMode"));
	}

	@Test
	void v2ProjectWithoutAnimationTracksStillLoads() throws Exception {
		Path file = tempDir.resolve("v2.osc");
		Files.writeString(file, """
			{"version": 2, "projectId": "v2-id", "timelineName": "V2", "audioPath": "", "markers": []}
			""");

		Timeline restored = Timeline.createDefault();
		restored.addAutoAnimationEvent(new TimelineAnimationEvent(
			"old", 1.0, 1.0, "build", "stage", 1f, Map.of()));
		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file, null, restored);

		assertEquals("v2-id", loaded.getProjectId());
		assertTrue(restored.getAutoAnimationEvents().isEmpty());
	}
}
