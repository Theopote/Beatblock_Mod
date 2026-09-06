package com.beatblock.automap.vfx;

import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

/**
 * Client-only environment lighting presentation state.
 * Does not mutate Minecraft skylight / block light / light engine.
 */
public final class EnvironmentLightingRuntime {

	public record State(double intensity, float r, float g, float b, double transitionSeconds) {
		public static final State NEUTRAL = new State(1.0, 1f, 1f, 1f, 0.0);

		public State {
			intensity = Math.max(0.0, intensity);
			transitionSeconds = Math.max(0.0, transitionSeconds);
		}

		public boolean isNeutral() {
			return Math.abs(intensity - 1.0) < 1e-6
				&& Math.abs(r - 1f) < 1e-6
				&& Math.abs(g - 1f) < 1e-6
				&& Math.abs(b - 1f) < 1e-6;
		}

		public GlobalEventPayload.EnvironmentLighting toPayload(@Nullable String name) {
			return new GlobalEventPayload.EnvironmentLighting(
				name != null ? name : "", intensity, r, g, b, transitionSeconds);
		}
	}

	private static volatile State current = State.NEUTRAL;

	private EnvironmentLightingRuntime() {
	}

	public static State current() {
		return current;
	}

	public static boolean apply(GlobalEventPayload.@Nullable EnvironmentLighting payload) {
		if (payload == null) {
			return false;
		}
		current = new State(payload.intensity(), payload.r(), payload.g(), payload.b(), payload.transitionSeconds());
		return true;
	}

	/** Seek / scrub: null restores neutral. */
	public static void sync(GlobalEventPayload.@Nullable EnvironmentLighting payload) {
		if (payload == null || payload.isNeutral()) {
			clear();
			return;
		}
		apply(payload);
	}

	public static void clear() {
		current = State.NEUTRAL;
	}

	public static void resetForTests() {
		clear();
	}
}
