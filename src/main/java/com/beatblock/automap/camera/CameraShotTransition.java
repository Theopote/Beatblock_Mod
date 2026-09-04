package com.beatblock.automap.camera;

import java.util.Locale;
import java.util.Optional;

/**
 * 镜间过渡：由 {@link CameraContinuityPlanner} 根据位姿跳跃选择。
 * <ul>
 *   <li>{@link #CUT} — 硬切</li>
 *   <li>{@link #DISSOLVE} — 叠化</li>
 *   <li>{@link #SMOOTH_MOVE} — 连续运镜衔接</li>
 *   <li>{@link #WHIP} — 快速甩镜式短混合</li>
 * </ul>
 */
public enum CameraShotTransition {
	CUT,
	DISSOLVE,
	SMOOTH_MOVE,
	WHIP;

	/**
	 * 解析过渡名；兼容旧值 {@code SMOOTH} → {@link #SMOOTH_MOVE}。
	 */
	public static CameraShotTransition parse(String raw) {
		return parse(raw, CUT);
	}

	public static CameraShotTransition parse(String raw, CameraShotTransition fallback) {
		return tryParse(raw).orElse(fallback != null ? fallback : CUT);
	}

	public static Optional<CameraShotTransition> tryParse(String raw) {
		if (raw == null || raw.isBlank()) return Optional.empty();
		String normalized = raw.trim().toUpperCase(Locale.ROOT);
		if ("SMOOTH".equals(normalized)) return Optional.of(SMOOTH_MOVE);
		try {
			return Optional.of(CameraShotTransition.valueOf(normalized));
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/** Incoming blend 窗口（秒）；CUT 为 0。 */
	public double blendSeconds() {
		return switch (this) {
			case CUT -> 0.0;
			case DISSOLVE -> 0.5;
			case SMOOTH_MOVE -> 0.35;
			case WHIP -> 0.12;
		};
	}
}
