package com.beatblock.timeline.playback;

import com.beatblock.automap.vfx.ActiveGlobalEffectState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic logical state left by state-bearing events at a timeline position. */
public record PlaybackStateDigest(
	Map<String, String> stageStates,
	Map<String, String> globalStates
) {
	public PlaybackStateDigest {
		stageStates = Map.copyOf(stageStates != null ? stageStates : Map.of());
		globalStates = Map.copyOf(globalStates != null ? globalStates : Map.of());
	}

	public static PlaybackStateDigest playTo(CompiledTimelineSnapshot program, double timeSeconds) {
		PlaybackEngine engine = new PlaybackEngine();
		Collector collector = new Collector();
		engine.load(program);
		engine.advance(timeSeconds, collector::stage, ignored -> {});
		return collector.digest(program, timeSeconds);
	}

	public static PlaybackStateDigest reconstructAt(CompiledTimelineSnapshot program, double timeSeconds) {
		PlaybackEngine engine = new PlaybackEngine();
		Collector collector = new Collector();
		engine.load(program);
		engine.seek(timeSeconds, SeekMode.RECONSTRUCT_STATE, collector::stage, ignored -> {});
		return collector.digest(program, timeSeconds);
	}

	private static final class Collector {
		private final Map<String, String> stages = new LinkedHashMap<>();

		private void stage(CompiledStageEvent compiled, com.beatblock.timeline.TimelineAnimationEvent event) {
			if (compiled.semantics() == PlaybackSemantics.TRANSIENT) return;
			String target = event.getTargetObjectId().isBlank() ? "event:" + compiled.stableSequence() : event.getTargetObjectId();
			stages.put(target, event.getActionMode().name() + ":" + event.getAnimationTypeId());
		}

		private PlaybackStateDigest digest(CompiledTimelineSnapshot program, double timeSeconds) {
			return new PlaybackStateDigest(stages, globalStatesAt(program, timeSeconds));
		}
	}

	/**
	 * Global logical state is sample-at-time via {@link ActiveGlobalEffectState}
	 * (continuous / mid-envelope only) — not "every cue that ever fired".
	 */
	private static Map<String, String> globalStatesAt(CompiledTimelineSnapshot program, double timeSeconds) {
		if (program == null) {
			return Map.of();
		}
		ActiveGlobalEffectState active = ActiveGlobalEffectState.resolve(program.globalEvents(), timeSeconds);
		Map<String, String> globals = new LinkedHashMap<>();
		putGlobal(globals, active.environmentLighting());
		putGlobal(globals, active.weather());
		putGlobal(globals, active.audioMix());
		putGlobal(globals, active.screenTint());
		putGlobal(globals, active.screenFlash());
		return Map.copyOf(globals);
	}

	private static void putGlobal(Map<String, String> globals, CompiledGlobalEvent event) {
		if (event == null) {
			return;
		}
		globals.put(event.typeName(), event.name() + ":" + canonicalPayload(event.payload()));
	}

	private static String canonicalPayload(GlobalEventPayload payload) {
		CompiledTimelineSnapshot shell = new CompiledTimelineSnapshot(
			java.util.List.of(), java.util.List.of(), new CompiledCameraTrack(java.util.List.of()),
			java.util.List.of(), java.util.List.of(),
			java.util.List.of(new CompiledGlobalEvent("digest", 0, payload)),
			CompiledAudioReference.empty(), new double[0], 120, 0, true, 0, null);
		return CompiledProgramFingerprint.compute(shell);
	}
}
