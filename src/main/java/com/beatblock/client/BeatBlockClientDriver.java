package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.automap.vfx.ActiveGlobalEffectState;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.export.ExportPresentationSnapshot;
import com.beatblock.client.export.ExportVfxState;
import com.beatblock.client.vfx.VfxEmitter;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
import com.beatblock.client.camera.CameraRuntime;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.ScrubPreviewOverlay;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventExecutor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.CompiledStageEvent;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.CompileResult;
import com.beatblock.timeline.playback.PerformanceCheckController;
import com.beatblock.timeline.playback.PlaybackEngine;
import com.beatblock.timeline.playback.SeekMode;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 第 3 层 — 客户端播放编排：按 Timeline 时钟推进音频预览与舞台/相机回放。
 * <p>
 * 只派发 {@link TimelineAnimationEvent} 给 {@link com.beatblock.engine.BlockAnimationEngine}；
 * 相机由 {@link com.beatblock.client.camera.TimelineCameraController} 独立处理。
 */
public final class BeatBlockClientDriver {

	public record TimelineActionExecutionReport(
		long timestampMs,
		String eventId,
		String targetObjectId,
		TimelineAnimationActionMode actionMode,
		int mutationCount,
		String status,
		String detail
	) {}

	private static BeatBlockClientDriver instance;

	private final Supplier<BeatBlockContext> contextSource;

	private volatile long lastTickNanos;
	private volatile boolean driving;
	/** 已调度的舞台事件（预览路径使用；正式播放由 {@link PlaybackEngine} 去重）。 */
	private final Set<String> scheduledStageEventIds = new HashSet<>();
	/**
	 * 预览路径：统一事件列表上的双指针游标。
	 * 正式播放使用 {@link #playbackEngine}。
	 */
	private final AtomicInteger stageEventCursor = new AtomicInteger(0);
	/** 实时预览时与 Timeline generation 对齐；正式播放固定使用 compiledPlayback。 */
	private volatile int lastStageEventsGeneration = -1;
	private @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlayback;
	/** Phase C: formal play advances only over the compiled program. */
	private final PlaybackEngine playbackEngine = new PlaybackEngine();
	/** Scrub 预览专用：与正式播放隔离，避免游标互相污染。 */
	private final PlaybackEngine previewPlaybackEngine = new PlaybackEngine();
	private @org.jspecify.annotations.Nullable CompiledTimelineSnapshot previewCompiledPlayback;
	private volatile long lastPreviewDocumentGeneration = -1L;
	/** Compile policy chosen when driving started; reused for hot-reload. */
	private CompilePolicy drivingCompilePolicy = CompilePolicy.STRICT;
	private final GlobalEventExecutor globalEventExecutor;
	private static final double TIMELINE_EVENT_EPSILON = 1e-4;
	private volatile double lastStageEventTime;
	/** 视频导出期间隔离 live presentation，帧捕获后再恢复 {@link #exportPresentationSnapshot}。 */
	private volatile boolean exportPresentationIsolated;
	private @org.jspecify.annotations.Nullable ExportPresentationSnapshot exportPresentationSnapshot;
	/**
	 * 播放时每帧 BUILD 世界写入上限，避免单 tick 放置海量方块卡顿。
	 * 预览路径使用 {@link WorldMutationSink#visualPreview}，不受此预算影响。
	 */
	private static final int PLAYBACK_MUTATION_BUDGET_PER_TICK = 768;
	private final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private RegistryKey<World> timelineMutationWorldKey;
	private volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public BeatBlockClientDriver(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
		this.globalEventExecutor = createGlobalEventExecutor();
	}

	public static void install(Supplier<BeatBlockContext> contextSource) {
		instance = new BeatBlockClientDriver(contextSource);
	}

	static void resetForTests() {
		instance = null;
	}

	static @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlaybackForTests() {
		return instance != null ? instance.compiledPlayback : null;
	}

	static int scheduledStageCountForTests() {
		return instance != null ? instance.playbackEngine.scheduledStageCount() : 0;
	}

	/** 单元测试：驱动 scrub 预览 reconstruct（不写世界）。 */
	static void syncPreviewStageForTests(double timeSeconds) {
		requireInstance().syncPreviewStageForTestsInternal(timeSeconds);
	}

	private void syncPreviewStageForTestsInternal(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		var timeline = ctx().timeline();
		var engine = ctx().blockAnimationEngine();
		if (timeline == null || engine == null) {
			return;
		}
		syncStageEventsPreview(timeSeconds, timeline, engine);
	}

	/** Advances formal playback to {@code timeSeconds} while driving (test helper). */
	static void advanceFormalPlaybackForTests(double timeSeconds) {
		requireInstance().advanceFormalPlaybackForTestsInternal(timeSeconds);
	}

	private void advanceFormalPlaybackForTestsInternal(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		if (!driving || compiledPlayback == null) {
			return;
		}
		// Drive PlaybackEngine directly so unit tests without a BlockAnimationEngine
		// still exercise scheduling / hot-reload reconstruct semantics.
		playbackEngine.advance(timeSeconds, (compiled, event) -> {}, this::onCompiledGlobalEvent);
		lastStageEventTime = timeSeconds;
	}

