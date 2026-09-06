package com.beatblock.client.render;

import com.beatblock.automap.vfx.GlobalScreenEffectAppearance;
import com.beatblock.timeline.playback.GlobalEventPayload;
import imgui.ImGui;
import org.jspecify.annotations.Nullable;

/** Full-screen editor overlay — appearance from typed payload via {@link GlobalScreenEffectAppearance}. */
public final class GlobalVisualEffectOverlay {
	private static volatile ColorEffect screenTint;
	private static volatile ColorEffect flash;

	private GlobalVisualEffectOverlay() {}

	public static boolean applyScreenTint(GlobalEventPayload.ScreenTint payload) {
		var color = GlobalScreenEffectAppearance.screenTint(payload);
		screenTint = color.map(c -> ColorEffect.solid(c.r(), c.g(), c.b(), c.alpha())).orElse(null);
		return color.isPresent();
	}

	/** Timeline-driven sync: null clears tint (seek reconstruct / duration expiry). */
	public static void syncScreenTint(GlobalEventPayload.@Nullable ScreenTint payload) {
		if (payload == null) {
			screenTint = null;
			return;
		}
		applyScreenTint(payload);
	}

	/** Live forward playback: wall-clock fade envelope starting at peak. */
	public static boolean applyScreenFlash(GlobalEventPayload.ScreenFlash payload) {
		if (payload == null) {
			return false;
		}
		var peak = GlobalScreenEffectAppearance.screenFlashPeak(payload);
		if (peak.isEmpty()) {
			return false;
		}
		double duration = Math.max(0.01, payload.durationSeconds());
		long now = System.nanoTime();
		long end = now + (long) (duration * 1_000_000_000L);
		var color = peak.get();
		flash = ColorEffect.wallClockFade(color.r(), color.g(), color.b(), color.alpha(), now, end);
		return true;
	}

	/**
	 * Timeline-driven flash for seek/scrub/export-aligned preview at {@code timelineTimeSeconds}.
	 * Null clears.
	 */
	public static void syncScreenFlash(
		GlobalEventPayload.@Nullable ScreenFlash payload,
		double startSeconds,
		double timelineTimeSeconds
	) {
		if (payload == null) {
			flash = null;
			return;
		}
		var color = GlobalScreenEffectAppearance.screenFlash(payload, startSeconds, timelineTimeSeconds);
		flash = color.map(c -> ColorEffect.solid(c.r(), c.g(), c.b(), c.alpha())).orElse(null);
	}

	public static void clear() {
		screenTint = null;
		flash = null;
	}

	public static void clearScreenTint() {
		screenTint = null;
	}

	public static void clearScreenFlash() {
		flash = null;
	}

	public static void render() {
		var io = ImGui.getIO();
		float width = io.getDisplaySizeX();
		float height = io.getDisplaySizeY();
		if (width <= 0 || height <= 0) return;
		long now = System.nanoTime();
		draw(screenTint, now, width, height);
		ColorEffect currentFlash = flash;
		if (currentFlash != null && currentFlash.wallClockFade() && now >= currentFlash.endNanos()) {
			flash = null;
		} else {
			draw(currentFlash, now, width, height);
		}
	}

	private static void draw(ColorEffect effect, long now, float width, float height) {
		if (effect == null) return;
		double alpha = effect.alpha();
		if (effect.wallClockFade()) {
			long remaining = Math.max(0, effect.endNanos() - now);
			double duration = Math.max(1, effect.endNanos() - effect.startNanos());
			alpha *= remaining / duration;
		}
		if (alpha <= 0) return;
		ImGui.getForegroundDrawList().addRectFilled(0, 0, width, height,
			abgr(effect.r(), effect.g(), effect.b(), alpha));
	}

	private static int abgr(float r, float g, float b, double alpha) {
		int ri = (int) Math.round(clamp(r, 0, 1) * 255);
		int gi = (int) Math.round(clamp(g, 0, 1) * 255);
		int bi = (int) Math.round(clamp(b, 0, 1) * 255);
		int ai = (int) Math.round(clamp(alpha, 0, 1) * 255);
		return ai << 24 | bi << 16 | gi << 8 | ri;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private record ColorEffect(
		float r, float g, float b, double alpha,
		long startNanos, long endNanos, boolean wallClockFade
	) {
		static ColorEffect solid(float r, float g, float b, double alpha) {
			return new ColorEffect(r, g, b, alpha, 0L, Long.MAX_VALUE, false);
		}

		static ColorEffect wallClockFade(float r, float g, float b, double alpha, long start, long end) {
			return new ColorEffect(r, g, b, alpha, start, end, true);
		}
	}
}
