package com.beatblock.timeline.playback;

import java.util.Objects;

/** Executes compiled global events through explicit runtime capabilities. */
public final class GlobalEventExecutor {

	public interface Backend {
		boolean applyLighting(GlobalEventPayload.Lighting payload);
		boolean applyWeather(GlobalEventPayload.Weather payload);
		boolean emitParticleBurst(GlobalEventPayload.ParticleBurst payload);
		boolean applyScreenFlash(GlobalEventPayload.ScreenFlash payload);
		boolean applyAudioMix(GlobalEventPayload.AudioMix payload);
		default void unsupported(GlobalEventPayload.Generic payload) {}
	}

	public record ExecutionResult(boolean executed, String typeName, String detail) {}

	private final Backend backend;

	public GlobalEventExecutor(Backend backend) {
		this.backend = Objects.requireNonNull(backend, "backend");
	}

	public ExecutionResult execute(CompiledGlobalEvent event) {
		Objects.requireNonNull(event, "event");
		GlobalEventPayload payload = event.payload();
		boolean executed;
		if (payload instanceof GlobalEventPayload.Lighting value) {
			executed = backend.applyLighting(value);
		} else if (payload instanceof GlobalEventPayload.Weather value) {
			executed = backend.applyWeather(value);
		} else if (payload instanceof GlobalEventPayload.ParticleBurst value) {
			executed = backend.emitParticleBurst(value);
		} else if (payload instanceof GlobalEventPayload.ScreenFlash value) {
			executed = backend.applyScreenFlash(value);
		} else if (payload instanceof GlobalEventPayload.AudioMix value) {
			executed = backend.applyAudioMix(value);
		} else if (payload instanceof GlobalEventPayload.Generic value) {
			backend.unsupported(value);
			executed = false;
		} else {
			executed = false;
		}
		return new ExecutionResult(executed, event.typeName(), executed ? "executed" : "unsupported");
	}
}