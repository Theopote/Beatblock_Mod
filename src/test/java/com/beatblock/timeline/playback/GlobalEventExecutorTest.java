package com.beatblock.timeline.playback;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalEventExecutorTest {

	@Test
	void dispatchesEveryTypedPayloadAndReportsGenericAsUnsupported() {
		AtomicInteger calls = new AtomicInteger();
		GlobalEventExecutor executor = new GlobalEventExecutor(new GlobalEventExecutor.Backend() {
			@Override public boolean applyEnvironmentLighting(GlobalEventPayload.EnvironmentLighting payload) { calls.incrementAndGet(); return true; }
			@Override public boolean applyScreenTint(GlobalEventPayload.ScreenTint payload) { calls.incrementAndGet(); return true; }
			@Override public boolean applyLocalVisualWeather(GlobalEventPayload.LocalVisualWeather payload) { calls.incrementAndGet(); return true; }
			@Override public boolean emitParticleBurst(GlobalEventPayload.ParticleBurst payload) { calls.incrementAndGet(); return true; }
			@Override public boolean applyScreenFlash(GlobalEventPayload.ScreenFlash payload) { calls.incrementAndGet(); return true; }
			@Override public boolean applyAudioMix(GlobalEventPayload.AudioMix payload) { calls.incrementAndGet(); return true; }
		});

		assertTrue(executor.execute(event("light", new GlobalEventPayload.EnvironmentLighting("", 1, 1, 1, 1, 0))).executed());
		assertTrue(executor.execute(event("tint", new GlobalEventPayload.ScreenTint("", 0.5, 1, 1, 1, 0))).executed());
		assertTrue(executor.execute(event("weather", new GlobalEventPayload.LocalVisualWeather("", "rain", 0))).executed());
		assertTrue(executor.execute(event("particle",
			new GlobalEventPayload.ParticleBurst("", "poof", 0, 0, 0, 1, 0.5, 0.04))).executed());
		assertTrue(executor.execute(event("flash", new GlobalEventPayload.ScreenFlash("", 1, 1, 1, 0.1))).executed());
		assertTrue(executor.execute(event("audio", new GlobalEventPayload.AudioMix("", "master", 1, 0))).executed());
		assertFalse(executor.execute(event("generic", new GlobalEventPayload.Generic("CUSTOM", "", Map.of()))).executed());
		assertEquals(6, calls.get());
	}

	@Test
	void globalSemanticsMatchSeekBehavior() {
		assertEquals(com.beatblock.automap.vfx.GlobalEffectSemantics.CONTINUOUS_STATE,
			event("light", new GlobalEventPayload.EnvironmentLighting("", 1, 1, 1, 1, 0)).effectSemantics());
		assertEquals(com.beatblock.automap.vfx.GlobalEffectSemantics.FINITE_ENVELOPE,
			event("tint", new GlobalEventPayload.ScreenTint("", 0.5, 1, 1, 1, 0)).effectSemantics());
		assertEquals(com.beatblock.automap.vfx.GlobalEffectSemantics.FINITE_ENVELOPE,
			event("flash", new GlobalEventPayload.ScreenFlash("", 1, 1, 1, 0.1)).effectSemantics());
		assertEquals(com.beatblock.automap.vfx.GlobalEffectSemantics.IMPULSE,
			event("particle",
				new GlobalEventPayload.ParticleBurst("", "poof", 0, 0, 0, 1, 0.5, 0.04)).effectSemantics());
		assertEquals(PlaybackSemantics.STATEFUL,
			event("flash", new GlobalEventPayload.ScreenFlash("", 1, 1, 1, 0.1)).semantics());
		assertEquals(PlaybackSemantics.TRANSIENT,
			event("particle",
				new GlobalEventPayload.ParticleBurst("", "poof", 0, 0, 0, 1, 0.5, 0.04)).semantics());
	}

	private static CompiledGlobalEvent event(String id, GlobalEventPayload payload) {
		return new CompiledGlobalEvent(id, 1.0, payload);
	}
}