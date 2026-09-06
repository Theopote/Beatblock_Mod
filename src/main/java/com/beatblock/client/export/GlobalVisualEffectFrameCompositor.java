package com.beatblock.client.export;

import com.beatblock.automap.vfx.ActiveGlobalEffectState;
import com.beatblock.automap.vfx.GlobalScreenEffectAppearance;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Deterministically composites screen-space timeline effects into an exported RGBA frame.
 * Consumes typed {@link GlobalEventPayload} only (via {@link ActiveGlobalEffectState} +
 * {@link GlobalScreenEffectAppearance}) — same path as runtime overlay.
 */
public final class GlobalVisualEffectFrameCompositor {
	private GlobalVisualEffectFrameCompositor() {}

	public static byte[] composite(
		byte[] rgba,
		int width,
		int height,
		List<CompiledGlobalEvent> events,
		double timelineTimeSeconds
	) {
		if (rgba == null) throw new IllegalArgumentException("rgba");
		if (width <= 0 || height <= 0 || rgba.length != width * height * 4) {
			throw new IllegalArgumentException("RGBA frame dimensions do not match buffer length");
		}
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}

		ActiveGlobalEffectState active = ActiveGlobalEffectState.resolve(events, timelineTimeSeconds);
		applyTint(rgba, active.screenTint());
		applyFlash(rgba, active.screenFlash(), timelineTimeSeconds);
		return rgba;
	}

	private static void applyTint(byte[] rgba, @Nullable CompiledGlobalEvent tintEvent) {
		if (tintEvent == null || !(tintEvent.payload() instanceof GlobalEventPayload.ScreenTint tint)) {
			return;
		}
		GlobalScreenEffectAppearance.screenTint(tint).ifPresent(color ->
			blend(rgba, color.r(), color.g(), color.b(), color.alpha()));
	}

	private static void applyFlash(
		byte[] rgba,
		@Nullable CompiledGlobalEvent flashEvent,
		double timelineTimeSeconds
	) {
		if (flashEvent == null || !(flashEvent.payload() instanceof GlobalEventPayload.ScreenFlash flash)) {
			return;
		}
		GlobalScreenEffectAppearance.screenFlash(flash, flashEvent.timeSeconds(), timelineTimeSeconds)
			.ifPresent(color -> blend(rgba, color.r(), color.g(), color.b(), color.alpha()));
	}

	private static void blend(byte[] rgba, float red, float green, float blue, double alpha) {
		double a = Math.max(0.0, Math.min(1.0, alpha));
		if (a <= 0) return;
		int overlayR = (int) Math.round(Math.max(0, Math.min(1, red)) * 255);
		int overlayG = (int) Math.round(Math.max(0, Math.min(1, green)) * 255);
		int overlayB = (int) Math.round(Math.max(0, Math.min(1, blue)) * 255);
		for (int i = 0; i < rgba.length; i += 4) {
			rgba[i] = (byte) blendChannel(rgba[i] & 0xff, overlayR, a);
			rgba[i + 1] = (byte) blendChannel(rgba[i + 1] & 0xff, overlayG, a);
			rgba[i + 2] = (byte) blendChannel(rgba[i + 2] & 0xff, overlayB, a);
		}
	}

	private static int blendChannel(int base, int overlay, double alpha) {
		return (int) Math.round(base * (1.0 - alpha) + overlay * alpha);
	}
}
