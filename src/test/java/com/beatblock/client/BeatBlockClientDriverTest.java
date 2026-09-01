package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatBlockClientDriverTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private MusicPlayer musicPlayer;
	private BeatBlockContext context;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(90.0);
		musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		BeatBlockClientDriver.install(() -> context);
	}

	@AfterEach
	void tearDown() {
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
	}

	@Test
	void previewTimelineTimeSecondsUsesInjectedEditorClock() {
		editor.getClock().seek(12.5);
		assertEquals(12.5, BeatBlockClientDriver.previewTimelineTimeSeconds(), 1e-9);
	}

	@Test
	void drivingLifecycleUsesInjectedMusicPlayer() {
		assertFalse(BeatBlockClientDriver.isDriving());
		BeatBlockClientDriver.startDriving();
		assertTrue(BeatBlockClientDriver.isDriving());
		BeatBlockClientDriver.stopDriving();
		assertFalse(BeatBlockClientDriver.isDriving());
	}

	@Test
	void stopPlaybackPausesMusicAndClearsDriving() {
		musicPlayer.setDurationSeconds(30.0);
		musicPlayer.play();
		BeatBlockClientDriver.startDriving();
		BeatBlockClientDriver.stopPlayback();
		assertFalse(BeatBlockClientDriver.isDriving());
		assertFalse(musicPlayer.isPlaying());
	}

	@Test
	void drivingKeepsCompiledSnapshotStableWhileDocumentChanges() {
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"before-play", 1.0, 1.0, "pulse", "stage", 1f, Map.of()));
		BeatBlockClientDriver.startDriving();
		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertEquals(1, snapshot.stageEvents().size());

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"during-play", 2.0, 1.0, "pulse", "stage", 1f, Map.of()));
		assertEquals(1, snapshot.stageEvents().size());
		assertEquals("pulse", snapshot.stageEvents().getFirst().getAnimationTypeId());
	}

	@Test
	void prepareExportFrameFromSnapshotSeeksClockAndUsesFrozenCompiledProgram() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "Stage", List.of(new BlockPos(0, 64, 0))));
		context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.blockAnimationEngine(engine)
			.build();
		BeatBlockClientDriver.install(() -> context);

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"frozen", 1.0, 1.0, "Pulse", "stage", 1f, Map.of(
				"animationType", "Pulse", "targetObject", "stage", "durationSeconds", 1.0)));
		var frozen = TimelineCompiler.compile(timeline, engine, null);

		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"live-edit", 2.0, 1.0, "Pulse", "stage", 1f, Map.of(
				"animationType", "Pulse", "targetObject", "stage", "durationSeconds", 1.0)));

		BeatBlockClientDriver.prepareExportFrameFromSnapshot(frozen, 10.0);

		assertEquals(10.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		var compiled = BeatBlockClientDriver.compiledPlaybackForTests();
		assertEquals(1, compiled.stageEvents().size());
		assertEquals("Pulse", compiled.stageEvents().getFirst().getAnimationTypeId());
	}
}
