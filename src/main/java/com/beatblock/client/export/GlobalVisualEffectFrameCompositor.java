package com.beatblock.client.export;

import com.beatblock.automap.vfx.GlobalScreenEffectAppearance;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Deterministically composites screen-space timeline effects into an exported RGBA frame.
 * Prefer {@link #composite(byte[], int, int, ExportVfxState, double)} from export (FrameSampler authority);
 * the events overload resolves {@link ExportVfxState} then delegates. Appearance via
 * {@link GlobalScreenEffectAppearance} — same path as runtime overlay.
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
		return composite(rgba, width, height, ExportVfxState.resolve(events, timelineTimeSeconds), timelineTimeSeconds);
	}

	/**
	 * 消费 {@link VideoExportFrameSampler} 已解析的 VFX 状态（导出语义权威路径）。
	 * flash 衰减仍依赖 {@code timelineTimeSeconds}。
	 */
	public static byte[] composite(
		byte[] rgba,
		int width,
		int height,
		ExportVfxState vfxState,
		double timelineTimeSeconds
	) {
		if (rgba == null) throw new IllegalArgumentException("rgba");
		if (width <= 0 || height <= 0 || rgba.length != width * height * 4) {
			throw new IllegalArgumentException("RGBA frame dimensions do not match buffer length");
		}
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}
		ExportVfxState vfx = vfxState != null ? vfxState : new ExportVfxState(null, null);
		applyTint(rgba, vfx.activeTint());
		applyFlash(rgba, vfx.activeFlash(), timelineTimeSeconds);
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
