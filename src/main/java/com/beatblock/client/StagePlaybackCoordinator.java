package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledStageEvent;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.CompileResult;
import com.beatblock.timeline.playback.PerformanceCheckController;
import com.beatblock.timeline.playback.PlaybackEngine;
import com.beatblock.timeline.playback.SeekMode;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import org.jspecify.annotations.Nullable;

/**
 * Formal / preview compiled playback: owns dual {@link PlaybackEngine} instances and
 * their {@link CompiledTimelineSnapshot} programs. Does not own transport or presentation overlays.
 */
public final class StagePlaybackCoordinator {

	private static final double TIMELINE_EVENT_EPSILON = 1e-4;

	public interface Host {
		BeatBlockContext ctx();

		boolean isExportIsolated();

		CompilePolicy drivingCompilePolicy();

		void setDrivingCompilePolicy(CompilePolicy policy);

		void restoreTimelineMutationSnapshot();

		void clearGlobalVfxPresentation();

		void syncStatefulGlobalVfx(@Nullable CompiledTimelineSnapshot program, double timeSeconds);

		void applyStageEvent(
			TimelineAnimationEvent event,
			@Nullable CompiledStageEvent compiledHint,
			boolean previewOnly,
			double[] referenceBeats,
			double bpm
		);

		void onCompiledGlobalEvent(com.beatblock.timeline.playback.CompiledGlobalEvent event);

		boolean consumePendingWorldReconstruct();

		void resetTimelineAnimationScheduling();
	}

	private final Host host;
	private final PlaybackEngine formalEngine = new PlaybackEngine();
	private final PlaybackEngine previewEngine = new PlaybackEngine();
	private @Nullable CompiledTimelineSnapshot formalProgram;
	private @Nullable CompiledTimelineSnapshot previewProgram;
	private volatile double lastStageEventTime;
	private volatile int lastStageEventsGeneration = -1;
	private volatile long lastPreviewDocumentGeneration = -1L;

	public StagePlaybackCoordinator(Host host) {
		this.host = host;
	}

	public PlaybackEngine formalEngine() {
		return formalEngine;
	}

	public @Nullable CompiledTimelineSnapshot formalProgram() {
		return formalProgram;
	}

	public void setFormalProgram(@Nullable CompiledTimelineSnapshot snapshot) {
		formalProgram = snapshot;
	}

	public double lastStageEventTime() {
		return lastStageEventTime;
	}

	public void setLastStageEventTime(double timeSeconds) {
		lastStageEventTime = timeSeconds;
	}

	public int scheduledStageCount() {
		return formalEngine.scheduledStageCount();
	}

	/** Compile live Timeline into formal program and load (start driving). */
	public void compileAndLoadFormal(CompilePolicy policy) {
		CompilePolicy resolved = policy != null ? policy : CompilePolicy.STRICT;
		host.setDrivingCompilePolicy(resolved);
		formalProgram = TimelineCompiler.compile(
			host.ctx().timeline(),
			host.ctx().blockAnimationEngine(),
			host.ctx().buildLayerManager(),
			resolved
		).snapshot();
		formalEngine.load(formalProgram);
	}

	public CompilePolicy consumeStartDrivingPolicy() {
		CompilePolicy policy = PerformanceCheckController.consumeNextCompilePolicy();
		return policy != null ? policy : CompilePolicy.STRICT;
	}

	public void resetFormal() {
		formalEngine.reset();
		formalProgram = null;
	}

	/**
	 * Hot-reload formal program while driving. Returns false if compile failed (previous kept).
	 */
	public boolean hotReloadFormal(double currentTimeSeconds) {
		double currentTime = currentTimeSeconds;
		if (!Double.isFinite(currentTime) || currentTime < 0) {
			currentTime = Math.max(0, lastStageEventTime);
		}
		CompiledTimelineSnapshot next;
		try {
			CompileResult result = TimelineCompiler.compile(
				host.ctx().timeline(),
				host.ctx().blockAnimationEngine(),
				host.ctx().buildLayerManager(),
				host.drivingCompilePolicy()
			);
			next = result.snapshot();
		} catch (TimelineCompilationException error) {
			BeatBlock.LOGGER.warn(
				"Timeline hot-reload compile failed; keeping previous compiled playback", error);
			return false;
		}
		formalProgram = next;
		formalEngine.load(formalProgram);
		reconstructFormalAt(currentTime);
		return true;
	}

