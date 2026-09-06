package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

/**
 * Active-window helpers for global VFX seek reconstruct + export sampling.
 */
public final class GlobalEffectActiveWindow {

	private GlobalEffectActiveWindow() {
	}

	/**
	 * Whether a compiled cue should contribute presentation when reconstructing at {@code timeSeconds}.
	 * {@link GlobalEffectSemantics#IMPULSE} always returns false.
	 */
	public static boolean isActiveAt(@Nullable CompiledGlobalEvent event, double timeSeconds) {
		if (event == null || !Double.isFinite(timeSeconds)) {
			return false;
		}
		if (event.timeSeconds() > timeSeconds) {
			return false;
		}
		GlobalEffectSemantics semantics = GlobalEffectSemantics.fromPayload(event.payload());
		return switch (semantics) {
			case IMPULSE -> false;
			case CONTINUOUS_STATE -> true;
			case FINITE_ENVELOPE -> switch (event.payload()) {
				case GlobalEventPayload.ScreenTint tint ->
					inDurationWindow(event.timeSeconds(), tint.durationSeconds(), timeSeconds);
				case GlobalEventPayload.ScreenFlash flash ->
					inDurationWindow(event.timeSeconds(), Math.max(0.01, flash.durationSeconds()), timeSeconds);
				default -> false;
			};
		};
	}

	/**
	 * Progress through a finite envelope in {@code [0, 1)} at {@code timeSeconds}.
	 * Empty when the cue is not active.
	 */
	public static java.util.OptionalDouble envelopeProgress(
		double startSeconds,
		double durationSeconds,
		double timeSeconds
	) {
		double duration = Math.max(0.01, durationSeconds);
		if (!inDurationWindow(startSeconds, duration, timeSeconds)) {
			return java.util.OptionalDouble.empty();
		}
		return java.util.OptionalDouble.of(Math.max(0.0, (timeSeconds - startSeconds) / duration));
	}

	/**
	 * {@code durationSeconds <= 0} means open-ended for tint-like cues
	 * (active until a later cue replaces it). Flash should pass {@code max(0.01, duration)}.
	 */
	public static boolean inDurationWindow(double startSeconds, double durationSeconds, double timeSeconds) {
		if (timeSeconds < startSeconds) {
			return false;
		}
		return durationSeconds <= 0 || timeSeconds < startSeconds + durationSeconds;
	}
}
