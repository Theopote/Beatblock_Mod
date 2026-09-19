package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.export.ExportPlaybackBridge;
import com.beatblock.client.export.ExportPresentationSnapshot;
import com.beatblock.client.vfx.VfxEmitter;
import com.beatblock.client.camera.CameraRuntime;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventExecutor;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.CompiledStageEvent;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.PlaybackEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 第 3 层 — 客户端播放编排：按 Timeline 时钟推进音频预览与舞台/相机回放。
 * <p>
 * Delegates to {@link PresentationStateCoordinator}, {@link StageEventDispatcher},
 * {@link ExportPlaybackBridge}, and {@link StagePlaybackCoordinator}.
 * Retains transport flags, world-mutation snapshot restore, and client tick orchestration.
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
	/** Compile policy chosen when driving started; reused for hot-reload. */
	private CompilePolicy drivingCompilePolicy = CompilePolicy.STRICT;
	private final PresentationStateCoordinator presentation;
	private final StageEventDispatcher stageEventDispatcher;
	private final ExportPlaybackBridge exportBridge;
	private final StagePlaybackCoordinator stagePlayback;
	private final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private RegistryKey<World> timelineMutationWorldKey;
	private volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public BeatBlockClientDriver(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
		this.presentation = new PresentationStateCoordinator(
			this.contextSource,
			this::isExportIsolated
		);
		this.stagePlayback = new StagePlaybackCoordinator(new StagePlaybackCoordinator.Host() {
			@Override
			public BeatBlockContext ctx() {
				return BeatBlockClientDriver.this.ctx();
			}

			@Override
			public boolean isExportIsolated() {
				return BeatBlockClientDriver.this.isExportIsolated();
			}

			@Override
			public CompilePolicy drivingCompilePolicy() {
				return drivingCompilePolicy;
			}

			@Override
			public void setDrivingCompilePolicy(CompilePolicy policy) {
				drivingCompilePolicy = policy != null ? policy : CompilePolicy.STRICT;
			}

			@Override
			public void restoreTimelineMutationSnapshot() {
				BeatBlockClientDriver.this.restoreTimelineMutationSnapshot();
			}

			@Override
			public void clearGlobalVfxPresentation() {
				BeatBlockClientDriver.this.clearGlobalVfxPresentation();
			}

			@Override
			public void syncStatefulGlobalVfx(
				@org.jspecify.annotations.Nullable CompiledTimelineSnapshot program,
				double timeSeconds
			) {
				presentation.syncStateful(program, timeSeconds);
			}

			@Override
			public void applyStageEvent(
				TimelineAnimationEvent event,
				@org.jspecify.annotations.Nullable CompiledStageEvent compiledHint,
				boolean previewOnly,
				double[] referenceBeats,
				double bpm
			) {
				applyTimelineActionEvent(event, compiledHint, previewOnly, referenceBeats, bpm);
			}

			@Override
			public void onCompiledGlobalEvent(CompiledGlobalEvent event) {
				BeatBlockClientDriver.this.onCompiledGlobalEvent(event);
			}

			@Override
			public boolean consumePendingWorldReconstruct() {
				return BeatBlockClientDriver.this.consumePendingWorldReconstruct();
			}

			@Override
			public void resetTimelineAnimationScheduling() {
				BeatBlockClientDriver.this.resetTimelineAnimationScheduling();
			}
		});
		this.exportBridge = new ExportPlaybackBridge(new ExportPlaybackBridge.Host() {
			@Override
			public BeatBlockContext ctx() {
				return BeatBlockClientDriver.this.ctx();
			}

			@Override
			public PresentationStateCoordinator presentation() {
				return presentation;
			}

			@Override
			public double previewTimelineTimeSeconds() {
				return previewTimelineTimeSecondsInternal();
			}

			@Override
			public void seekPreviewClock(double timeSeconds) {
				BeatBlockClientDriver.this.seekPreviewClock(timeSeconds);
			}

			@Override
			public void stopOrdinaryPlayback() {
				stopPlaybackInternal();
			}

			@Override
			public void resetTimelineAnimationScheduling() {
				BeatBlockClientDriver.this.resetTimelineAnimationScheduling();
			}

			@Override
			public void clearGlobalVfxPresentation() {
				BeatBlockClientDriver.this.clearGlobalVfxPresentation();
			}

			@Override
			public void setDriving(boolean value) {
				driving = value;
			}

			@Override
			public void setDrivingCompilePolicy(CompilePolicy policy) {
				drivingCompilePolicy = policy != null ? policy : CompilePolicy.STRICT;
			}

			@Override
			public void setCompiledPlayback(@org.jspecify.annotations.Nullable CompiledTimelineSnapshot snapshot) {
				stagePlayback.setFormalProgram(snapshot);
			}

			@Override
			public PlaybackEngine playbackEngine() {
				return stagePlayback.formalEngine();
			}

			@Override
			public void restoreTimelineMutationSnapshot() {
				BeatBlockClientDriver.this.restoreTimelineMutationSnapshot();
			}

			@Override
			public void tickExportReconstruction(double timeSeconds, @org.jspecify.annotations.Nullable World world) {
				tickBlockAnimationEngine(timeSeconds, PlaybackExecutionMode.EXPORT_RECONSTRUCTION, world);
			}

			@Override
			public void syncStageEventsFormal(double timeSeconds) {
				stagePlayback.sync(timeSeconds, false);
			}
		});
		this.stageEventDispatcher = new StageEventDispatcher(
			new StageEventDispatcher.Host() {
				@Override
				public BeatBlockContext ctx() {
					return BeatBlockClientDriver.this.ctx();
				}

				@Override
				public @org.jspecify.annotations.Nullable CompiledStageEvent resolveCompiled(
					TimelineAnimationEvent event
				) {
					return stagePlayback.findCompiledStage(event);
				}

				@Override
				public void recordActionReport(
					TimelineAnimationEvent event,
					int mutationCount,
					String status,
					String detail
				) {
					BeatBlockClientDriver.this.recordActionReport(event, mutationCount, status, detail);
				}

				@Override
				public void captureTimelineMutationOriginalState(
					World world,
					BlockPos pos,
					BlockState currentState
				) {
					BeatBlockClientDriver.this.captureTimelineMutationOriginalState(world, pos, currentState);
				}
			},
			this::isExportIsolated
		);
	}

	private boolean isExportIsolated() {
		return exportBridge.isIsolated();
	}

	public static void install(Supplier<BeatBlockContext> contextSource) {
		instance = new BeatBlockClientDriver(contextSource);
	}

	static void resetForTests() {
		instance = null;
	}

	static @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlaybackForTests() {
		return instance != null ? instance.stagePlayback.formalProgram() : null;
	}

	static int scheduledStageCountForTests() {
		return instance != null ? instance.stagePlayback.scheduledStageCount() : 0;
	}

	/** 单元测试：驱动 scrub 预览 reconstruct（不写世界）。 */
	static void syncPreviewStageForTests(double timeSeconds) {
		requireInstance().syncPreviewStageForTestsInternal(timeSeconds);
	}

	private void syncPreviewStageForTestsInternal(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		stagePlayback.syncPreviewForTests(timeSeconds);
	}

	/** Advances formal playback to {@code timeSeconds} while driving (test helper). */
	static void advanceFormalPlaybackForTests(double timeSeconds) {
		requireInstance().advanceFormalPlaybackForTestsInternal(timeSeconds);
	}

	private void advanceFormalPlaybackForTestsInternal(double timeSeconds) {
		ClientThreadGuard.assertClientThread();
		if (!driving || stagePlayback.formalProgram() == null) {
			return;
		}
		stagePlayback.advanceFormalForTests(timeSeconds);
	}

	public static @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlayback() {
		return instance != null ? instance.stagePlayback.formalProgram() : null;
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
		PlaybackExecutionMode mode = currentExecutionMode();
		if (!mode.blocksOrdinaryClientTick()) {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		}

		// 导出重建由 VideoExportCoordinator 独占驱动，禁止普通 preview/realtime tick 推进同一引擎
		if (mode.blocksOrdinaryClientTick()) {
			return;
		}

		if (mode == PlaybackExecutionMode.REALTIME_PLAYBACK) {
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
			tickBlockAnimationEngine(currentTime, PlaybackExecutionMode.REALTIME_PLAYBACK, world);
			return;
		}

		if (world != null && engine != null && ctx().timeline() != null) {
			tickBlockAnimationEngine(previewTimelineTimeSeconds(), PlaybackExecutionMode.EDITOR_PREVIEW, world);
		}
	}

	/** 当前 client tick 应使用的执行模式（导出会话期间强制 EXPORT_RECONSTRUCTION）。 */
	PlaybackExecutionMode currentExecutionMode() {
		if (exportBridge.isIsolated()) {
			return PlaybackExecutionMode.EXPORT_RECONSTRUCTION;
		}
		if (driving) {
			return PlaybackExecutionMode.REALTIME_PLAYBACK;
		}
		return PlaybackExecutionMode.EDITOR_PREVIEW;
	}

	public static PlaybackExecutionMode executionMode() {
		return requireInstance().currentExecutionMode();
	}

	private void tickBlockAnimationEngine(double currentTime, PlaybackExecutionMode mode, World world) {
		var engine = ctx().blockAnimationEngine();
		if (engine == null) return;
		boolean previewOnly = mode.isPreviewOnly();
		syncStageEvents(currentTime, previewOnly);
		var buildSequencer = engine.getBuildSequencer();
		if (buildSequencer != null) {
			// 必须在 sync 之后：rewind 路径不得把导出策略打回 realtime
			buildSequencer.setExecutionPolicy(mode.buildPolicy());
		}
		WorldMutationSink sink = previewOnly
			? WorldMutationSink.visualPreview(engine.getAnimationPlayer())
			: (mode == PlaybackExecutionMode.EXPORT_RECONSTRUCTION
				? BeatBlockAuthoritativeWorldMutator.awaitingSinkFor(engine.getBlockControlExecutor(), world)
				: BeatBlockAuthoritativeWorldMutator.sinkFor(engine.getBlockControlExecutor(), world));
		engine.tick(currentTime, world, sink);
		if (mode.writesAuthoritativeWorld() && world != null) {
			VfxEmitter.emit(MinecraftClient.getInstance(), engine.getLastInfluenceFrame());
		}
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
			currentTime = Math.max(0, stagePlayback.lastStageEventTime());
		}
		stagePlayback.hotReloadFormal(currentTime);
	}

	/** Clear screen overlays + environment lighting before reconstruct. */
	private void clearGlobalVfxPresentation() {
		presentation.clear();
	}

	private void startDrivingInternal() {
		ClientThreadGuard.assertClientThread();
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		stagePlayback.compileAndLoadFormal(stagePlayback.consumeStartDrivingPolicy());
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
		stagePlayback.resetFormal();
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
		return requireInstance().exportBridge.isIsolated();
	}

	/** 单帧捕获完成后恢复编辑态 presentation，供下一帧 seek 继续使用隔离路径。 */
	public static void restoreIsolatedPresentationAfterExportFrame() {
		requireInstance().exportBridge.restoreAfterFrame();
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
		stagePlayback.resetFormal();
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
		requireInstance().exportBridge.prepareFrame(timeSeconds);
	}

	/**
	 * 视频导出专用：基于冻结的编译快照 seek，确保舞台/镜头/VFX 与正式播放一致。
	 */
	public static void prepareExportFrameFromSnapshot(CompiledTimelineSnapshot snapshot, double timeSeconds) {
		requireInstance().exportBridge.prepareFrameFromSnapshot(snapshot, timeSeconds);
	}

	/** 导出开始前捕获客户端呈现态（世界 mutation 由 scheduling reset 恢复）。 */
	public static ExportPresentationSnapshot beginExportPresentation() {
		return requireInstance().exportBridge.begin();
	}

	/**
	 * 结束视频导出：恢复世界 mutation、Camera、Weather、Lighting、Overlay、AudioMix 与 seek。
	 */
	public static void endExportPresentation(ExportPresentationSnapshot snapshot) {
		requireInstance().exportBridge.end(snapshot);
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

	private void seekPreviewClock(double timeSeconds) {
		if (exportBridge.isIsolated()) {
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
	 * 调度舞台事件：Preview / Formal 均只消费 {@link CompiledTimelineSnapshot}
	 *（dirty 时重编译），经各自的 {@link PlaybackEngine} 实例派发。
	 */
	private void syncStageEvents(double currentTime, boolean previewOnly) {
		stagePlayback.sync(currentTime, previewOnly);
	}

	/** PlaybackSession Loop wrap / 回退 seek 发出的显式重建请求。 */
	private boolean consumePendingWorldReconstruct() {
		var editor = ctx().timelineEditor();
		if (editor == null) {
			return false;
		}
		return editor.getPlaybackSession().consumePendingWorldReconstruct();
	}

	GlobalEventExecutor globalEventExecutorForTests() {
		return presentation.globalEventExecutor();
	}

	private void onCompiledGlobalEvent(CompiledGlobalEvent event) {
		GlobalEventExecutor.ExecutionResult execution = presentation.executeCompiledGlobal(event);
		if (event == null) {
			return;
		}
		lastTimelineActionExecutionReport = new TimelineActionExecutionReport(
			System.currentTimeMillis(), event.id(), "", TimelineAnimationActionMode.ANIMATE, 0,
			execution.executed() ? "GLOBAL_EXECUTED" : "GLOBAL_UNSUPPORTED",
			execution.typeName() + ":" + event.name());
	}

	private void applyTimelineActionEvent(
		TimelineAnimationEvent event,
		@org.jspecify.annotations.Nullable CompiledStageEvent compiledHint,
		boolean previewOnly,
		double[] referenceBeats,
		double bpm
	) {
		stageEventDispatcher.apply(event, compiledHint, previewOnly, referenceBeats, bpm);
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
			if (exportBridge.isIsolated()) {
				BeatBlockAuthoritativeWorldMutator.restoreAuthoritativeAndAwait(
					world,
					Map.copyOf(timelineMutationSnapshot),
					BeatBlockAuthoritativeWorldMutator.DEFAULT_AWAIT_TIMEOUT
				);
			} else {
				BeatBlockAuthoritativeWorldMutator.restoreAuthoritative(world, Map.copyOf(timelineMutationSnapshot));
			}
		}
		timelineMutationSnapshot.clear();
		timelineMutationWorldKey = null;
	}

	private boolean shouldRestoreTimelineMutations() {
		CompiledTimelineSnapshot playback = stagePlayback.formalProgram();
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
		stagePlayback.resetSchedulingState();
		var engine = ctx().blockAnimationEngine();
		if (engine != null) {
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(Integer.MAX_VALUE);
			}
		}
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
