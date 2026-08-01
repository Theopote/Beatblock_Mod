package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.client.vfx.VfxEmitter;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.CompiledStageEvent;
import com.beatblock.timeline.playback.PlaybackEngine;
import com.beatblock.timeline.playback.TimelineCompiler;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
	private int stageEventCursor;
	/** 实时预览时与 Timeline generation 对齐；正式播放固定使用 compiledPlayback。 */
	private int lastStageEventsGeneration = -1;
	private @org.jspecify.annotations.Nullable CompiledTimelineSnapshot compiledPlayback;
	/** Phase C: formal play advances only over the compiled program. */
	private final PlaybackEngine playbackEngine = new PlaybackEngine();
	private static final double TIMELINE_EVENT_EPSILON = 1e-4;
	private volatile double lastStageEventTime;
	/**
	 * 播放时每帧 BUILD 世界写入上限，避免单 tick 放置海量方块卡顿。
	 * 预览路径使用 {@link WorldMutationSink#NO_OP}，不受此预算影响。
	 */
	private static final int PLAYBACK_MUTATION_BUDGET_PER_TICK = 768;
	private final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private RegistryKey<World> timelineMutationWorldKey;
	private volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public BeatBlockClientDriver(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
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
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		var engine = ctx().blockAnimationEngine();
		if (engine != null && mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			var camera = mc.gameRenderer.getCamera();
			engine.setRuntimeCameraPosition(camera.getCameraPos());
			engine.setRuntimeCameraOrientation(camera.getYaw(), camera.getPitch());
		}
		com.beatblock.client.camera.TimelineCameraController.getInstance().tick();

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
			? WorldMutationSink.NO_OP
			: BeatBlockAuthoritativeWorldMutator.sinkFor(engine.getBlockControlExecutor(), world);
		engine.tick(currentTime, previewOnly ? null : world, sink);
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

	private void startDrivingInternal() {
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		// Phase B/C: full compile → load into PlaybackEngine
		compiledPlayback = TimelineCompiler.compile(
			ctx().timeline(),
			ctx().blockAnimationEngine(),
			ctx().buildLayerManager()
		);
		playbackEngine.load(compiledPlayback);
		driving = true;
	}

	public static void stopDriving() {
		requireInstance().stopDrivingInternal();
	}

	private void stopDrivingInternal() {
		driving = false;
		resetTimelineAnimationScheduling();
		playbackEngine.reset();
		compiledPlayback = null;
	}

	public static boolean isDriving() {
		return requireInstance().driving;
	}

	public static void stopPlayback() {
		requireInstance().stopPlaybackInternal();
	}

	private void stopPlaybackInternal() {
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

	private void prepareExportFrameInternal(double timeSeconds) {
		stopPlaybackInternal();
		var editor = ctx().timelineEditor();
		if (editor != null) {
			editor.getPlaybackSession().seek(timeSeconds);
		} else {
			var musicPlayer = ctx().musicPlayer();
			if (musicPlayer != null) {
				musicPlayer.setCurrentTimeSeconds(timeSeconds);
			}
			var stemMixer = ctx().stemMixer();
			if (stemMixer != null && stemMixer.hasStems()) {
				stemMixer.setCurrentTimeSeconds(timeSeconds);
			}
		}
		resetTimelineAnimationScheduling();
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null) {
			tickBlockAnimationEngine(timeSeconds, true, world);
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
		if (currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime) {
			// Rewind: engine clears its own cursors on advance; still restore world mutations
			restoreTimelineMutationSnapshot();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(PLAYBACK_MUTATION_BUDGET_PER_TICK);
			}
		}
		CompiledTimelineSnapshot playback = compiledPlayback;
		if (playback == null) {
			playback = TimelineCompiler.compile(timeline, engine, ctx().buildLayerManager());
			compiledPlayback = playback;
			playbackEngine.load(playback);
		}
		double[] referenceBeats = playback.referenceBeatTimesSeconds();
		double bpm = playback.bpm();
		playbackEngine.advance(
			currentTime,
			(compiled, event) -> applyTimelineActionEvent(event, compiled, false, referenceBeats, bpm),
			this::onCompiledGlobalEvent
		);
		lastStageEventTime = currentTime;
	}

	private void syncStageEventsPreview(double currentTime, com.beatblock.timeline.Timeline timeline,
		com.beatblock.engine.BlockAnimationEngine engine) {
		if (currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime) {
			resetTimelineAnimationScheduling();
		}
		List<TimelineAnimationEvent> events = timeline.getStageEvents();
		double[] referenceBeats = readReferenceBeatTimes();
		double bpm = timeline.getBpm() > 0 ? timeline.getBpm() : 120.0;
		int generation = timeline.getStageEventsGeneration();
		if (generation != lastStageEventsGeneration) {
			stageEventCursor = 0;
			lastStageEventsGeneration = generation;
		}
		if (stageEventCursor < 0 || stageEventCursor > events.size()) {
			stageEventCursor = 0;
		}
		while (stageEventCursor < events.size()) {
			TimelineAnimationEvent event = events.get(stageEventCursor);
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (scheduledStageEventIds.add(key)) {
				applyTimelineActionEvent(event, null, true, referenceBeats, bpm);
			}
			stageEventCursor++;
		}
		lastStageEventTime = currentTime;
	}

	private void onCompiledGlobalEvent(CompiledGlobalEvent event) {
		// Phase C: formal global/VFX cues are frozen; dispatch is intentionally light
		// (lighting/special hooks can subscribe here later without re-reading the document).
		if (event == null) {
			return;
		}
		// Keep last report style diagnostics for tools
		TimelineActionExecutionReport report = new TimelineActionExecutionReport(
			System.currentTimeMillis(),
			event.id(),
			"",
			TimelineAnimationActionMode.ANIMATE,
			0,
			"GLOBAL",
			event.typeName() + ":" + event.name()
		);
		lastTimelineActionExecutionReport = report;
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
		if (previewOnly && actionMode != TimelineAnimationActionMode.ANIMATE) {
			return;
		}
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
				recordActionReport(event, inst.getTotalBlocks(), "BUILD", "scheduled-" + inst.getTotalBlocks() + "-blocks");
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
		if (mutations == null || mutations.isEmpty()) {
			String detail = plan.skipReason() != null
				? "skip-" + plan.skipReason().name().toLowerCase(Locale.ROOT)
				: "skip-no-change";
			recordActionReport(event, 0, "SKIPPED", detail);
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
		if (eventId != null && !eventId.isBlank()) {
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
		restoreTimelineMutationSnapshot();
		scheduledStageEventIds.clear();
		stageEventCursor = 0;
		lastStageEventsGeneration = -1;
		lastStageEventTime = 0.0;
		// Keep loaded program; only clear engine scheduling state on hard stop via playbackEngine.reset()
		if (playbackEngine.isLoaded()) {
			// Soft rewind path: re-load same program to clear engine cursors without dropping snapshot
			playbackEngine.load(compiledPlayback);
		}
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
