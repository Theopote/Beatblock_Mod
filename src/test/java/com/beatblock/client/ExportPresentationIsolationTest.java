package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.export.ExportPresentationSession;
import com.beatblock.client.export.VideoExportSyncFixtures;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出期间 live presentation 隔离：帧 seek 不应改写编辑态 seek / lighting / weather。
 */
class ExportPresentationIsolationTest {

	private TimelineEditor editor;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		EnvironmentLightingRuntime.resetForTests();
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		MusicPlayer musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		BeatBlockClientDriver.install(() -> context);
	}

	@AfterEach
	void tearDown() {
		EnvironmentLightingRuntime.resetForTests();
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
	}

	@Test
	void exportFrameDoesNotSeekEditorClockWhileSessionActive() {
		editor.getClock().seek(4.0);
		ExportPresentationSession session = ExportPresentationSession.begin();

		BeatBlockClientDriver.prepareExportFrameFromSnapshot(
			VideoExportSyncFixtures.tenSecondShowcase(),
			12.0
		);

		assertEquals(4.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		session.close();
	}

	@Test
	void exportFrameDoesNotMutateEnvironmentLightingWhileSessionActive() {
		var before = new GlobalEventPayload.EnvironmentLighting("Editor", 0.55, 0.2f, 0.3f, 0.4f, 0.0);
		EnvironmentLightingRuntime.apply(before);
		editor.getClock().seek(2.0);

		ExportPresentationSession session = ExportPresentationSession.begin();
		BeatBlockClientDriver.prepareExportFrameFromSnapshot(
			VideoExportSyncFixtures.tenSecondShowcase(),
			8.0
		);

		assertEquals(before.intensity(), EnvironmentLightingRuntime.current().intensity(), 1e-6);
		assertEquals(before.r(), EnvironmentLightingRuntime.current().r(), 1e-4f);
		session.close();
	}

	@Test
	void restoreAfterExportFrameReappliesCapturedPresentation() {
		var before = new GlobalEventPayload.EnvironmentLighting("Stable", 0.7, 0.1f, 0.2f, 0.3f, 0.0);
		EnvironmentLightingRuntime.apply(before);
		editor.getClock().seek(3.0);
		ExportPresentationSession.begin();

		BeatBlockClientDriver.prepareExportFrameFromSnapshot(
			VideoExportSyncFixtures.tenSecondShowcase(),
			10.0
		);
		EnvironmentLightingRuntime.apply(
			new GlobalEventPayload.EnvironmentLighting("Export Scratch", 1.5, 1f, 0f, 0f, 0.0)
		);

		BeatBlockClientDriver.restoreIsolatedPresentationAfterExportFrame();

		assertEquals(3.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertEquals(before.intensity(), EnvironmentLightingRuntime.current().intensity(), 1e-6);
		assertTrue(BeatBlockClientDriver.isExportPresentationIsolated());
	}
}
