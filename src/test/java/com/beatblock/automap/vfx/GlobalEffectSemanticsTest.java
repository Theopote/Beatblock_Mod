package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.PlaybackSemantics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalEffectSemanticsTest {

	@Test
	void classifiesThreeTiers() {
		assertEquals(GlobalEffectSemantics.CONTINUOUS_STATE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.EnvironmentLighting("", 1, 1, 1, 1, 2)));
		assertEquals(GlobalEffectSemantics.CONTINUOUS_STATE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.LocalVisualWeather("", "rain", 1)));
		assertEquals(GlobalEffectSemantics.CONTINUOUS_STATE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.AudioMix("", "master", 1, 0.5)));
		assertEquals(GlobalEffectSemantics.FINITE_ENVELOPE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.ScreenTint("", 0.5, 1, 1, 1, 2)));
		assertEquals(GlobalEffectSemantics.FINITE_ENVELOPE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.ScreenFlash("", 1, 1, 1, 0.2)));
		assertEquals(GlobalEffectSemantics.IMPULSE,
			GlobalEffectSemantics.fromPayload(new GlobalEventPayload.ParticleBurst("", "poof", 0, 64, 0, 8, 0.5, 0.04)));
	}

	@Test
	void reconstructFlagsAndPlaybackMapping() {
		assertTrue(GlobalEffectSemantics.CONTINUOUS_STATE.reconstructOnSeek());
		assertTrue(GlobalEffectSemantics.FINITE_ENVELOPE.reconstructOnSeek());
		assertFalse(GlobalEffectSemantics.IMPULSE.reconstructOnSeek());
		assertEquals(PlaybackSemantics.STATEFUL, GlobalEffectSemantics.FINITE_ENVELOPE.toPlaybackSemantics());
		assertEquals(PlaybackSemantics.TRANSIENT, GlobalEffectSemantics.IMPULSE.toPlaybackSemantics());
	}
}
