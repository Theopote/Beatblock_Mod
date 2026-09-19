package com.beatblock.client;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrubPreviewReconstructTest {

	private Timeline timeline;
	private BlockAnimationEngine engine;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0))));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"build-1", 0.0, 5.0, animId, "tower", 1f,
			Map.of(
				"actionMode", TimelineAnimationActionMode.BUILD.name(),
				"targetObject", "tower",
				"animationType", animId
			)));
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.blockAnimationEngine(engine)
			.build();
		BeatBlockClientDriver.install(() -> context);
	}

	@AfterEach
	void tearDown() {
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
	}

	@Test
	void scrubReconstructSchedulesBuildForPreview() {
		BeatBlockClientDriver.syncPreviewStageForTests(2.5);

		assertFalse(BeatBlockClientDriver.isDriving());
		assertTrue(engine.getBuildSequencer().getActiveInstances().size() >= 1,
			"scrub reconstruct should schedule BUILD instances without formal playback");
	}
}
