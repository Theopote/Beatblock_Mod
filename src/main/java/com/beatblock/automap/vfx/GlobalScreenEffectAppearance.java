package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Shared screen-overlay appearance derived only from typed {@link GlobalEventPayload}.
 * Runtime ImGui overlay and export frame compositor must both use this — never re-read
 * raw Timeline parameter maps.
 */
public final class GlobalScreenEffectAppearance {

	public static final double TINT_ALPHA_SCALE = 0.35;
	public static final double FLASH_PEAK_ALPHA = 0.85;

	public record OverlayColor(float r, float g, float b, double alpha) {
		public OverlayColor {
			r = (float) clamp01(r);
			g = (float) clamp01(g);
			b = (float) clamp01(b);
			alpha = clamp01(alpha);
		}

		public boolean isVisible() {
			return alpha > 0;
		}
	}

	private GlobalScreenEffectAppearance() {
	}

	public static Optional<OverlayColor> screenTint(GlobalEventPayload.@Nullable ScreenTint tint) {
		if (tint == null) {
			return Optional.empty();
		}
		double alpha = clamp01(tint.intensity()) * TINT_ALPHA_SCALE;
		if (alpha <= 0) {
			return Optional.empty();
		}
		return Optional.of(new OverlayColor(tint.r(), tint.g(), tint.b(), alpha));
	}

	/**
	 * Timeline-sampled flash envelope at {@code timelineTimeSeconds}.
	 * Progress 0 → peak alpha; progress → 1 → fades out.
	 */
	public static Optional<OverlayColor> screenFlash(
		GlobalEventPayload.@Nullable ScreenFlash flash,
		double startSeconds,
		double timelineTimeSeconds
	) {
		if (flash == null) {
			return Optional.empty();
		}
		var progress = GlobalEffectActiveWindow.envelopeProgress(
			startSeconds, flash.durationSeconds(), timelineTimeSeconds);
		if (progress.isEmpty()) {
			return Optional.empty();
		}
		double alpha = FLASH_PEAK_ALPHA * (1.0 - progress.getAsDouble());
		if (alpha <= 0) {
			return Optional.empty();
		}
		return Optional.of(new OverlayColor(flash.r(), flash.g(), flash.b(), alpha));
	}

	/** Peak flash alpha for live wall-clock playback start (full envelope remaining). */
	public static Optional<OverlayColor> screenFlashPeak(GlobalEventPayload.@Nullable ScreenFlash flash) {
		if (flash == null) {
			return Optional.empty();
		}
		return Optional.of(new OverlayColor(flash.r(), flash.g(), flash.b(), FLASH_PEAK_ALPHA));
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
