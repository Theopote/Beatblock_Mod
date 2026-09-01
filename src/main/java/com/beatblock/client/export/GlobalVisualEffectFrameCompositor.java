package com.beatblock.client.export;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;

import java.util.List;

/** Deterministically composites screen-space timeline effects into an exported RGBA frame. */
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

		ExportVfxState active = ExportVfxState.resolve(events, timelineTimeSeconds);
		if (active.activeTint() != null && active.activeTint().payload() instanceof GlobalEventPayload.ScreenTint tint) {
			blend(rgba, tint.r(), tint.g(), tint.b(), clamp(tint.intensity(), 0, 1) * 0.35);
		}
		if (active.activeFlash() != null && active.activeFlash().payload() instanceof GlobalEventPayload.ScreenFlash flash) {
			double duration = Math.max(0.01, flash.durationSeconds());
			double progress = clamp((timelineTimeSeconds - active.activeFlash().timeSeconds()) / duration, 0, 1);
			blend(rgba, flash.r(), flash.g(), flash.b(), 0.85 * (1.0 - progress));
		}
		return rgba;
	}

	private static void blend(byte[] rgba, float red, float green, float blue, double alpha) {
		double a = clamp(alpha, 0, 1);
		if (a <= 0) return;
		int overlayR = (int) Math.round(clamp(red, 0, 1) * 255);
		int overlayG = (int) Math.round(clamp(green, 0, 1) * 255);
		int overlayB = (int) Math.round(clamp(blue, 0, 1) * 255);
		for (int i = 0; i < rgba.length; i += 4) {
			rgba[i] = (byte) blendChannel(rgba[i] & 0xff, overlayR, a);
			rgba[i + 1] = (byte) blendChannel(rgba[i + 1] & 0xff, overlayG, a);
			rgba[i + 2] = (byte) blendChannel(rgba[i + 2] & 0xff, overlayB, a);
		}
	}

	private static int blendChannel(int base, int overlay, double alpha) {
		return (int) Math.round(base * (1.0 - alpha) + overlay * alpha);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
