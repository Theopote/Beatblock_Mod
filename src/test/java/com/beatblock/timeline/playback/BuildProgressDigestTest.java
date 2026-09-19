package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-4：digest 必须区分 BUILD 进度，而不能只比较 BUILD:Pulse 标签。
 */
class BuildProgressDigestTest {

	@BeforeAll
	static void bootstrap() {
		com.beatblock.testutil.MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void midBuildProgressDiffersFromCompletedBuild() {
		CompiledTimelineSnapshot program = compileTenBlockWall();
		PlaybackStateDigest early = PlaybackStateDigest.reconstructAt(program, 5.2);
		PlaybackStateDigest late = PlaybackStateDigest.reconstructAt(program, 10.0);

		assertTrue(early.buildProgress().containsKey("wall"));
		assertTrue(late.buildProgress().containsKey("wall"));
		BuildProgressDigest earlyBuild = early.buildProgress().get("wall");
		BuildProgressDigest lateBuild = late.buildProgress().get("wall");

		assertEquals(10, earlyBuild.totalBlockCount());
		assertEquals(10, lateBuild.totalBlockCount());
		assertTrue(earlyBuild.completedBlockCount() < lateBuild.completedBlockCount());
		assertEquals(10, lateBuild.completedBlockCount());
		assertNotEquals(earlyBuild.visibleBlockHash(), lateBuild.visibleBlockHash());
		assertNotEquals(early, late, "same BUILD label must not hide progress mismatch");
	}

	@Test
	void playToAndReconstructAgreeOnBuildProgress() {
		CompiledTimelineSnapshot program = compileTenBlockWall();
		for (double t : new double[] {5.0, 5.5, 6.0, 10.0}) {
			assertEquals(
				PlaybackStateDigest.playTo(program, t),
				PlaybackStateDigest.reconstructAt(program, t),
				"t=" + t
			);
		}
	}

	private static CompiledTimelineSnapshot compileTenBlockWall() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(20.0);
		timeline.setMetadata("bpm", 120.0);

		List<BlockPos> blocks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			blocks.add(new BlockPos(i, 64, 0));
		}
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks("wall", "Wall", blocks));

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"build-wall", 5.0, 2.0, "Pulse", "wall", 1f,
			Map.of(
				"actionMode", "BUILD",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", "wall",
				"buildMode", "wall",
				"durationSeconds", 2.0)));

		// Feature beats so BUILD uses beat-grid pacing from compiled snapshot
		for (double t = 0.0; t <= 20.0; t += 0.5) {
			timeline.addFeatureEvent("kick", new com.beatblock.timeline.FeatureEvent(t, 1f));
		}

		return TimelineCompiler.compile(timeline, engine, null);
	}
}
