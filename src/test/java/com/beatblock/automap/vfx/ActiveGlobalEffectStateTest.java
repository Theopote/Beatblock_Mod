package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveGlobalEffectStateTest {

	@Test
	void seekIntoTintWindowKeepsActiveTint() {
		CompiledGlobalEvent tint = new CompiledGlobalEvent(
			"tint", 10.0, new GlobalEventPayload.ScreenTint("Warm", 0.7, 1, 0.8f, 0.5f, 10.0));
		CompiledGlobalEvent burst = new CompiledGlobalEvent(
			"burst", 12.0, new GlobalEventPayload.ParticleBurst("Poof", "poof", 0, 64, 0, 4, 0.5, 0.04));

		ActiveGlobalEffectState at15 = ActiveGlobalEffectState.resolve(List.of(tint, burst), 15.0);
		assertSame(tint, at15.screenTint());
		assertNull(at15.screenFlash());
	}

	@Test
	void seekIntoFlashEnvelopeKeepsMidFlash() {
		CompiledGlobalEvent flash = new CompiledGlobalEvent(
			"flash", 10.0, new GlobalEventPayload.ScreenFlash("Pop", 1, 1, 1, 1.0));
		ActiveGlobalEffectState at105 = ActiveGlobalEffectState.resolve(List.of(flash), 10.5);
		assertSame(flash, at105.screenFlash());
		assertEquals(0.5, GlobalEffectActiveWindow.envelopeProgress(10.0, 1.0, 10.5).orElseThrow(), 1e-9);
	}

	@Test
	void seekPastFlashEnvelopeClearsFlash() {
		CompiledGlobalEvent flash = new CompiledGlobalEvent(
			"flash", 10.0, new GlobalEventPayload.ScreenFlash("Pop", 1, 1, 1, 1.0));
		assertNull(ActiveGlobalEffectState.resolve(List.of(flash), 11.0).screenFlash());
	}

	@Test
	void weatherIsStickyLastWriterWins() {
		CompiledGlobalEvent clear = new CompiledGlobalEvent(
			"clear", 1.0, new GlobalEventPayload.LocalVisualWeather("Clear", "clear", 0.5));
		CompiledGlobalEvent rain = new CompiledGlobalEvent(
			"rain", 8.0, new GlobalEventPayload.LocalVisualWeather("Rain", "rain", 1.0));
		assertSame(rain, ActiveGlobalEffectState.resolve(List.of(clear, rain), 12.0).weather());
	}

	@Test
	void impulseNeverActiveForSeek() {
		CompiledGlobalEvent burst = new CompiledGlobalEvent(
			"burst", 10.0, new GlobalEventPayload.ParticleBurst("Poof", "poof", 0, 64, 0, 4, 0.5, 0.04));
		assertFalse(GlobalEffectActiveWindow.isActiveAt(burst, 30.0));
		assertFalse(GlobalEffectActiveWindow.isActiveAt(burst, 10.0));
		assertTrue(GlobalEffectActiveWindow.isActiveAt(
			new CompiledGlobalEvent("flash", 10.0, new GlobalEventPayload.ScreenFlash("", 1, 1, 1, 1.0)),
			10.5));
	}
}
