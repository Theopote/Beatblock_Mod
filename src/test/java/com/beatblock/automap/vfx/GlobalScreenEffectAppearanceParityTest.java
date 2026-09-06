package com.beatblock.automap.vfx;

import com.beatblock.client.export.GlobalVisualEffectFrameCompositor;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance: runtime overlay and export compositor share {@link GlobalScreenEffectAppearance}
 * from the same typed {@link GlobalEventPayload} — no parallel Timeline-parameter reinterpretation.
 */
class GlobalScreenEffectAppearanceParityTest {

	@AfterEach
	void clearOverlay() {
		GlobalVisualEffectOverlay.clear();
	}

	@Test
	void tintAlphaMatchesSharedAppearanceConstant() {
		var tint = new GlobalEventPayload.ScreenTint("t", 0.4, 0.1f, 0.2f, 1f, 5);
		var color = GlobalScreenEffectAppearance.screenTint(tint).orElseThrow();
		assertEquals(0.4 * GlobalScreenEffectAppearance.TINT_ALPHA_SCALE, color.alpha(), 1e-9);
		assertEquals(0.1f, color.r());
		assertEquals(0.2f, color.g());
		assertEquals(1f, color.b());
	}

	@Test
	void flashMidEnvelopeMatchesSharedAppearance() {
		var flash = new GlobalEventPayload.ScreenFlash("f", 1f, 0f, 0f, 2);
		var mid = GlobalScreenEffectAppearance.screenFlash(flash, 10, 11).orElseThrow();
		assertEquals(GlobalScreenEffectAppearance.FLASH_PEAK_ALPHA * 0.5, mid.alpha(), 1e-9);
		assertTrue(GlobalScreenEffectAppearance.screenFlash(flash, 10, 12).isEmpty());
	}

	@Test
	void exportCompositorUsesSameTintAlphaAsAppearanceHelper() {
		var tint = new GlobalEventPayload.ScreenTint("", 1, 1, 0, 0, 0);
		double alpha = GlobalScreenEffectAppearance.screenTint(tint).orElseThrow().alpha();
		int expectedR = (int) Math.round(0 * (1 - alpha) + 255 * alpha);

		byte[] frame = opaqueBlack(1, 1);
		GlobalVisualEffectFrameCompositor.composite(
			frame, 1, 1,
			List.of(new CompiledGlobalEvent("tint", 0, tint)),
			12);
		assertEquals(expectedR, Byte.toUnsignedInt(frame[0]));
	}

	@Test
	void exportCompositorUsesSameFlashEnvelopeAsAppearanceHelper() {
		var flash = new GlobalEventPayload.ScreenFlash("", 1, 0, 0, 2);
		var event = new CompiledGlobalEvent("flash", 10, flash);
		double alpha = GlobalScreenEffectAppearance.screenFlash(flash, 10, 11).orElseThrow().alpha();
		int expectedR = (int) Math.round(0 * (1 - alpha) + 255 * alpha);

		byte[] middle = opaqueBlack(1, 1);
		GlobalVisualEffectFrameCompositor.composite(middle, 1, 1, List.of(event), 11);
		assertEquals(expectedR, Byte.toUnsignedInt(middle[0]));
	}

	@Test
	void runtimeSyncTintUsesSameAppearanceAsExport() {
		var tint = new GlobalEventPayload.ScreenTint("chorus", 0.8, 0.2f, 0.4f, 0.6f, 3);
		var appearance = GlobalScreenEffectAppearance.screenTint(tint).orElseThrow();
		double alpha = appearance.alpha();
		int overlayR = (int) Math.round(appearance.r() * 255);
		int overlayG = (int) Math.round(appearance.g() * 255);
		int overlayB = (int) Math.round(appearance.b() * 255);

		GlobalVisualEffectOverlay.syncScreenTint(tint);

		byte[] frame = opaqueBlack(1, 1);
		GlobalVisualEffectFrameCompositor.composite(
			frame, 1, 1,
			List.of(new CompiledGlobalEvent("chorus", 0, tint)),
			1);
		assertEquals((int) Math.round(overlayR * alpha), Byte.toUnsignedInt(frame[0]));
		assertEquals((int) Math.round(overlayG * alpha), Byte.toUnsignedInt(frame[1]));
		assertEquals((int) Math.round(overlayB * alpha), Byte.toUnsignedInt(frame[2]));
	}

	@Test
	void activeStateResolveFeedsExportWithoutRawParams() {
		var tint = new GlobalEventPayload.ScreenTint("Chorus Tint", 0.4, 0.1f, 0.2f, 1f, 5);
		var flash = new GlobalEventPayload.ScreenFlash("Pre-Drop Flash", 1f, 1f, 1f, 1);
		List<CompiledGlobalEvent> events = List.of(
			new CompiledGlobalEvent("Chorus Tint", 8, tint),
			new CompiledGlobalEvent("Pre-Drop Flash", 9.5, flash)
		);

		ActiveGlobalEffectState at95 = ActiveGlobalEffectState.resolve(events, 9.5);
		assertEquals(tint, java.util.Objects.requireNonNull(at95.screenTint()).payload());
		assertEquals(flash, java.util.Objects.requireNonNull(at95.screenFlash()).payload());

		var tintColor = GlobalScreenEffectAppearance.screenTint(
			(GlobalEventPayload.ScreenTint) at95.screenTint().payload()).orElseThrow();
		var flashColor = GlobalScreenEffectAppearance.screenFlash(
			(GlobalEventPayload.ScreenFlash) at95.screenFlash().payload(),
			at95.screenFlash().timeSeconds(),
			9.5).orElseThrow();

		assertEquals(0.4 * GlobalScreenEffectAppearance.TINT_ALPHA_SCALE, tintColor.alpha(), 1e-9);
		assertEquals(GlobalScreenEffectAppearance.FLASH_PEAK_ALPHA, flashColor.alpha(), 1e-9);
	}

	private static byte[] opaqueBlack(int width, int height) {
		byte[] frame = new byte[width * height * 4];
		for (int i = 3; i < frame.length; i += 4) {
			frame[i] = (byte) 255;
		}
		return frame;
	}
}
