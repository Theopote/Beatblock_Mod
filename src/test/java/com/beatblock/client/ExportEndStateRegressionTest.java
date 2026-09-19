package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.camera.CameraRuntime;
import com.beatblock.client.export.ExportPresentationSession;
import com.beatblock.client.export.VideoExportSyncFixtures;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Export 结束态回归：关闭会话后 presentation 应恢复至导出前。
 */
class ExportEndStateRegressionTest {

	private TimelineEditor editor;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		EnvironmentLightingRuntime.resetForTests();
		GlobalVisualEffectOverlay.clear();
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
		GlobalVisualEffectOverlay.clear();
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
	}

	@Test
	void closeRestoresEnvironmentLightingAfterExportMutation() {
		var before = new GlobalEventPayload.EnvironmentLighting("Pre Export", 0.6, 0.2f, 0.3f, 0.4f, 0.0);
		EnvironmentLightingRuntime.apply(before);

		ExportPresentationSession session = ExportPresentationSession.begin();
		EnvironmentLightingRuntime.apply(
			new GlobalEventPayload.EnvironmentLighting("Export Frame", 1.8, 1f, 0f, 0f, 0.0)
		);

		session.close();

		assertEquals(before.intensity(), EnvironmentLightingRuntime.current().intensity(), 1e-6);
		assertEquals(before.r(), EnvironmentLightingRuntime.current().r(), 1e-4f);
	}

	@Test
	void closeRestoresTimelineSeekAndReleasesCameraOwner() {
		editor.getClock().seek(4.0);
		ExportPresentationSession session = ExportPresentationSession.begin();

		BeatBlockClientDriver.prepareExportFrameFromSnapshot(
			VideoExportSyncFixtures.tenSecondShowcase(),
			12.0
		);

		session.close();

		assertEquals(4.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertTrue(CameraRuntime.getInstance().isPlayerOwner());
		assertFalse(CameraRuntime.getInstance().isTimelineOwner());
	}

	@Test
	void closeIsIdempotentForEndStateRestore() {
		EnvironmentLightingRuntime.apply(
			new GlobalEventPayload.EnvironmentLighting("Stable", 1.0, 0.9f, 0.8f, 0.7f, 0.0)
		);
		ExportPresentationSession session = ExportPresentationSession.begin();
		EnvironmentLightingRuntime.clear();

		session.close();
		session.close();

		assertFalse(EnvironmentLightingRuntime.current().isNeutral());
	}
}
