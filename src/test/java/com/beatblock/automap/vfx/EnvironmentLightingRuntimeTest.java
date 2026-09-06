package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentLightingRuntimeTest {

	@AfterEach
	void tearDown() {
		EnvironmentLightingRuntime.resetForTests();
	}

	@Test
	void applyStoresStickyState() {
		var warm = new GlobalEventPayload.EnvironmentLighting("Warm", 0.8, 1f, 0.5f, 0.2f, 1.5);
		assertTrue(EnvironmentLightingRuntime.apply(warm));
		assertEquals(0.8, EnvironmentLightingRuntime.current().intensity(), 1e-9);
		assertEquals(0.5f, EnvironmentLightingRuntime.current().g(), 1e-6);
		assertEquals(1.5, EnvironmentLightingRuntime.current().transitionSeconds(), 1e-9);
	}

	@Test
	void seekAt15RestoresLightingFromActiveState() {
		CompiledGlobalEvent warm = new CompiledGlobalEvent(
			"warm", 10.0, new GlobalEventPayload.EnvironmentLighting("Warm", 0.7, 1f, 0.4f, 0.1f, 1.0));
		ActiveGlobalEffectState at15 = ActiveGlobalEffectState.resolve(List.of(warm), 15.0);
		assertSame(warm, at15.environmentLighting());
		EnvironmentLightingRuntime.sync((GlobalEventPayload.EnvironmentLighting) at15.environmentLighting().payload());
		assertEquals(0.7, EnvironmentLightingRuntime.current().intensity(), 1e-9);
	}

	@Test
	void laterLightingOverridesEarlier() {
		CompiledGlobalEvent warm = new CompiledGlobalEvent(
			"warm", 10.0, new GlobalEventPayload.EnvironmentLighting("Warm", 0.7, 1f, 0.4f, 0.1f, 0));
		CompiledGlobalEvent blue = new CompiledGlobalEvent(
			"blue", 20.0, new GlobalEventPayload.EnvironmentLighting("Blue", 0.5, 0.2f, 0.3f, 1f, 0));
		ActiveGlobalEffectState at25 = ActiveGlobalEffectState.resolve(List.of(warm, blue), 25.0);
		assertSame(blue, at25.environmentLighting());
		EnvironmentLightingRuntime.sync((GlobalEventPayload.EnvironmentLighting) at25.environmentLighting().payload());
		assertEquals(0.5, EnvironmentLightingRuntime.current().intensity(), 1e-9);
		assertEquals(1f, EnvironmentLightingRuntime.current().b(), 1e-6);
	}

	@Test
	void clearRestoresNeutral() {
		EnvironmentLightingRuntime.apply(
			new GlobalEventPayload.EnvironmentLighting("Warm", 0.4, 1f, 0f, 0f, 0));
		EnvironmentLightingRuntime.clear();
		assertTrue(EnvironmentLightingRuntime.current().isNeutral());
	}

	@Test
	void environmentResetClearsStickyPresentationInActiveState() {
		List<CompiledGlobalEvent> events = List.of(
			new CompiledGlobalEvent("warm", 5.0,
				new GlobalEventPayload.EnvironmentLighting("Warm", 0.6, 1f, 0.5f, 0.2f, 0)),
			new CompiledGlobalEvent("rain", 6.0,
				new GlobalEventPayload.LocalVisualWeather("Rain", "rain", 1)),
			new CompiledGlobalEvent("tint", 7.0,
				new GlobalEventPayload.ScreenTint("Tint", 0.5, 0, 0, 1, 0)),
			new CompiledGlobalEvent("reset", 12.0,
				new GlobalEventPayload.EnvironmentReset("Reset"))
		);
		ActiveGlobalEffectState before = ActiveGlobalEffectState.resolve(events, 10.0);
		assertSame(events.get(0), before.environmentLighting());
		assertSame(events.get(1), before.weather());
		assertSame(events.get(2), before.screenTint());

		ActiveGlobalEffectState after = ActiveGlobalEffectState.resolve(events, 15.0);
		assertNull(after.environmentLighting());
		assertNull(after.weather());
		assertNull(after.screenTint());
		assertNull(after.audioMix());
	}

	@Test
	void codecPrefersTransitionSecondsAndAcceptsLegacyDuration() {
		GlobalEventPayload.EnvironmentLighting fromTransition = assertEnv(
			GlobalEventPayloadCodec.decode(Map.of(
				"type", "ENVIRONMENT_LIGHTING",
				"intensity", 0.5,
				"transitionSeconds", 1.25)));
		assertEquals(1.25, fromTransition.transitionSeconds(), 1e-9);

		GlobalEventPayload.EnvironmentLighting fromLegacy = assertEnv(
			GlobalEventPayloadCodec.decode(Map.of(
				"type", "LIGHTING",
				"intensity", 0.5,
				"durationSeconds", 2.0)));
		assertEquals(2.0, fromLegacy.transitionSeconds(), 1e-9);

		Map<String, Object> encoded = GlobalEventPayloadCodec.encode(fromTransition);
		assertEquals(1.25, ((Number) encoded.get("transitionSeconds")).doubleValue(), 1e-9);
		assertFalse(encoded.containsKey("durationSeconds"));
	}

	private static GlobalEventPayload.EnvironmentLighting assertEnv(GlobalEventPayload payload) {
		assertTrue(payload instanceof GlobalEventPayload.EnvironmentLighting);
		return (GlobalEventPayload.EnvironmentLighting) payload;
	}
}
