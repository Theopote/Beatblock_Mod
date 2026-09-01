package com.beatblock.automap.camera;

import java.util.Locale;

/** 镜头运动/过渡缓动曲线（0→1 进度映射）。 */
public final class CameraEasing {

	private CameraEasing() {}

	public static double apply(double t, CameraShotEasing easing) {
		return apply(t, easing != null ? easing.name() : "SMOOTH");
	}

	public static double apply(double t, String easingName) {
		double u = Math.max(0.0, Math.min(1.0, t));
		String name = easingName != null ? easingName.trim().toUpperCase(Locale.ROOT) : "SMOOTH";
		return switch (name) {
			case "LINEAR" -> u;
			case "EASE_IN" -> u * u;
			case "EASE_OUT" -> 1.0 - (1.0 - u) * (1.0 - u);
			default -> smoothstep(u);
		};
	}

	private static double smoothstep(double t) {
		return t * t * (3.0 - 2.0 * t);
	}
}
