package com.beatblock.client.export;

import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.BeatBlockAuthoritativeWorldMutator;
import com.beatblock.client.ClientThreadGuard;
import com.beatblock.client.PlaybackExecutionMode;
import com.beatblock.client.PresentationStateCoordinator;
import com.beatblock.client.camera.CameraRuntime;
import com.beatblock.client.camera.TimelineCameraController;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.PlaybackEngine;
import com.beatblock.timeline.playback.StageStateResolver;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Export playback bridge: freeze / restore editor presentation and drive
 * frame seeks against a frozen {@link CompiledTimelineSnapshot}.
 * <p>
 * Keeps export isolation state out of {@link com.beatblock.client.BeatBlockClientDriver}
 * while still using the driver's formal {@link PlaybackEngine} via {@link Host}.
 */
public final class ExportPlaybackBridge {

	/**
	 * Driver-owned playback hooks the bridge must not own (formal engine, mutation restore, tick).
	 */
	public interface Host {
		BeatBlockContext ctx();

		PresentationStateCoordinator presentation();

		double previewTimelineTimeSeconds();

		void seekPreviewClock(double timeSeconds);

		/** Stop ordinary play/preview transport (audio + driving) when not yet isolated. */
		void stopOrdinaryPlayback();

		void resetTimelineAnimationScheduling();

		void clearGlobalVfxPresentation();

		void setDriving(boolean driving);

		void setDrivingCompilePolicy(CompilePolicy policy);

		void setCompiledPlayback(@Nullable CompiledTimelineSnapshot snapshot);

		PlaybackEngine playbackEngine();

		void restoreTimelineMutationSnapshot();

		void tickExportReconstruction(double timeSeconds, @Nullable World world);

		void syncStageEventsFormal(double timeSeconds);
	}

	private final Host host;
	private volatile boolean isolated;
	private @Nullable ExportPresentationSnapshot activeSnapshot;

	public ExportPlaybackBridge(Host host) {
		this.host = host;
	}

	public boolean isIsolated() {
		return isolated;
	}

	public @Nullable ExportPresentationSnapshot activeSnapshot() {
		return activeSnapshot;
	}

	public ExportPresentationSnapshot begin() {
		ClientThreadGuard.assertClientThread();
		double seekSeconds = host.previewTimelineTimeSeconds();
		MinecraftClient client = MinecraftClient.getInstance();
		World world = client != null ? client.world : null;
		float rain = 0f;
		float thunder = 0f;
		if (world != null) {
			rain = world.getRainGradient(0f);
			thunder = world.getThunderGradient(0f);
		}
		Map<String, Float> stemGains = Map.of();
		var mixer = host.ctx().stemMixer();
		if (mixer != null) {
			stemGains = mixer.snapshotStemGains();
		}
		CameraRuntime.Owner owner = CameraRuntime.getInstance().isTimelineOwner()
			? CameraRuntime.Owner.TIMELINE
			: CameraRuntime.Owner.PLAYER;
		var snapshot = new ExportPresentationSnapshot(
			seekSeconds,
			EnvironmentLightingRuntime.current(),
			rain,
			thunder,
			stemGains,
			owner,
			CameraRuntime.getInstance().getCurrentSample(),
			captureExportVfxStateAt(seekSeconds)
		);
		isolated = true;
		activeSnapshot = snapshot;
		freeze(snapshot);
		return snapshot;
	}

	public void end(@Nullable ExportPresentationSnapshot snapshot) {
		ClientThreadGuard.assertClientThread();
		isolated = false;
		activeSnapshot = null;
		if (snapshot == null) {
			host.resetTimelineAnimationScheduling();
			host.clearGlobalVfxPresentation();
			TimelineCameraController.getInstance().onTimelineUiClosed();
			host.seekPreviewClock(0.0);
			host.setDriving(false);
			host.setDrivingCompilePolicy(CompilePolicy.STRICT);
			host.setCompiledPlayback(null);
			host.playbackEngine().reset();
			return;
		}
		host.resetTimelineAnimationScheduling();
		restorePresentation(snapshot);
		TimelineCameraController.getInstance().onTimelineUiClosed();
		host.seekPreviewClock(snapshot.restoreTimelineTimeSeconds());
		host.setDriving(false);
		host.setDrivingCompilePolicy(CompilePolicy.STRICT);
		host.setCompiledPlayback(null);
		host.playbackEngine().reset();
	}

