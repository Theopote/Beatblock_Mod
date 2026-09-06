package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalEffectPayloadUiTest {

	@Test
	void scopeLabelFollowsPayloadType() {
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.environment"),
			GlobalEffectPayloadUi.scopeLabel(new GlobalEventPayload.EnvironmentLighting("", 1, 1, 1, 1, 1)));
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.screen"),
			GlobalEffectPayloadUi.scopeLabel(new GlobalEventPayload.ScreenTint("", 0.5, 1, 1, 1, 1)));
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.world_position"),
			GlobalEffectPayloadUi.scopeLabel(new GlobalEventPayload.ParticleBurst("", "poof", 0, 64, 0, 1, 0.5, 0.04)));
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.audio"),
			GlobalEffectPayloadUi.scopeLabel(new GlobalEventPayload.AudioMix("", "master", 1, 0.5)));
	}

	@Test
	void screenTintScopeIsScreenNotEnvironment() {
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.screen"),
			GlobalEffectPayloadUi.scopeLabel(GlobalEffectKind.SCREEN_TINT));
		assertEquals(BBTexts.get("beatblock.vfx_creator.scope.environment"),
			GlobalEffectPayloadUi.scopeLabel(GlobalEffectKind.ENVIRONMENT_LIGHTING));
	}
}