	public static @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlayback() {
		return instance != null ? instance.compiledPlayback : null;
	}

	private static BeatBlockClientDriver requireInstance() {
		if (instance == null) {
			install(BeatBlock::getContext);
		}
		return instance;
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	public static void onClientTick() {
		requireInstance().tick();
	}

	void tick() {
		ClientThreadGuard.assertClientThread();
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		var engine = ctx().blockAnimationEngine();
		if (engine != null && mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			var camera = mc.gameRenderer.getCamera();
			engine.setRuntimeCameraPosition(camera.getCameraPos());
			engine.setRuntimeCameraOrientation(camera.getYaw(), camera.getPitch());
		}
		if (!exportPresentationIsolated) {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		}

		if (exportPresentationIsolated) {
			return;
		}

		if (driving) {
			if (world == null) return;

			long now = System.nanoTime();
			double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
			lastTickNanos = now;

			var musicPlayer = ctx().musicPlayer();
			if (musicPlayer != null) {
				musicPlayer.tick(delta);
			}
			ctx().pauseFullMixIfStemPlayback();
			double currentTime = ctx().playbackTimeSeconds();
			tickBlockAnimationEngine(currentTime, false, world);
			return;
		}

		if (world != null && engine != null && ctx().timeline() != null) {
			tickBlockAnimationEngine(previewTimelineTimeSeconds(), true, world);
		}
	}

	private void tickBlockAnimationEngine(double currentTime, boolean previewOnly, World world) {
		var engine = ctx().blockAnimationEngine();
		if (engine == null) return;
		var buildSequencer = engine.getBuildSequencer();
		if (buildSequencer != null) {
			// 正式播放限流；预览不写世界，预算保持无上限以免测试/状态机被截断
			buildSequencer.setMutationBudgetPerTick(
				previewOnly ? Integer.MAX_VALUE : PLAYBACK_MUTATION_BUDGET_PER_TICK);
		}
		syncStageEvents(currentTime, previewOnly);
		WorldMutationSink sink = previewOnly
			? WorldMutationSink.visualPreview(engine.getAnimationPlayer())
			: BeatBlockAuthoritativeWorldMutator.sinkFor(engine.getBlockControlExecutor(), world);
		engine.tick(currentTime, world, sink);
		if (!previewOnly && world != null) {
			VfxEmitter.emit(MinecraftClient.getInstance(), engine.getLastInfluenceFrame());
		}
	}

	private double[] readReferenceBeatTimes() {
		var timeline = ctx().timeline();
		if (timeline == null) {
			return new double[0];
		}
		return ReferenceBeatResolver.resolveBeatTimesSeconds(timeline);
	}

	public static void startDriving() {
		requireInstance().startDrivingInternal();
	}

	/**
	 * Hot-reloads the formal playback snapshot from the live Timeline while driving.
	 * No-op when not driving (preview already reads the live document).
	 * <p>
	 * After compile, reconstructs at the current playhead so {@link PlaybackEngine#load}
	 * does not cause the next {@code advance} to re-dispatch already-passed events.
	 */
	public static void reloadCompiledPlaybackIfDriving() {
		requireInstance().reloadCompiledPlaybackIfDrivingInternal();
	}

	private void reloadCompiledPlaybackIfDrivingInternal() {
		ClientThreadGuard.assertClientThread();
		if (!driving) {
			return;
		}
		double currentTime = ctx().playbackTimeSeconds();
		if (!Double.isFinite(currentTime) || currentTime < 0) {
			currentTime = Math.max(0, lastStageEventTime);
		}

		CompiledTimelineSnapshot next;
		try {
			CompileResult result = TimelineCompiler.compile(
				ctx().timeline(),
				ctx().blockAnimationEngine(),
				ctx().buildLayerManager(),
				drivingCompilePolicy
			);
			next = result.snapshot();
		} catch (TimelineCompilationException error) {
			BeatBlock.LOGGER.warn(
				"Timeline hot-reload compile failed; keeping previous compiled playback", error);
			return;
		}

		compiledPlayback = next;
		playbackEngine.load(compiledPlayback);
		reconstructFormalPlaybackAt(currentTime);
	}

	private void reconstructFormalPlaybackAt(double currentTime) {
		var engine = ctx().blockAnimationEngine();
		restoreTimelineMutationSnapshot();
		if (engine != null) {
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(PLAYBACK_MUTATION_BUDGET_PER_TICK);
			}
		}
		if (compiledPlayback == null) {
			lastStageEventTime = Math.max(0, currentTime);
			clearGlobalVfxPresentation();
			return;
		}
		double time = Math.max(0, currentTime);
		clearGlobalVfxPresentation();
		double[] referenceBeats = compiledPlayback.referenceBeatTimesSeconds();
		double bpm = compiledPlayback.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> applyTimelineActionEvent(event, compiled, false, referenceBeats, bpm);
		playbackEngine.seek(
			time,
			SeekMode.RECONSTRUCT_STATE,
			stageHandler,
			this::onCompiledGlobalEvent
		);
		syncStatefulGlobalVfxAt(time);
		lastStageEventTime = time;
	}

