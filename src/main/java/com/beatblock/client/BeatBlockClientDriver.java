package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.client.vfx.VfxEmitter;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
import com.beatblock.engine.BlockControlExecutor;
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
import com.beatblock.timeline.playback.PerformanceCheckController;
import com.beatblock.timeline.playback.PlaybackEngine;
import com.beatblock.timeline.playback.SeekMode;
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
	private final GlobalEventExecutor globalEventExecutor;
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
		ClientThreadGuard.assertClientThread();
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		// Phase B/C: full compile → load into PlaybackEngine
		CompilePolicy policy = PerformanceCheckController.consumeNextCompilePolicy();
		compiledPlayback = TimelineCompiler.compile(
			ctx().timeline(),
			ctx().blockAnimationEngine(),
			ctx().buildLayerManager(),
			policy
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
			// Rewind: engine clears its own cursors on advance; still restore world mutations
			restoreTimelineMutationSnapshot();
			engine.clear();
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
			stageEventCursor.set(0);
			lastStageEventsGeneration = generation;
		}
		int cursor = stageEventCursor.get();
		if (cursor < 0 || cursor > events.size()) {
			cursor = 0;
			stageEventCursor.set(0);
		}
		while (cursor < events.size()) {
			TimelineAnimationEvent event = events.get(cursor);
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (scheduledStageEventIds.add(key)) {
				applyTimelineActionEvent(event, null, true, referenceBeats, bpm);
			}
			cursor++;
		}
		stageEventCursor.set(cursor);
		lastStageEventTime = currentTime;
	}

	private GlobalEventExecutor createGlobalEventExecutor() {
		return new GlobalEventExecutor(new GlobalEventExecutor.Backend() {
			@Override public boolean applyEnvironmentLighting(GlobalEventPayload.@NotNull EnvironmentLighting payload) { return false; }
			@Override public boolean applyScreenTint(GlobalEventPayload.@NotNull ScreenTint payload) { return GlobalVisualEffectOverlay.applyScreenTint(payload); }
			@Override public boolean applyLocalVisualWeather(GlobalEventPayload.@NotNull LocalVisualWeather payload) { return applyClientVisualWeather(payload); }
			@Override public boolean emitParticleBurst(GlobalEventPayload.@NotNull ParticleBurst payload) { return emitGlobalParticles(payload); }
			@Override public boolean applyScreenFlash(GlobalEventPayload.@NotNull ScreenFlash payload) { return GlobalVisualEffectOverlay.applyScreenFlash(payload); }
			@Override public boolean applyAudioMix(GlobalEventPayload.@NotNull AudioMix payload) { return applyGlobalAudioMix(payload); }
		});
	}

	GlobalEventExecutor globalEventExecutorForTests() {
		return globalEventExecutor;
	}

	private void onCompiledGlobalEvent(CompiledGlobalEvent event) {
		if (event == null) return;
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

	private boolean emitGlobalParticles(GlobalEventPayload.ParticleBurst payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		ParticleEffect particle = switch (payload.particleType().toLowerCase(Locale.ROOT)) {
			case "flame", "minecraft:flame" -> ParticleTypes.FLAME;
			case "crit", "minecraft:crit" -> ParticleTypes.CRIT;
			case "firework", "minecraft:firework" -> ParticleTypes.FIREWORK;
			case "end_rod", "minecraft:end_rod" -> ParticleTypes.END_ROD;
			default -> ParticleTypes.POOF;
		};
		for (int i = 0; i < payload.count(); i++) {
			double angle = i * 2.399963229728653;
			double speed = 0.04 + (i % 5) * 0.01;
			client.world.addParticleClient(particle, payload.x(), payload.y(), payload.z(),
				Math.cos(angle) * speed, 0.04 + (i % 3) * 0.02, Math.sin(angle) * speed);
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
		if (mutations.isEmpty()) {
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