	/** After each exported frame capture: restore frozen editor presentation for the next seek. */
	public void restoreAfterFrame() {
		if (!isolated || activeSnapshot == null) {
			return;
		}
		host.restoreTimelineMutationSnapshot();
		var engine = host.ctx().blockAnimationEngine();
		if (engine != null) {
			engine.clear();
		}
		freeze(activeSnapshot);
	}

	public void prepareFrame(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		prepareFrameCommon(timeSeconds);
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null) {
			host.tickExportReconstruction(timeSeconds, world);
		}
	}

	public void prepareFrameFromSnapshot(@Nullable CompiledTimelineSnapshot snapshot, double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		if (snapshot == null) {
			prepareFrame(timeSeconds);
			return;
		}
		prepareFrameCommon(timeSeconds);
		host.setCompiledPlayback(snapshot);
		host.playbackEngine().load(snapshot);
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null) {
			var resolved = StageStateResolver.resolve(snapshot, timeSeconds);
			ExportChunkPreloader.ensureChunksLoadedAndAwait(
				world,
				resolved.requiredBuildBlockPositions(),
				BeatBlockAuthoritativeWorldMutator.DEFAULT_AWAIT_TIMEOUT
			);
			host.tickExportReconstruction(timeSeconds, world);
		} else {
			host.syncStageEventsFormal(timeSeconds);
			var engine = host.ctx().blockAnimationEngine();
			if (engine != null) {
				var buildSequencer = engine.getBuildSequencer();
				if (buildSequencer != null) {
					buildSequencer.setExecutionPolicy(
						PlaybackExecutionMode.EXPORT_RECONSTRUCTION.buildPolicy());
				}
				engine.tick(timeSeconds, null, WorldMutationSink.NO_OP);
			}
		}
	}

	private void prepareFrameCommon(double timeSeconds) {
		if (isolated) {
			host.setDriving(false);
			host.setDrivingCompilePolicy(CompilePolicy.STRICT);
		} else {
			host.stopOrdinaryPlayback();
			host.seekPreviewClock(timeSeconds);
		}
		host.resetTimelineAnimationScheduling();
	}

	private void freeze(ExportPresentationSnapshot snapshot) {
		restorePresentation(snapshot);
		restoreCamera(snapshot);
	}

	private void restorePresentation(ExportPresentationSnapshot snapshot) {
		var lighting = snapshot.environmentLighting().toPayload("export-restore");
		PresentationStateCoordinator presentation = host.presentation();
		presentation.syncEnvironmentLighting(lighting);
		presentation.restoreExportVfxOverlays(snapshot.vfxState(), snapshot.restoreTimelineTimeSeconds());
		presentation.restoreClientWeather(snapshot.rainGradient(), snapshot.thunderGradient());
		var mixer = host.ctx().stemMixer();
		if (mixer != null) {
			mixer.restoreStemGains(snapshot.stemGains());
		}
	}

	private void restoreCamera(ExportPresentationSnapshot snapshot) {
		var runtime = CameraRuntime.getInstance();
		var sample = snapshot.preExportCameraSample();
		if (snapshot.cameraOwner() == CameraRuntime.Owner.TIMELINE && sample != null) {
			runtime.setOwner(CameraRuntime.Owner.TIMELINE);
			runtime.applyTimelineSample(sample);
			return;
		}
		runtime.setOwner(CameraRuntime.Owner.PLAYER);
		if (sample != null) {
			runtime.syncPlayerToSample(sample);
		}
	}

	private @Nullable ExportVfxState captureExportVfxStateAt(double seekSeconds) {
		var timeline = host.ctx().timeline();
		if (timeline == null) {
			return null;
		}
		try {
			CompiledTimelineSnapshot program = TimelineCompiler.compile(
				timeline,
				host.ctx().blockAnimationEngine(),
				host.ctx().buildLayerManager()
			);
			return ExportVfxState.resolve(program.globalEvents(), seekSeconds);
		} catch (TimelineCompilationException ex) {
			return null;
		}
	}
}
