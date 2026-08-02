package com.beatblock.client.render;

import com.beatblock.timeline.playback.GlobalEventPayload;
import imgui.ImGui;

/** Full-screen editor overlay for compiled screen-tint and screen-flash cues. */
public final class GlobalVisualEffectOverlay {
	private static volatile ColorEffect screenTint;
	private static volatile ColorEffect flash;

	private GlobalVisualEffectOverlay() {}

	public static boolean applyScreenTint(GlobalEventPayload.ScreenTint payload) {
		if (payload == null) return false;
		double intensity = clamp(payload.intensity(), 0.0, 1.0);
		screenTint = intensity > 0
			? new ColorEffect(payload.r(), payload.g(), payload.b(), intensity * 0.35, Long.MAX_VALUE, false)
			: null;
		return true;
	}

	public static boolean applyScreenFlash(GlobalEventPayload.ScreenFlash payload) {
		if (payload == null) return false;
		double duration = Math.max(0.01, payload.durationSeconds());
		long now = System.nanoTime();
		long end = now + (long) (duration * 1_000_000_000L);
		flash = new ColorEffect(payload.r(), payload.g(), payload.b(), 0.85, end, true);
		return true;
	}

	public static void clear() {
		screenTint = null;
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
		if (currentFlash != null && now >= currentFlash.endNanos()) {
			flash = null;
		} else {
			draw(currentFlash, now, width, height);
		}
	}

	private static void draw(ColorEffect effect, long now, float width, float height) {
		if (effect == null) return;
		double alpha = effect.alpha();
		if (effect.fade()) {
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

	private record ColorEffect(float r, float g, float b, double alpha,
		long startNanos, long endNanos, boolean fade) {
		private ColorEffect(float r, float g, float b, double alpha, long endNanos, boolean fade) {
			this(r, g, b, alpha, System.nanoTime(), endNanos, fade);
		}
	}
}