	/** Clear screen overlays + environment lighting before reconstruct. */
	private void clearGlobalVfxPresentation() {
		if (exportPresentationIsolated) {
			return;
		}
		GlobalVisualEffectOverlay.clear();
		EnvironmentLightingRuntime.clear();
	}

	/**
	 * Sample-at-time sync for continuous/envelope screen VFX and sticky environment lighting
	 * (seek + forward expiry). Particle impulses are never reconstructed here.
	 */
	private void syncStatefulGlobalVfxAt(double timeSeconds) {
		if (exportPresentationIsolated) {
			return;
		}
		if (compiledPlayback == null) {
			GlobalVisualEffectOverlay.clearScreenTint();
			GlobalVisualEffectOverlay.clearScreenFlash();
			EnvironmentLightingRuntime.clear();
			return;
		}
		ActiveGlobalEffectState active = ActiveGlobalEffectState.resolve(
			compiledPlayback.globalEvents(), timeSeconds);
		CompiledGlobalEvent lightingEvent = active.environmentLighting();
		if (lightingEvent != null && lightingEvent.payload() instanceof GlobalEventPayload.EnvironmentLighting lighting) {
			EnvironmentLightingRuntime.sync(lighting);
			GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
		} else if (lightingEvent != null && lightingEvent.payload() instanceof GlobalEventPayload.Lighting legacy) {
			var lighting = new GlobalEventPayload.EnvironmentLighting(
				legacy.name(), legacy.intensity(), legacy.r(), legacy.g(), legacy.b(), 0);
			EnvironmentLightingRuntime.sync(lighting);
			GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
		} else {
			EnvironmentLightingRuntime.clear();
			GlobalVisualEffectOverlay.syncEnvironmentLighting(null);
		}
		CompiledGlobalEvent tintEvent = active.screenTint();
		if (tintEvent != null && tintEvent.payload() instanceof GlobalEventPayload.ScreenTint tint) {
			GlobalVisualEffectOverlay.syncScreenTint(tint);
		} else {
			GlobalVisualEffectOverlay.clearScreenTint();
		}
		CompiledGlobalEvent flashEvent = active.screenFlash();
		if (flashEvent != null && flashEvent.payload() instanceof GlobalEventPayload.ScreenFlash flash) {
			GlobalVisualEffectOverlay.syncScreenFlash(flash, flashEvent.timeSeconds(), timeSeconds);
		} else {
			GlobalVisualEffectOverlay.clearScreenFlash();
		}
	}

	private void startDrivingInternal() {
		ClientThreadGuard.assertClientThread();
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		// Phase B/C: full compile → load into PlaybackEngine
		CompilePolicy policy = PerformanceCheckController.consumeNextCompilePolicy();
		drivingCompilePolicy = policy != null ? policy : CompilePolicy.STRICT;
		compiledPlayback = TimelineCompiler.compile(
			ctx().timeline(),
			ctx().blockAnimationEngine(),
			ctx().buildLayerManager(),
			drivingCompilePolicy
		).snapshot();
		playbackEngine.load(compiledPlayback);
		driving = true;
	}

	public static void stopDriving() {
		requireInstance().stopDrivingInternal();
	}

	private void stopDrivingInternal() {
		ClientThreadGuard.assertClientThread();
		driving = false;
		drivingCompilePolicy = CompilePolicy.STRICT;
		resetTimelineAnimationScheduling();
		playbackEngine.reset();
		compiledPlayback = null;
		clearGlobalVfxPresentation();
	}

	public static boolean isDriving() {
		return requireInstance().driving;
	}

	public static void stopPlayback() {
		requireInstance().stopPlaybackInternal();
	}

	/** 导出启动：仅暂停音频/驱动，不清除 Camera 与 VFX（由 {@link #beginExportPresentation()} 隔离）。 */
	public static void stopPlaybackForExport() {
		requireInstance().stopPlaybackForExportInternal();
	}

	public static boolean isExportPresentationIsolated() {
		return requireInstance().exportPresentationIsolated;
	}

	/** 单帧捕获完成后恢复编辑态 presentation，供下一帧 seek 继续使用隔离路径。 */
	public static void restoreIsolatedPresentationAfterExportFrame() {
		requireInstance().restoreIsolatedPresentationAfterExportFrameInternal();
	}

