package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalEffectKindTest {

	@Test
	void fromPayloadMapsScreenTint() {
		var payload = new GlobalEventPayload.ScreenTint("Tint", 0.5, 1f, 0f, 0f, 1.0);
		assertEquals(GlobalEffectKind.SCREEN_TINT, GlobalEffectKind.fromPayload(payload));
	}

	@Test
	void defaultPayloadMatchesKind() {
		GlobalEventPayload payload = GlobalEffectKind.PARTICLE_BURST.defaultPayload("Burst");
		assertInstanceOf(GlobalEventPayload.ParticleBurst.class, payload);
		assertEquals("Burst", ((GlobalEventPayload.ParticleBurst) payload).name());
	}
}
