package com.beatblock.timeline.playback;

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
		engine.advance(timeSeconds, collector::stage, collector::global);
		return collector.digest();
	}

	public static PlaybackStateDigest reconstructAt(CompiledTimelineSnapshot program, double timeSeconds) {
		PlaybackEngine engine = new PlaybackEngine();
		Collector collector = new Collector();
		engine.load(program);
		engine.seek(timeSeconds, SeekMode.RECONSTRUCT_STATE, collector::stage, collector::global);
		return collector.digest();
	}

	private static final class Collector {
		private final Map<String, String> stages = new LinkedHashMap<>();
		private final Map<String, String> globals = new LinkedHashMap<>();

		private void stage(CompiledStageEvent compiled, com.beatblock.timeline.TimelineAnimationEvent event) {
			if (compiled.semantics() == PlaybackSemantics.TRANSIENT) return;
			String target = event.getTargetObjectId().isBlank() ? "event:" + compiled.stableSequence() : event.getTargetObjectId();
			stages.put(target, event.getActionMode().name() + ":" + event.getAnimationTypeId());
		}

		private void global(CompiledGlobalEvent event) {
			if (event.semantics() == PlaybackSemantics.TRANSIENT) return;
			globals.put(event.typeName(), event.name() + ":" + canonicalPayload(event.payload()));
		}

		private PlaybackStateDigest digest() { return new PlaybackStateDigest(stages, globals); }
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