	private void stopPlaybackForExportInternal() {
		ClientThreadGuard.assertClientThread();
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.pause();
		}
		var stemMixer = ctx().stemMixer();
		if (stemMixer != null && stemMixer.hasStems()) {
			stemMixer.pause();
		}
		driving = false;
		drivingCompilePolicy = CompilePolicy.STRICT;
		resetTimelineAnimationScheduling();
		playbackEngine.reset();
		compiledPlayback = null;
	}

	private void stopPlaybackInternal() {
		ClientThreadGuard.assertClientThread();
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.pause();
		}
		var stemMixer = ctx().stemMixer();
		if (stemMixer != null && stemMixer.hasStems()) {
			stemMixer.pause();
		}
		resetTimelineAnimationScheduling();
		stopDrivingInternal();
		com.beatblock.client.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
	}

	public static double previewTimelineTimeSeconds() {
		return requireInstance().previewTimelineTimeSecondsInternal();
	}

	/**
	 * 视频导出专用：将时间线 seek 到指定时刻并刷新动画/镜头预览。
	 */
	public static void prepareExportFrame(double timeSeconds) {
		requireInstance().prepareExportFrameInternal(timeSeconds);
	}

	/**
	 * 视频导出专用：基于冻结的编译快照 seek，确保舞台/镜头/VFX 与正式播放一致。
	 */
	public static void prepareExportFrameFromSnapshot(CompiledTimelineSnapshot snapshot, double timeSeconds) {
		requireInstance().prepareExportFrameFromSnapshotInternal(snapshot, timeSeconds);
	}

	/** 导出开始前捕获客户端呈现态（世界 mutation 由 scheduling reset 恢复）。 */
	public static ExportPresentationSnapshot beginExportPresentation() {
		return requireInstance().beginExportPresentationInternal();
	}

	/**
	 * 结束视频导出：恢复世界 mutation、Camera、Weather、Lighting、Overlay、AudioMix 与 seek。
	 */
	public static void endExportPresentation(ExportPresentationSnapshot snapshot) {
		requireInstance().endExportPresentationInternal(snapshot);
	}

	/** @deprecated 使用 {@link #endExportPresentation(ExportPresentationSnapshot)} */
	@Deprecated
	public static void endExportPresentation(double restoreTimelineTimeSeconds) {
		endExportPresentation(new ExportPresentationSnapshot(
			restoreTimelineTimeSeconds,
			EnvironmentLightingRuntime.State.NEUTRAL,
			0f,
			0f,
			Map.of(),
			CameraRuntime.Owner.PLAYER,
			null,
			null
		));
	}

	private void prepareExportFrameInternal(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		stopPlaybackInternal();
		seekPreviewClock(timeSeconds);
		resetTimelineAnimationScheduling();
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null) {
			tickBlockAnimationEngine(timeSeconds, true, world);
		}
	}

	private ExportPresentationSnapshot beginExportPresentationInternal() {
		ClientThreadGuard.assertClientThread();
		double seekSeconds = previewTimelineTimeSeconds();
		MinecraftClient client = MinecraftClient.getInstance();
		World world = client != null ? client.world : null;
		float rain = 0f;
		float thunder = 0f;
		if (world != null) {
			rain = world.getRainGradient(0f);
			thunder = world.getThunderGradient(0f);
		}
		Map<String, Float> stemGains = Map.of();
		var mixer = ctx().stemMixer();
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
		exportPresentationIsolated = true;
		exportPresentationSnapshot = snapshot;
		freezeIsolatedPresentation(snapshot);
		return snapshot;
	}

	private void freezeIsolatedPresentation(ExportPresentationSnapshot snapshot) {
		restoreExportPresentationSnapshot(snapshot);
		restoreExportCamera(snapshot);
	}

	private @org.jspecify.annotations.Nullable ExportVfxState captureExportVfxStateAt(double seekSeconds) {
		var timeline = ctx().timeline();
		if (timeline == null) {
			return null;
		}
		try {
			CompiledTimelineSnapshot program = TimelineCompiler.compile(
				timeline,
				ctx().blockAnimationEngine(),
				ctx().buildLayerManager()
			);
			return ExportVfxState.resolve(program.globalEvents(), seekSeconds);
		} catch (TimelineCompilationException ex) {
			return null;
		}
	}

	private void endExportPresentationInternal(ExportPresentationSnapshot snapshot) {
		ClientThreadGuard.assertClientThread();
		exportPresentationIsolated = false;
		exportPresentationSnapshot = null;
		if (snapshot == null) {
			resetTimelineAnimationScheduling();
			clearGlobalVfxPresentation();
			com.beatblock.client.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
			seekPreviewClock(0.0);
			driving = false;
			drivingCompilePolicy = CompilePolicy.STRICT;
			compiledPlayback = null;
			playbackEngine.reset();
			return;
		}
		resetTimelineAnimationScheduling();
		restoreExportPresentationSnapshot(snapshot);
		com.beatblock.client.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
		seekPreviewClock(snapshot.restoreTimelineTimeSeconds());
		driving = false;
		drivingCompilePolicy = CompilePolicy.STRICT;
		compiledPlayback = null;
		playbackEngine.reset();
	}

	private void restoreExportPresentationSnapshot(ExportPresentationSnapshot snapshot) {
		var lighting = snapshot.environmentLighting().toPayload("export-restore");
		EnvironmentLightingRuntime.sync(lighting);
		GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
		restoreExportVfxOverlays(snapshot.vfxState(), snapshot.restoreTimelineTimeSeconds());
		restoreClientWeather(snapshot.rainGradient(), snapshot.thunderGradient());
		var mixer = ctx().stemMixer();
		if (mixer != null) {
			mixer.restoreStemGains(snapshot.stemGains());
		}
	}

	private void restoreExportCamera(ExportPresentationSnapshot snapshot) {
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

	private void restoreIsolatedPresentationAfterExportFrameInternal() {
		if (!exportPresentationIsolated || exportPresentationSnapshot == null) {
			return;
		}
		restoreTimelineMutationSnapshot();
		var engine = ctx().blockAnimationEngine();
		if (engine != null) {
			engine.clear();
		}
		freezeIsolatedPresentation(exportPresentationSnapshot);
	}

	private void restoreExportVfxOverlays(
		@org.jspecify.annotations.Nullable ExportVfxState vfx,
		double timelineTimeSeconds
	) {
		GlobalVisualEffectOverlay.clearScreenTint();
		GlobalVisualEffectOverlay.clearScreenFlash();
		if (vfx == null) {
			return;
		}
		if (vfx.activeTint() != null
			&& vfx.activeTint().payload() instanceof GlobalEventPayload.ScreenTint tint) {
			GlobalVisualEffectOverlay.syncScreenTint(tint);
		}
		if (vfx.activeFlash() != null
			&& vfx.activeFlash().payload() instanceof GlobalEventPayload.ScreenFlash flash) {
			GlobalVisualEffectOverlay.syncScreenFlash(
				flash,
				vfx.activeFlash().timeSeconds(),
				timelineTimeSeconds
			);
		}
	}

	private void restoreClientWeather(float rainGradient, float thunderGradient) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return;
		}
		client.world.setRainGradient(Math.max(0f, Math.min(1f, rainGradient)));
		client.world.setThunderGradient(Math.max(0f, Math.min(1f, thunderGradient)));
	}

	private void prepareExportFrameFromSnapshotInternal(CompiledTimelineSnapshot snapshot, double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		if (snapshot == null) {
			prepareExportFrameInternal(timeSeconds);
			return;
		}
		stopPlaybackInternal();
		seekPreviewClock(timeSeconds);
		resetTimelineAnimationScheduling();
		compiledPlayback = snapshot;
		playbackEngine.load(snapshot);
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null) {
			tickBlockAnimationEngine(timeSeconds, false, world);
		} else {
			syncStageEvents(timeSeconds, false);
			var engine = ctx().blockAnimationEngine();
			if (engine != null) {
				engine.tick(timeSeconds, null, WorldMutationSink.NO_OP);
			}
		}
	}

	private void seekPreviewClock(double timeSeconds) {
		if (exportPresentationIsolated) {
			return;
		}
		var editor = ctx().timelineEditor();
		if (editor != null) {
			editor.getPlaybackSession().seek(timeSeconds);
			return;
		}
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.setCurrentTimeSeconds(timeSeconds);
		}
		var stemMixer = ctx().stemMixer();
		if (stemMixer != null && stemMixer.hasStems()) {
			stemMixer.setCurrentTimeSeconds(timeSeconds);
		}
	}

	private double previewTimelineTimeSecondsInternal() {
		var editor = ctx().timelineEditor();
		if (editor != null) {
			return editor.getPlaybackSession().currentTimeSeconds();
		}
		var musicPlayer = ctx().musicPlayer();
		return musicPlayer != null ? musicPlayer.getCurrentTimeSeconds() : 0.0;
	}

	/**
	 * 调度舞台事件：
	 * <ul>
	 *   <li>正式播放：{@link PlaybackEngine} 只消费 {@link CompiledTimelineSnapshot}</li>
	 *   <li>预览：仍扫可编辑 Timeline，世代变化时回退游标</li>
	 * </ul>
	 */
	private void syncStageEvents(double currentTime, boolean previewOnly) {
		var timeline = ctx().timeline();
		var engine = ctx().blockAnimationEngine();
		if (timeline == null || engine == null) return;

		if (previewOnly) {
			syncStageEventsPreview(currentTime, timeline, engine);
			return;
		}

		// Formal play — PlaybackEngine only
		boolean rewinding = currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime;
		if (rewinding) {
			restoreTimelineMutationSnapshot();
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(PLAYBACK_MUTATION_BUDGET_PER_TICK);
			}
			clearGlobalVfxPresentation();
		}
		CompiledTimelineSnapshot playback = compiledPlayback;
		if (playback == null) {
			playback = TimelineCompiler.compile(timeline, engine, ctx().buildLayerManager());
			compiledPlayback = playback;
			playbackEngine.load(playback);
		}
		double[] referenceBeats = playback.referenceBeatTimesSeconds();
		double bpm = playback.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> applyTimelineActionEvent(event, compiled, false, referenceBeats, bpm);
		if (rewinding) {
			playbackEngine.seek(
				currentTime,
				SeekMode.RECONSTRUCT_STATE,
				stageHandler,
				this::onCompiledGlobalEvent
			);
		} else {
			playbackEngine.advance(currentTime, stageHandler, this::onCompiledGlobalEvent);
		}
		syncStatefulGlobalVfxAt(currentTime);
		lastStageEventTime = currentTime;
	}

	private void syncStageEventsPreview(double currentTime, com.beatblock.timeline.Timeline timeline,
		com.beatblock.engine.BlockAnimationEngine engine) {
		long documentGeneration = timeline.getDocumentGeneration();
		int stageGeneration = timeline.getStageEventsGeneration();
		boolean rewinding = currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime;
		if (rewinding
			|| documentGeneration != lastPreviewDocumentGeneration
			|| stageGeneration != lastStageEventsGeneration) {
			resetTimelineAnimationScheduling();
			previewCompiledPlayback = null;
			lastPreviewDocumentGeneration = documentGeneration;
		}

		CompiledTimelineSnapshot playback = previewCompiledPlayback;
		if (playback == null) {
			playback = TimelineCompiler.compile(timeline, engine, ctx().buildLayerManager());
			previewCompiledPlayback = playback;
			previewPlaybackEngine.load(playback);
		}

		engine.clear();
		var buildSequencer = engine.getBuildSequencer();
		if (buildSequencer != null) {
			buildSequencer.setTimeline(timeline);
			buildSequencer.setMutationBudgetPerTick(Integer.MAX_VALUE);
		}

		double[] referenceBeats = playback.referenceBeatTimesSeconds();
		double bpm = playback.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> applyTimelineActionEvent(event, compiled, true, referenceBeats, bpm);
		previewPlaybackEngine.seek(
			currentTime,
			SeekMode.RECONSTRUCT_STATE,
			stageHandler,
			ignored -> {}
		);

		lastStageEventTime = currentTime;
		lastStageEventsGeneration = stageGeneration;
	}

	private GlobalEventExecutor createGlobalEventExecutor() {
		return new GlobalEventExecutor(new GlobalEventExecutor.Backend() {
			@Override public boolean applyEnvironmentLighting(GlobalEventPayload.@NotNull EnvironmentLighting payload) {
				boolean ok = EnvironmentLightingRuntime.apply(payload);
				GlobalVisualEffectOverlay.syncEnvironmentLighting(payload);
				return ok;
			}
			@Override public boolean applyScreenTint(GlobalEventPayload.@NotNull ScreenTint payload) { return GlobalVisualEffectOverlay.applyScreenTint(payload); }
			@Override public boolean applyLocalVisualWeather(GlobalEventPayload.@NotNull LocalVisualWeather payload) { return applyClientVisualWeather(payload); }
			@Override public boolean emitParticleBurst(GlobalEventPayload.@NotNull ParticleBurst payload) { return emitGlobalParticles(payload); }
			@Override public boolean applyScreenFlash(GlobalEventPayload.@NotNull ScreenFlash payload) { return GlobalVisualEffectOverlay.applyScreenFlash(payload); }
			@Override public boolean applyAudioMix(GlobalEventPayload.@NotNull AudioMix payload) { return applyGlobalAudioMix(payload); }
			@Override public boolean applyEnvironmentReset(GlobalEventPayload.@NotNull EnvironmentReset payload) {
				return applyEnvironmentResetPresentation(payload);
			}
		});
	}

	GlobalEventExecutor globalEventExecutorForTests() {
		return globalEventExecutor;
	}

	private void onCompiledGlobalEvent(CompiledGlobalEvent event) {
		if (event == null || exportPresentationIsolated) return;
		GlobalEventExecutor.ExecutionResult execution = globalEventExecutor.execute(event);
		lastTimelineActionExecutionReport = new TimelineActionExecutionReport(
			System.currentTimeMillis(), event.id(), "", TimelineAnimationActionMode.ANIMATE, 0,
			execution.executed() ? "GLOBAL_EXECUTED" : "GLOBAL_UNSUPPORTED",
			execution.typeName() + ":" + event.name());
	}

	private boolean applyClientVisualWeather(GlobalEventPayload.LocalVisualWeather payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		String weather = payload.weatherType().toLowerCase(Locale.ROOT);
		boolean rain = "rain".equals(weather) || "thunder".equals(weather) || "storm".equals(weather);
		boolean thunder = "thunder".equals(weather) || "storm".equals(weather);
		client.world.setRainGradient(rain ? 1.0f : 0.0f);
		client.world.setThunderGradient(thunder ? 1.0f : 0.0f);
		return true;
	}

	private boolean applyEnvironmentResetPresentation(GlobalEventPayload.EnvironmentReset payload) {
		EnvironmentLightingRuntime.clear();
		GlobalVisualEffectOverlay.syncEnvironmentLighting(null);
		GlobalVisualEffectOverlay.clearScreenTint();
		applyClientVisualWeather(new GlobalEventPayload.LocalVisualWeather(
			payload != null ? payload.name() : "Clear", "clear", 0));
		var mixer = ctx().stemMixer();
		if (mixer != null) {
			mixer.setStemVolume("master", 1f);
		}
		return true;
	}

	private boolean emitGlobalParticles(GlobalEventPayload.ParticleBurst payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		net.minecraft.util.math.Vec3d origin = com.beatblock.automap.vfx.VfxParticleSubjectSupport.emissionOrigin(payload);
		ParticleEffect particle = switch (payload.particleType().toLowerCase(Locale.ROOT)) {
			case "flame", "minecraft:flame" -> ParticleTypes.FLAME;
			case "crit", "minecraft:crit" -> ParticleTypes.CRIT;
			case "firework", "minecraft:firework" -> ParticleTypes.FIREWORK;
			case "end_rod", "minecraft:end_rod" -> ParticleTypes.END_ROD;
			default -> ParticleTypes.POOF;
		};
		for (int i = 0; i < payload.count(); i++) {
			double angle = i * 2.399963229728653;
			double radius = payload.spread() * (0.5 + (i % 4) * 0.125);
			double px = origin.x + Math.cos(angle) * radius;
			double py = origin.y + payload.spread() * 0.05 * (i % 3);
			double pz = origin.z + Math.sin(angle) * radius;
			double vx = Math.cos(angle) * payload.speed();
			double vy = payload.speed() * (0.4 + (i % 5) * 0.12);
			double vz = Math.sin(angle) * payload.speed();
			client.world.addParticleClient(particle, px, py, pz, vx, vy, vz);
		}
		return true;
	}

	private boolean applyGlobalAudioMix(GlobalEventPayload.AudioMix payload) {
		var mixer = ctx().stemMixer();
		return mixer != null && mixer.setStemVolume(payload.channel(), payload.volume());
	}
	private void applyTimelineActionEvent(
		TimelineAnimationEvent event,
		@org.jspecify.annotations.Nullable CompiledStageEvent compiledHint,
		boolean previewOnly,
		double[] referenceBeats,
		double bpm
	) {
		var engine = ctx().blockAnimationEngine();
		if (event == null || engine == null) return;
		if (!passesEnergyThreshold(event)) {
			recordActionReport(event, 0, "SKIPPED", "energy-below-threshold");
			return;
		}
			TimelineAnimationActionMode actionMode = event.getActionMode();
			if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			var compiled = compiledHint != null ? compiledHint : compiledStageEvent(event);
			if (!previewOnly && compiled != null) {
				engine.scheduleTimelineEvent(compiled, referenceBeats, bpm);
			} else {
				engine.scheduleTimelineEvent(event, referenceBeats, bpm);
			}
			recordActionReport(event, 0, "ANIMATE", "scheduled");
			return;
		}

			if (actionMode == TimelineAnimationActionMode.BUILD) {
				var inst = engine.getBuildSequencer().schedule(event);
				if (inst != null) {
					String detail = previewOnly
						? "preview-scheduled-" + inst.getTotalBlocks() + "-blocks"
						: "scheduled-" + inst.getTotalBlocks() + "-blocks";
					recordActionReport(event, inst.getTotalBlocks(), "BUILD", detail);
				} else {
					recordActionReport(event, 0, "SKIPPED", "build-no-target");
				}
				return;
			}

			MinecraftClient mc = MinecraftClient.getInstance();
			World world = mc != null ? mc.world : null;
			if (world == null) {
				recordActionReport(event, 0, "SKIPPED", "no-world");
				return;
			}
			var plan = engine.planControl(event, world);
			var mutations = plan.mutations();
			if (mutations.isEmpty()) {
				String detail = plan.skipReason() != null
					? "skip-" + plan.skipReason().name().toLowerCase(Locale.ROOT)
					: "skip-no-change";
				recordActionReport(event, 0, "SKIPPED", detail);
				return;
			}
			if (previewOnly) {
				ScrubPreviewOverlay.applyMutations(engine.getAnimationPlayer(), mutations);
				recordActionReport(event, mutations.size(), "PREVIEW", "overlay");
				return;
			}
			for (BlockControlExecutor.BlockMutation mutation : mutations) {
				captureTimelineMutationOriginalState(world, mutation.pos(), mutation.fromState());
			}
			WorldMutationSink sink = BeatBlockAuthoritativeWorldMutator.sinkFor(
				engine.getBlockControlExecutor(), world);
			engine.applyControlMutations(mutations, sink);
			recordActionReport(event, mutations.size(), "APPLIED", "ok");
		}

	private @org.jspecify.annotations.Nullable CompiledStageEvent compiledStageEvent(
		TimelineAnimationEvent event) {
		if (event == null) return null;
		String id = event.getEventId();
		if (id != null && !id.isBlank()) {
			var fromEngine = playbackEngine.findCompiledStage(id);
			if (fromEngine != null) {
				return fromEngine;
			}
		}
		if (compiledPlayback == null) return null;
		for (var compiled : compiledPlayback.compiledStageEvents()) {
			if (compiled.event() == event || compiled.event().getEventId().equals(event.getEventId())) {
				return compiled;
			}
		}
		return null;
	}

	private void recordActionReport(TimelineAnimationEvent event, int mutationCount, String status, String detail) {
		if (event == null) return;
		TimelineActionExecutionReport report = new TimelineActionExecutionReport(
			System.currentTimeMillis(),
			event.getEventId(),
			event.getTargetObjectId(),
			event.getActionMode(),
			Math.max(0, mutationCount),
			status != null ? status : "UNKNOWN",
			detail != null ? detail : ""
		);
		lastTimelineActionExecutionReport = report;
		String eventId = event.getEventId();
		if (!eventId.isBlank()) {
			if (timelineActionReportByEventId.size() > MAX_ACTION_REPORT_CACHE_SIZE) {
				timelineActionReportByEventId.clear();
			}
			timelineActionReportByEventId.put(eventId, report);
		}
	}

	private boolean passesEnergyThreshold(TimelineAnimationEvent event) {
		if (event == null) return false;
		return event.getPayload().passesEnergyGate();
	}

	private void captureTimelineMutationOriginalState(World world, BlockPos pos, BlockState currentState) {
		if (!shouldRestoreTimelineMutations()) return;
		if (world == null || pos == null || currentState == null) return;
		RegistryKey<World> worldKey = world.getRegistryKey();
		if (timelineMutationWorldKey == null) {
			timelineMutationWorldKey = worldKey;
		} else if (!timelineMutationWorldKey.equals(worldKey)) {
			restoreTimelineMutationSnapshot();
			timelineMutationWorldKey = worldKey;
		}
		timelineMutationSnapshot.putIfAbsent(pos.toImmutable(), currentState);
	}

	private void restoreTimelineMutationSnapshot() {
		if (timelineMutationSnapshot.isEmpty()) {
			timelineMutationWorldKey = null;
			return;
		}
		if (!shouldRestoreTimelineMutations()) {
			timelineMutationSnapshot.clear();
			timelineMutationWorldKey = null;
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null && timelineMutationWorldKey != null && timelineMutationWorldKey.equals(world.getRegistryKey())) {
			BeatBlockAuthoritativeWorldMutator.restoreAuthoritative(world, Map.copyOf(timelineMutationSnapshot));
		}
		timelineMutationSnapshot.clear();
		timelineMutationWorldKey = null;
	}

	private boolean shouldRestoreTimelineMutations() {
		CompiledTimelineSnapshot playback = compiledPlayback;
		if (playback != null) return playback.restoreWorldMutations();
		var timeline = ctx().timeline();
		if (timeline == null) return true;
		Object raw = timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) return true;
		String mode = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}

	private void resetTimelineAnimationScheduling() {
		ClientThreadGuard.assertClientThread();
		restoreTimelineMutationSnapshot();
		scheduledStageEventIds.clear();
		stageEventCursor.set(0);
		lastStageEventsGeneration = -1;
		lastStageEventTime = 0.0;
		// Keep loaded program; only clear engine scheduling state on hard stop via playbackEngine.reset()
		if (playbackEngine.isLoaded()) {
			// Soft rewind path: re-load same program to clear engine cursors without dropping snapshot
			playbackEngine.load(compiledPlayback);
		}
		previewCompiledPlayback = null;
		lastPreviewDocumentGeneration = -1L;
		previewPlaybackEngine.reset();
		var engine = ctx().blockAnimationEngine();
		if (engine != null) {
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(Integer.MAX_VALUE);
			}
		}
	}

	private static String scheduleKey(TimelineAnimationEvent event) {
		if (event.getEventId() != null && !event.getEventId().isBlank()) {
			return event.getEventId();
		}
		return String.format(Locale.ROOT, "%s|%.6f|%s|%s",
			event.getActionMode().name(),
			event.getTimeSeconds(),
			event.getAnimationTypeId(),
			event.getTargetObjectId());
	}

	public static TimelineActionExecutionReport getLastTimelineActionExecutionReport() {
		return requireInstance().lastTimelineActionExecutionReport;
	}

	public static TimelineActionExecutionReport getTimelineActionExecutionReport(String eventId) {
		if (eventId == null || eventId.isBlank()) return null;
		return requireInstance().timelineActionReportByEventId.get(eventId);
	}

	public static void togglePlayback() {
		requireInstance().togglePlaybackInternal();
	}

	private void togglePlaybackInternal() {
		var player = ctx().activeAudioPlayer();
		if (player == null) {
			return;
		}
		if (player.isPlaying()) {
			stopPlaybackInternal();
			return;
		}
		// Hotkey play uses the same Performance check gate as Transport
		com.beatblock.timeline.playback.PerformanceCheckController.gatePlay(
			ctx().timeline(),
			ctx().blockAnimationEngine(),
			ctx().buildLayerManager(),
			() -> {
				ctx().pauseFullMixIfStemPlayback();
				player.play();
				startDrivingInternal();
			}
		);
	}
}
