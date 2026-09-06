package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

/**
 * Marker 编辑态：对齐 {@link com.beatblock.automap.choreography.SectionPlanSource} 思想。
 * <ul>
 *   <li>{@link #GENERATED} — 系统写入，可被 re-analysis 替换</li>
 *   <li>{@link #USER_EDITED} — 用户改过，re-analysis 不得静默覆盖</li>
 *   <li>{@link #LOCKED} — 锁定，Creator 不得改删</li>
 * </ul>
 */
public enum MarkerEditState {
	GENERATED,
	USER_EDITED,
	LOCKED;

	public boolean isLocked() {
		return this == LOCKED;
	}

	public boolean isReplaceableByGeneration() {
		return this == GENERATED;
	}

	public static MarkerEditState fromValue(@Nullable Object raw) {
		if (raw == null) {
			return USER_EDITED;
		}
		String s = String.valueOf(raw).trim();
		if (s.isEmpty()) {
			return USER_EDITED;
		}
		String normalized = s.toUpperCase().replace('-', '_');
		return switch (normalized) {
			case "GENERATED", "ANALYZED", "SYSTEM" -> GENERATED;
			case "USER_EDITED", "EDITED", "MANUAL" -> USER_EDITED;
			case "LOCKED" -> LOCKED;
			default -> {
				try {
					yield valueOf(normalized);
				} catch (IllegalArgumentException ex) {
					yield USER_EDITED;
				}
			}
		};
	}
}