	public void reconstructFormalAt(double currentTime) {
		var engine = host.ctx().blockAnimationEngine();
		host.restoreTimelineMutationSnapshot();
		if (engine != null) {
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(
					PlaybackExecutionMode.REALTIME_MUTATION_BUDGET_PER_TICK);
			}
		}
		if (formalProgram == null) {
			lastStageEventTime = Math.max(0, currentTime);
			host.clearGlobalVfxPresentation();
			return;
		}
		double time = Math.max(0, currentTime);
		host.clearGlobalVfxPresentation();
		double[] referenceBeats = formalProgram.referenceBeatTimesSeconds();
		double bpm = formalProgram.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> host.applyStageEvent(event, compiled, false, referenceBeats, bpm);
		formalEngine.seek(time, SeekMode.RECONSTRUCT_STATE, stageHandler, host::onCompiledGlobalEvent);
		host.syncStatefulGlobalVfx(formalProgram, time);
		lastStageEventTime = time;
	}

	public void sync(double currentTime, boolean previewOnly) {
		var timeline = host.ctx().timeline();
		var engine = host.ctx().blockAnimationEngine();
		if (timeline == null || engine == null) return;

		if (previewOnly) {
			syncPreview(currentTime, timeline, engine);
			return;
		}
		syncFormal(currentTime, timeline, engine);
	}

	public void syncPreviewForTests(double timeSeconds) {
		var timeline = host.ctx().timeline();
		var engine = host.ctx().blockAnimationEngine();
		if (timeline == null || engine == null) {
			return;
		}
		syncPreview(timeSeconds, timeline, engine);
	}

	public void advanceFormalForTests(double timeSeconds) {
		if (formalProgram == null) {
			return;
		}
		formalEngine.advance(timeSeconds, (compiled, event) -> {}, host::onCompiledGlobalEvent);
		lastStageEventTime = timeSeconds;
	}

	public @Nullable CompiledStageEvent findCompiledStage(TimelineAnimationEvent event) {
		if (event == null) return null;
		String id = event.getEventId();
		if (id != null && !id.isBlank()) {
			var fromEngine = formalEngine.findCompiledStage(id);
			if (fromEngine != null) {
				return fromEngine;
			}
		}
		if (formalProgram == null) return null;
		for (var compiled : formalProgram.compiledStageEvents()) {
			if (compiled.event() == event || compiled.event().getEventId().equals(event.getEventId())) {
				return compiled;
			}
		}
		return null;
	}

	/**
	 * Soft reset of engine cursors / preview program. Caller restores world mutations first if needed.
	 */
	public void resetSchedulingState() {
		lastStageEventsGeneration = -1;
		lastStageEventTime = 0.0;
		if (formalEngine.isLoaded()) {
			formalEngine.load(formalProgram);
		}
		previewProgram = null;
		lastPreviewDocumentGeneration = -1L;
		previewEngine.reset();
	}

	private void syncFormal(double currentTime, Timeline timeline, com.beatblock.engine.BlockAnimationEngine engine) {
		boolean loopOrSeekReconstruct = host.consumePendingWorldReconstruct();
		boolean rewinding = loopOrSeekReconstruct
			|| currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime;
		if (rewinding) {
			host.restoreTimelineMutationSnapshot();
			engine.clear();
			var buildSequencer = engine.getBuildSequencer();
			if (buildSequencer != null) {
				buildSequencer.setMutationBudgetPerTick(
					PlaybackExecutionMode.REALTIME_MUTATION_BUDGET_PER_TICK);
			}
			host.clearGlobalVfxPresentation();
		}
		CompiledTimelineSnapshot playback = formalProgram;
		if (playback == null) {
			playback = TimelineCompiler.compile(timeline, engine, host.ctx().buildLayerManager());
			formalProgram = playback;
			formalEngine.load(playback);
		}
		double[] referenceBeats = playback.referenceBeatTimesSeconds();
		double bpm = playback.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> host.applyStageEvent(event, compiled, false, referenceBeats, bpm);
		if (host.isExportIsolated() || rewinding) {
			formalEngine.seek(
				currentTime,
				SeekMode.RECONSTRUCT_STATE,
				stageHandler,
				host::onCompiledGlobalEvent
			);
		} else {
			formalEngine.advance(currentTime, stageHandler, host::onCompiledGlobalEvent);
		}
		host.syncStatefulGlobalVfx(formalProgram, currentTime);
		lastStageEventTime = currentTime;
	}

	private void syncPreview(
		double currentTime,
		Timeline timeline,
		com.beatblock.engine.BlockAnimationEngine engine
	) {
		long documentGeneration = timeline.getDocumentGeneration();
		int stageGeneration = timeline.getStageEventsGeneration();
		boolean rewinding = currentTime + TIMELINE_EVENT_EPSILON < lastStageEventTime;
		if (rewinding
			|| documentGeneration != lastPreviewDocumentGeneration
			|| stageGeneration != lastStageEventsGeneration) {
			host.resetTimelineAnimationScheduling();
			previewProgram = null;
			lastPreviewDocumentGeneration = documentGeneration;
		}

		CompiledTimelineSnapshot playback = previewProgram;
		if (playback == null) {
			playback = TimelineCompiler.compile(timeline, engine, host.ctx().buildLayerManager());
			previewProgram = playback;
			previewEngine.load(playback);
		}

		engine.clear();
		var buildSequencer = engine.getBuildSequencer();
		if (buildSequencer != null) {
			buildSequencer.setExecutionPolicy(com.beatblock.engine.BuildExecutionPolicy.PREVIEW);
		}

		double[] referenceBeats = playback.referenceBeatTimesSeconds();
		double bpm = playback.bpm();
		PlaybackEngine.StageEventHandler stageHandler =
			(compiled, event) -> host.applyStageEvent(event, compiled, true, referenceBeats, bpm);
		previewEngine.seek(
			currentTime,
			SeekMode.RECONSTRUCT_STATE,
			stageHandler,
			ignored -> {}
		);

		lastStageEventTime = currentTime;
		lastStageEventsGeneration = stageGeneration;
	}
}
